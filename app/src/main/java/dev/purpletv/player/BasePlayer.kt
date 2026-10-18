package dev.purpletv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaSource
import dev.purpletv.data.DefaultQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackState {
    data object Loading : PlaybackState
    data object Playing : PlaybackState
    data object Buffering : PlaybackState
    data object Offline : PlaybackState
    data object Ended : PlaybackState
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

enum class PlayerMode { LIVE, VOD, PREVIEW }

fun loadControlFor(mode: PlayerMode, lowLatency: Boolean): LoadControl = when {
    mode == PlayerMode.VOD -> DefaultLoadControl.Builder()
        .setBufferDurationsMs(10_000, 60_000, 1_000, 2_500)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    lowLatency -> DefaultLoadControl.Builder()
        .setBufferDurationsMs(2_500, 20_000, 1_000, 2_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    else -> DefaultLoadControl.Builder()
        .setBufferDurationsMs(4_000, 30_000, 1_000, 2_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

/**
 * ExoPlayer wrapper shared by every app in PurpleTV: playback state, quality list and selection,
 * statistics, volume and seeking. Subclasses only know how to load their content.
 */
abstract class BasePlayer(
    context: Context,
    val mode: PlayerMode,
    mediaSourceFactory: MediaSource.Factory,
    loadControl: LoadControl,
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected val _state = MutableStateFlow<PlaybackState>(PlaybackState.Loading)
    val state: StateFlow<PlaybackState> = _state

    private val _qualities = MutableStateFlow<List<Quality>>(emptyList())
    val qualities: StateFlow<List<Quality>> = _qualities

    /** Selected quality label, null = auto. */
    private val _selectedQuality = MutableStateFlow<String?>(null)
    val selectedQuality: StateFlow<String?> = _selectedQuality

    protected var droppedFrames = 0
    private var bandwidthBps = 0L

    val player: ExoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()

    init {
        if (mode == PlayerMode.PREVIEW) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, 360).build()
            player.volume = 0f
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        onReady()
                        _state.value = PlaybackState.Playing
                    }
                    Player.STATE_BUFFERING -> if (_state.value == PlaybackState.Playing) _state.value = PlaybackState.Buffering
                    Player.STATE_ENDED -> if (!player.isCurrentMediaItemLive) _state.value = PlaybackState.Ended
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
                this@BasePlayer.droppedFrames += droppedFrames
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
    }

    /** Playback reached READY; reset retry counters etc. */
    protected open fun onReady() = Unit

    protected abstract fun handleError(error: PlaybackException)

    /** Loads the current content again (after an error, or from the menu). */
    abstract fun reload()

    protected fun resetForNewContent() {
        droppedFrames = 0
        _qualities.value = emptyList()
        _state.value = PlaybackState.Loading
        player.stop()
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

    protected fun applyDefaultQuality(q: DefaultQuality) {
        if (mode == PlayerMode.PREVIEW) return
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

    // --- Position ------------------------------------------------------------------------

    val positionMs get() = player.currentPosition.coerceAtLeast(0)
    val durationMs get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    fun seekTo(ms: Long) {
        val d = durationMs
        player.seekTo(if (d > 0) ms.coerceIn(0, d - 1000) else ms.coerceAtLeast(0))
    }

    // --- Volume --------------------------------------------------------------------------

    /** Extra reason to be silent, e.g. an unblockable Twitch ad. */
    protected open val forceMuted: Boolean get() = false

    var muted = false
        private set
    var volume = 1f
        private set

    fun setMuted(mute: Boolean) {
        muted = mute
        applyVolume()
    }

    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        if (volume > 0f) muted = false
        applyVolume()
    }

    protected fun applyVolume() {
        player.volume = if (mode == PlayerMode.PREVIEW || forceMuted || muted) 0f else volume
    }

    // --- Transport -----------------------------------------------------------------------

    open fun stop() {
        player.stop()
        _state.value = PlaybackState.Loading
    }

    fun pause() = player.pause()

    open fun resume() {
        when {
            player.playbackState == Player.STATE_IDLE -> reload()
            player.isCurrentMediaItemLive -> {
                // Jump back to live after a pause instead of playing old content.
                player.seekToDefaultPosition()
                player.play()
            }
            else -> player.play()
        }
    }

    open fun release() {
        scope.cancel()
        player.release()
    }
}
