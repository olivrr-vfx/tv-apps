package dev.purpletv

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.purpletv.core.AppId
import dev.purpletv.data.Http
import dev.purpletv.data.Settings
import dev.purpletv.data.WatchHistory
import dev.purpletv.hub.AppLauncher
import dev.purpletv.remote.RemoteServer
import dev.purpletv.remote.WebPlayback
import dev.purpletv.twitch.TwitchRepository
import dev.purpletv.twitch.api.Helix
import dev.purpletv.twitch.api.TwitchAuth
import dev.purpletv.twitch.api.TwitchGql
import dev.purpletv.youtube.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PurpleApp : Application(), ImageLoaderFactory {
    lateinit var settings: Settings
        private set
    lateinit var gql: TwitchGql
        private set
    lateinit var auth: TwitchAuth
        private set
    lateinit var helix: Helix
        private set
    /** Twitch VOD progress. */
    lateinit var history: WatchHistory
        private set
    lateinit var twitch: TwitchRepository
        private set
    lateinit var youtube: YouTubeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        gql = TwitchGql(settings.deviceId)
        auth = TwitchAuth(settings)
        helix = Helix(settings, auth)
        history = WatchHistory(this)
        twitch = TwitchRepository(settings, auth, helix)
        youtube = YouTubeRepository(this, settings)
        _enabled.value = enabledApps()
        AppLauncher.sync(this, settings)
        if (settings.remoteEnabled) startRemote()
    }

    fun enabledApps(): List<AppId> = AppId.entries.filter { settings.isEnabled(it) }

    private val _enabled = MutableStateFlow<List<AppId>>(emptyList())
    /** Enabled apps as state, so the navigation and dashboard follow the settings. */
    val enabledAppsState: StateFlow<List<AppId>> get() = _enabled

    fun setAppEnabled(app: AppId, enabled: Boolean) {
        settings.setEnabled(app, enabled)
        _enabled.value = enabledApps()
        AppLauncher.sync(this, settings)
        if (enabled) onForeground() else when (app) {
            AppId.TWITCH -> twitch.onBackground()
            AppId.YOUTUBE -> youtube.onBackground()
        }
    }

    /** PurpleTV is visible: keep the data of enabled apps fresh. */
    fun onForeground() {
        if (settings.isEnabled(AppId.TWITCH)) twitch.onForeground()
        if (settings.isEnabled(AppId.YOUTUBE)) youtube.onForeground()
    }

    fun onBackground() {
        twitch.onBackground()
        youtube.onBackground()
    }

    /** Twitch streams relayed to browsers (Couch web app player). */
    val webPlayback by lazy { WebPlayback(this) }

    private var remote: List<RemoteServer> = emptyList()

    /** Starts the web server for Home Assistant and the Couch web app (HTTP + HTTPS). */
    fun startRemote() {
        if (remote.isNotEmpty()) return
        remote = listOf(RemoteServer(this), RemoteServer(this, secure = true)).onEach { it.startAll() }
    }

    fun stopRemote() {
        remote.forEach { it.stopAll() }
        remote = emptyList()
    }

    // Animated emotes (7TV/BTTV webp + gif) need the platform ImageDecoder on Android 9+.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .crossfade(150)
        .build()
}
