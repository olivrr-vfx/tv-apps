package dev.purpletv.twitch

import dev.purpletv.core.AppLog
import dev.purpletv.core.Load
import dev.purpletv.data.Settings
import dev.purpletv.remote.RemoteControl
import dev.purpletv.twitch.api.ChannelInfo
import dev.purpletv.twitch.api.Game
import dev.purpletv.twitch.api.Helix
import dev.purpletv.twitch.api.NotLoggedInException
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.TwitchAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide Twitch data: followed live channels, followed channels, top streams and categories.
 *
 * Lives as long as the app (not a screen), so every screen and the player see the same, fresh
 * list. While Couch is visible the followed list refreshes every [FOLLOWED_INTERVAL_MS] and
 * immediately when the app comes back to the foreground; this is what removes channels that went
 * offline. Before, the list was only refreshed at start and while the home screen was open.
 */
class TwitchRepository(
    private val settings: Settings,
    private val auth: TwitchAuth,
    private val helix: Helix,
) {
    companion object {
        const val FOLLOWED_INTERVAL_MS = 60_000L
        const val TOP_INTERVAL_MS = 5 * 60_000L
        /** Coming back after this long refreshes right away. */
        const val STALE_AFTER_MS = 30_000L
        /** Waits before trying a failed request again (e.g. the TV woke up before its Wi-Fi). */
        val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000, 20_000, 40_000)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _followed = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val followed: StateFlow<Load<List<StreamInfo>>> = _followed
    private val _channels = MutableStateFlow<Load<List<ChannelInfo>>>(Load.Loading)
    val channels: StateFlow<Load<List<ChannelInfo>>> = _channels
    private val _top = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val top: StateFlow<Load<List<StreamInfo>>> = _top
    private val _games = MutableStateFlow<Load<List<Game>>>(Load.Loading)
    val games: StateFlow<Load<List<Game>>> = _games

    val loggedInName = MutableStateFlow(settings.userName)

    /** Wall-clock time of the last successful followed refresh, for "aktualisiert vor ..." */
    val followedUpdatedAt = MutableStateFlow(0L)

    private var started = false
    private var lastFollowedRefresh = 0L
    private var lastTopRefresh = 0L
    private var loop: Job? = null

    val isLoggedIn get() = auth.isLoggedIn

    /** First load. Validates the token once so the parallel requests don't all race to refresh it. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (auth.isLoggedIn) {
                withContext(Dispatchers.IO) { runCatching { auth.validate() } }
                launch(Dispatchers.IO) {
                    runCatching { helix.loadSelf() }
                    loggedInName.value = settings.userName
                }
            }
            refreshAll()
        }
    }

    /** Couch became visible: refresh what is stale and keep refreshing until [onBackground]. */
    fun onForeground() {
        if (!started) {
            start()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastFollowedRefresh > STALE_AFTER_MS) refreshFollowed(silent = true)
            if (now - lastTopRefresh > TOP_INTERVAL_MS) refreshTop(silent = true)
        }
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                delay(FOLLOWED_INTERVAL_MS)
                refreshFollowed(silent = true)
                if (System.currentTimeMillis() - lastTopRefresh > TOP_INTERVAL_MS) refreshTop(silent = true)
            }
        }
    }

    fun onBackground() {
        loop?.cancel()
        loop = null
    }

    private fun <T> load(
        target: MutableStateFlow<Load<T>>,
        showLoading: Boolean = true,
        onOk: (T) -> Unit = {},
        attempt: Int = 0,
        block: () -> T,
    ) {
        if (showLoading || target.value !is Load.Ok) target.value = Load.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Load.Ok(block())
                } catch (e: NotLoggedInException) {
                    Load.NeedLogin
                } catch (e: Exception) {
                    AppLog.e("Twitch", "Laden fehlgeschlagen", e)
                    Load.Error(e.message ?: "Fehler")
                }
            }
            if (result is Load.Ok) onOk(result.data)
            // Keep showing old data if a silent refresh fails.
            if (result is Load.Ok || showLoading || target.value !is Load.Ok) target.value = result
            if (result is Load.Error && attempt < RETRY_DELAYS_MS.size) {
                delay(RETRY_DELAYS_MS[attempt])
                // Something else (a manual refresh, the network callback) may have fixed it already.
                if (target.value is Load.Error || (target.value is Load.Ok && !showLoading)) {
                    load(target, showLoading = false, onOk = onOk, attempt = attempt + 1, block = block)
                }
            }
        }
    }

    /** The network came back: load again whatever failed. */
    fun retryFailed() {
        if (!started) return
        if (_followed.value is Load.Error) refreshFollowed()
        if (_top.value is Load.Error) refreshTop()
        if (_channels.value is Load.Error) load(_channels) { helix.followedChannels() }
        if (_games.value is Load.Error) load(_games) { helix.topGames() }
    }

    fun refreshAll() {
        refreshFollowed()
        load(_channels) { helix.followedChannels() }
        refreshTop()
        load(_games) { helix.topGames() }
    }

    fun refreshFollowed(silent: Boolean = false) {
        lastFollowedRefresh = System.currentTimeMillis()
        load(_followed, showLoading = !silent, onOk = {
            RemoteControl.followedLive = it
            followedUpdatedAt.value = System.currentTimeMillis()
        }) { helix.followedStreams() }
    }

    fun refreshTop(silent: Boolean = false) {
        lastTopRefresh = System.currentTimeMillis()
        load(_top, showLoading = !silent) { helix.topStreams() }
    }

    /** Is [login] in the last good followed list? Null while it is unknown. */
    fun isFollowedLive(login: String): Boolean? =
        (followed.value as? Load.Ok)?.data?.any { it.login == login }

    fun onLoginChanged() {
        loggedInName.value = settings.userName
        refreshAll()
    }

    fun logout() {
        auth.logout()
        RemoteControl.followedLive = emptyList()
        onLoginChanged()
    }
}
