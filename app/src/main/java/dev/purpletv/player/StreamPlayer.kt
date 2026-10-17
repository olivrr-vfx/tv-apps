package dev.purpletv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import dev.purpletv.data.DefaultQuality
import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import dev.purpletv.twitch.TwitchGql
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlaybackState {
    data object Loading : PlaybackState
    data object Playing : PlaybackState
    data object Buffering : PlaybackState
    data object Offline : PlaybackState
    data class Error(val message: String) : PlaybackState
}

data class Quality(val label: String, val height: Int, val group: Tracks.Group?, val trackIndex: Int)

data class PlayerStats(
    val resolution: String = "-",
    val bitrateKbps: Int = 0,
    val bufferSec: Double = 0.0,
    val latencySec: Double? = null,
    val droppedFrames: Int = 0,
    val bandwidthMbps: Double = 0.0,
)

class StreamPlayer(context: Context, private val settings: Settings, private val gql: TwitchGql) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lowLatency = settings.lowLatency

    val adEngine = AdBlockEngine(gql, { settings.adBlock }, { lowLatency })

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Loading)
    val state: StateFlow<PlaybackState> = _state

    private val _qualities = MutableStateFlow<List<Quality>>(emptyList())
    val qualities: StateFlow<List<Quality>> = _qualities

    /** Selected quality label, null = auto. */
    private val _selectedQuality = MutableStateFlow<String?>(null)
    val selectedQuality: StateFlow<String?> = _selectedQuality

    private var droppedFrames = 0
    private var bandwidthBps = 0L
    private var login: String? = null
    private var retries = 0
    private var loadJob: Job? = null

    val player: ExoPlayer

    init {
        val dataSourceFactory = AdBlockDataSource.Factory(OkHttpDataSource.Factory(Http.client), adEngine)
        val loadControl = if (lowLatency) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 20_000, 500, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 30_000, 1_000, 2_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
        player = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(HlsMediaSource.Factory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        applyDefaultQuality(settings.defaultQuality)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        retries = 0
                        _state.value = PlaybackState.Playing
                    }
                    Player.STATE_BUFFERING -> if (_state.value == PlaybackState.Playing) _state.value = PlaybackState.Buffering
                    else -> Unit
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                _qualities.value = buildQualities(tracks)
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                this@StreamPlayer.droppedFrames += droppedFrames
            }

            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                bandwidthBps = bitrateEstimate
            }
        })

        scope.launch {
            adEngine.status.collect { status ->
                player.volume = if (status == AdStatus.SHOWING && settings.muteAds) 0f else 1f
            }
        }
    }

    fun play(login: String) {
        this.login = login
        retries = 0
        droppedFrames = 0
        adEngine.reset(login)
        _qualities.value = emptyList()
        _state.value = PlaybackState.Loading
        player.stop()
        load()
    }

    fun reload() {
        login?.let { play(it) }
    }

    private fun load() {
        val login = login ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            val url = withContext(Dispatchers.IO) {
                runCatching { gql.playbackToken(login)?.let { gql.usherUrl(login, it, lowLatency) } }
            }
            val usher = url.getOrElse {
                retryLater("Keine Verbindung zu Twitch")
                return@launch
            }
            if (usher == null) {
                _state.value = PlaybackState.Offline
                return@launch
            }
            val item = MediaItem.Builder()
                .setUri(usher)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(if (lowLatency) 2_500 else 6_000)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.05f)
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun handleError(error: PlaybackException) {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpStatusException && cause.url.contains("usher") && cause.code == 404) {
                _state.value = PlaybackState.Offline
                return
            }
            cause = cause.cause
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        // Covers stuck playlists while ads are stripped, network hiccups, expired tokens, ...
        retryLater(error.message ?: "Wiedergabefehler")
    }

    private fun retryLater(message: String) {
        if (retries >= 8) {
            _state.value = PlaybackState.Error(message)
            return
        }
        retries++
        _state.value = PlaybackState.Buffering
        scope.launch {
            delay(800L * retries)
            load()
        }
    }

    // --- Quality -------------------------------------------------------------------------

    private fun buildQualities(tracks: Tracks): List<Quality> {
        val out = mutableListOf<Quality>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                if (f.height <= 0) continue
                val fps = if (f.frameRate > 0) Math.round(f.frameRate) else 0
                val label = "${f.height}p" + if (fps > 30) "$fps" else ""
                out += Quality(label, f.height, group, i)
            }
        }
        val sorted = out.distinctBy { it.label }.sortedWith(compareByDescending<Quality> { it.height }.thenByDescending { it.label })
        return sorted.mapIndexed { idx, q -> if (idx == 0) q.copy(label = q.label + " (Quelle)") else q }
    }

    fun selectQuality(quality: Quality?) {
        val params = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        if (quality?.group != null) {
            params.setOverrideForType(TrackSelectionOverride(quality.group.mediaTrackGroup, listOf(quality.trackIndex)))
        }
        player.trackSelectionParameters = params.build()
        _selectedQuality.value = quality?.label
    }

    private fun applyDefaultQuality(q: DefaultQuality) {
        val params = player.trackSelectionParameters.buildUpon()
        when {
            q == DefaultQuality.SOURCE -> params.setForceHighestSupportedBitrate(true)
            q.maxHeight != null -> params.setMaxVideoSize(Int.MAX_VALUE, q.maxHeight)
        }
        player.trackSelectionParameters = params.build()
        _selectedQuality.value = if (q == DefaultQuality.AUTO) null else q.label
    }

    fun currentQualityLabel(): String {
        val f = player.videoFormat ?: return "-"
        val fps = if (f.frameRate > 0) Math.round(f.frameRate) else 0
        return "${f.height}p" + if (fps > 30) "$fps" else ""
    }

    fun stats(): PlayerStats {
        val f = player.videoFormat
        val offset = player.currentLiveOffset
        return PlayerStats(
            resolution = f?.let { "${it.width}x${it.height}" + if (it.frameRate > 0) " @${Math.round(it.frameRate)}" else "" } ?: "-",
            bitrateKbps = (f?.bitrate ?: 0).coerceAtLeast(0) / 1000,
            bufferSec = player.totalBufferedDuration / 1000.0,
            latencySec = if (offset == C.TIME_UNSET) null else offset / 1000.0,
            droppedFrames = droppedFrames,
            bandwidthMbps = bandwidthBps / 1_000_000.0,
        )
    }

    fun pause() = player.pause()
    fun resume() {
        if (player.playbackState == Player.STATE_IDLE) reload() else {
            // Jump back to live after a pause instead of playing old content.
            player.seekToDefaultPosition()
            player.play()
        }
    }

    fun release() {
        scope.cancel()
        player.release()
    }
}
