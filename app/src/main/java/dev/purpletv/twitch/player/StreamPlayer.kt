package dev.purpletv.twitch.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import dev.purpletv.player.BasePlayer
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.player.loadControlFor
import dev.purpletv.twitch.api.TwitchGql
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Twitch live streams and VODs, with the ad blocker sitting in front of every playlist. */
class StreamPlayer private constructor(
    context: Context,
    private val settings: Settings,
    private val gql: TwitchGql,
    mode: PlayerMode,
    val adEngine: AdBlockEngine,
    private val lowLatency: Boolean,
) : BasePlayer(
    context,
    mode,
    HlsMediaSource.Factory(AdBlockDataSource.Factory(OkHttpDataSource.Factory(Http.client), adEngine)),
    loadControlFor(mode, lowLatency),
) {
    constructor(context: Context, settings: Settings, gql: TwitchGql, mode: PlayerMode = PlayerMode.LIVE) : this(
        context,
        settings,
        gql,
        mode,
        lowLatency = settings.lowLatency && mode != PlayerMode.VOD,
    )

    private constructor(context: Context, settings: Settings, gql: TwitchGql, mode: PlayerMode, lowLatency: Boolean) : this(
        context,
        settings,
        gql,
        mode,
        AdBlockEngine(gql, { settings.adBlock }, { lowLatency }).apply { active = mode != PlayerMode.VOD },
        lowLatency,
    )

    private var login: String? = null
    private var vodId: String? = null
    private var vodStartMs = 0L
    private var retries = 0
    private var loadJob: Job? = null
    private var adMuted = false

    override val forceMuted: Boolean get() = adMuted

    init {
        applyDefaultQuality(settings.defaultQuality)
        scope.launch {
            adEngine.status.collect { status ->
                adMuted = status == AdStatus.SHOWING && settings.muteAds
                applyVolume()
            }
        }
    }

    override fun onReady() {
        retries = 0
    }

    fun play(login: String) {
        this.login = login
        this.vodId = null
        retries = 0
        adEngine.reset(login)
        resetForNewContent()
        load()
    }

    fun playVod(id: String, startMs: Long) {
        vodId = id
        vodStartMs = startMs
        login = null
        retries = 0
        resetForNewContent()
        loadVod()
    }

    override fun reload() {
        val vod = vodId
        if (vod != null) {
            vodStartMs = player.currentPosition.takeIf { it > 0 } ?: vodStartMs
            playVod(vod, vodStartMs)
        } else {
            login?.let { play(it) }
        }
    }

    private fun loadVod() {
        val id = vodId ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { gql.vodToken(id)?.let { gql.vodUsherUrl(id, it) } } }
            val usher = url.getOrElse {
                retryLater("Keine Verbindung zu Twitch")
                return@launch
            }
            if (usher == null) {
                _state.value = PlaybackState.Error("Dieses Video gibt es nicht mehr")
                return@launch
            }
            val item = MediaItem.Builder().setUri(usher).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            player.setMediaItem(item, vodStartMs)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun load() {
        if (vodId != null) {
            vodStartMs = player.currentPosition.takeIf { it > 0 } ?: vodStartMs
            loadVod()
            return
        }
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
                        .setTargetOffsetMs(if (lowLatency) 3_500 else 7_000)
                        .setMinPlaybackSpeed(0.98f)
                        .setMaxPlaybackSpeed(1.02f)
                        .build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun handleError(error: PlaybackException) {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpStatusException && cause.url.contains("usher")) {
                if (vodId != null && cause.code == 403) {
                    _state.value = PlaybackState.Error("Dieses Video ist nur für Abonnenten verfügbar")
                    return
                }
                if (cause.code == 404) {
                    _state.value = if (vodId != null) PlaybackState.Error("Video nicht gefunden") else PlaybackState.Offline
                    return
                }
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

    override fun stop() {
        loadJob?.cancel()
        super.stop()
    }
}
