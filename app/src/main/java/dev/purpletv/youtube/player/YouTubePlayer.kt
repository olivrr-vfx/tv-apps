package dev.purpletv.youtube.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.purpletv.data.Settings
import dev.purpletv.player.BasePlayer
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.player.loadControlFor
import dev.purpletv.youtube.api.YouTubeHttp
import dev.purpletv.youtube.api.YtPlayback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import java.io.ByteArrayInputStream

/**
 * Plays YouTube videos and live streams. Preference order:
 * 1. HLS from YouTube's Apple client (adaptive, all qualities in one manifest),
 * 2. separate video + audio streams, wrapped into DASH manifests (one video source per quality,
 *    so the quality menu works),
 * 3. a single muxed stream (360p) as last resort.
 */
class YouTubePlayer(
    context: Context,
    private val settings: Settings,
    /** Fetches fresh stream URLs (they expire after a few hours or on 403). */
    private val refresh: suspend () -> YtPlayback?,
) : BasePlayer(
    context,
    PlayerMode.VOD,
    DefaultMediaSourceFactory(dataSourceFactory),
    loadControlFor(PlayerMode.VOD, lowLatency = false),
) {
    companion object {
        private val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(YouTubeHttp.mediaClient)
        private const val MAX_RETRIES = 2
    }

    private var current: YtPlayback? = null
    private var retries = 0
    private var job: Job? = null

    init {
        applyDefaultQuality(settings.youtubeQuality)
    }

    override fun onReady() {
        retries = 0
    }

    fun play(playback: YtPlayback, startMs: Long = 0) {
        current = playback
        resetForNewContent()
        val source = runCatching { buildSource(playback) }.getOrElse {
            _state.value = PlaybackState.Error("Video kann nicht abgespielt werden: ${it.message}")
            return
        }
        if (source == null) {
            _state.value = PlaybackState.Error("Für dieses Video wurde kein abspielbarer Stream gefunden.")
            return
        }
        player.setMediaSource(source, if (playback.video.isLive) 0 else startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun buildSource(p: YtPlayback): MediaSource? {
        if (p.hlsUrl.isNotEmpty()) {
            return HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.Builder().setUri(p.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
        }
        if (p.dashUrl.isNotEmpty()) {
            return DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.Builder().setUri(p.dashUrl).setMimeType(MimeTypes.APPLICATION_MPD).build())
        }
        val duration = p.video.durationSec
        val audio = p.audio.filter { it.isUrl && it.itagItem != null }
            .sortedWith(compareByDescending<AudioStream> { it.format?.mimeType?.contains("mp4") == true }.thenByDescending { it.averageBitrate })
            .firstOrNull()
        // One stream per height; H.264 first because every TV decodes it in hardware.
        val videos = p.videoOnly.filter { it.isUrl && it.itagItem != null }
            .sortedWith(compareByDescending<VideoStream> { it.codec.orEmpty().startsWith("avc") }.thenByDescending { it.fps })
            .distinctBy { it.height }
            .sortedByDescending { it.height }
            .take(5)
        if (audio != null && videos.isNotEmpty()) {
            val sources = (videos + listOf<Stream>(audio)).mapNotNull { dashSource(it, duration) }
            if (sources.size >= 2) return MergingMediaSource(*sources.toTypedArray())
        }
        val muxed = p.muxed.filter { it.isUrl }.maxByOrNull { it.height } ?: return null
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(muxed.content))
    }

    private fun dashSource(stream: Stream, durationSec: Long): MediaSource? = runCatching {
        val itag = stream.itagItem ?: return null
        val manifest = when (stream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itag, durationSec)
            DeliveryMethod.DASH -> YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, durationSec)
            else -> return null
        }
        val parsed = DashManifestParser().parse(Uri.parse(stream.content), ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)))
        DashMediaSource.Factory(dataSourceFactory).createMediaSource(parsed, MediaItem.Builder().setUri(stream.content).setMediaId(stream.content).build())
    }.getOrNull()

    override fun reload() {
        val position = positionMs
        job?.cancel()
        job = scope.launch {
            _state.value = PlaybackState.Loading
            val fresh = runCatching { refresh() }.getOrNull() ?: current
            if (fresh == null) {
                _state.value = PlaybackState.Error("Video konnte nicht neu geladen werden")
                return@launch
            }
            play(fresh, position)
        }
    }

    override fun handleError(error: PlaybackException) {
        var cause: Throwable? = error
        var expired = false
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException && (cause.responseCode == 403 || cause.responseCode == 410)) expired = true
            cause = cause.cause
        }
        if (retries < MAX_RETRIES) {
            retries++
            // Stream URLs expired or got blocked: fetch new ones and continue where we were.
            reload()
            return
        }
        _state.value = PlaybackState.Error(
            if (expired) "YouTube verweigert gerade die Wiedergabe (403). Bitte später nochmal versuchen."
            else error.message ?: "Wiedergabefehler"
        )
    }

    override fun release() {
        job?.cancel()
        super.release()
    }
}
