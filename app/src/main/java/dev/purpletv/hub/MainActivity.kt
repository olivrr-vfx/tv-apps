package dev.purpletv.hub

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.core.items
import dev.purpletv.data.StartScreen
import dev.purpletv.design.Accent
import dev.purpletv.design.AccentScope
import dev.purpletv.design.NavRail
import dev.purpletv.design.Palette
import dev.purpletv.design.PurpleTheme
import dev.purpletv.design.RailItem
import dev.purpletv.design.Tokens
import dev.purpletv.remote.DeepLinks
import dev.purpletv.remote.RemoteControl
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.ui.ChannelScreen
import dev.purpletv.twitch.ui.GameStreamsScreen
import dev.purpletv.twitch.ui.LivePlayerScreen
import dev.purpletv.twitch.ui.LoginScreen
import dev.purpletv.twitch.ui.TwitchHome
import dev.purpletv.twitch.ui.TwitchTab
import dev.purpletv.twitch.ui.TwitchViewModel
import dev.purpletv.twitch.ui.VodPlayerScreen
import dev.purpletv.update.UpdateState
import dev.purpletv.update.Updater
import dev.purpletv.youtube.api.YtVideo
import dev.purpletv.youtube.ui.WatchScreen
import dev.purpletv.youtube.ui.YouTubeHome
import dev.purpletv.youtube.ui.YouTubeTab
import dev.purpletv.youtube.ui.YouTubeViewModel
import dev.purpletv.youtube.ui.YtChannelScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var lastUpdateCheck = 0L
    private val app get() = application as PurpleApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleLink(intent)
        val launchedFrom = AppLauncher.appFor(intent)
        setContent {
            PurpleTheme {
                HubApp(app, launchedFrom)
            }
        }
        // Phone remote: D-pad keys become real key events, so they move the focus like the TV remote.
        lifecycleScope.launch {
            RemoteControl.keys.collect { name -> if (RemoteControl.foreground) injectKey(name) }
        }
    }

    private fun injectKey(name: String) {
        if (name == "back") {
            onBackPressedDispatcher.onBackPressed()
            return
        }
        val code = when (name) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
            "menu" -> KeyEvent.KEYCODE_MENU
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "channel_up" -> KeyEvent.KEYCODE_CHANNEL_UP
            "channel_down" -> KeyEvent.KEYCODE_CHANNEL_DOWN
            else -> return
        }
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val fromTile = AppLauncher.appFor(intent)
        if (fromTile != null) RemoteControl.pendingLink.value = RemoteControl.Command.OpenApp(fromTile)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        DeepLinks.parse(intent?.data)?.let { RemoteControl.pendingLink.value = it }
    }

    override fun onStart() {
        super.onStart()
        RemoteControl.foreground = true
        app.onForeground()
        if (!app.settings.autoUpdate) return
        val now = System.currentTimeMillis()
        // Re-check right away when coming back from the permission screen.
        if (Updater.state.value == UpdateState.NeedsPermission || now - lastUpdateCheck > 30 * 60_000) {
            lastUpdateCheck = now
            lifecycleScope.launch { Updater.checkAndInstall(this@MainActivity) }
        }
    }

    override fun onStop() {
        super.onStop()
        RemoteControl.foreground = false
        app.onBackground()
    }
}

private fun startSection(app: PurpleApp, launchedFrom: AppId?): Route.Section {
    val enabled = app.enabledApps()
    if (launchedFrom != null && launchedFrom in enabled) return Route.App(launchedFrom)
    return when (app.settings.startScreen) {
        StartScreen.DASHBOARD -> Route.Dashboard
        StartScreen.LAST -> app.settings.lastApp?.takeIf { it in enabled }?.let { Route.App(it) } ?: Route.Dashboard
        StartScreen.TWITCH -> if (AppId.TWITCH in enabled) Route.App(AppId.TWITCH) else Route.Dashboard
        StartScreen.YOUTUBE -> if (AppId.YOUTUBE in enabled) Route.App(AppId.YOUTUBE) else Route.Dashboard
    }
}

private fun Route.Section.key() = when (this) {
    Route.Dashboard -> "dashboard"
    Route.Settings -> "settings"
    is Route.App -> app.key
}

private fun accentFor(app: AppId) = when (app) {
    AppId.TWITCH -> Accent.Twitch
    AppId.YOUTUBE -> Accent.YouTube
}

@Composable
private fun HubApp(app: PurpleApp, launchedFrom: AppId?) {
    val nav = remember { Navigator(startSection(app, launchedFrom)) }
    val saveable = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var phoneRemote by remember { mutableStateOf(false) }

    BackHandler(enabled = nav.stack.size > 1 || nav.section != nav.root) { nav.back() }

    // Tell the remote API where we are, remember the last app.
    LaunchedEffect(nav) {
        snapshotFlow { nav.current }.collect { route ->
            RemoteControl.screen = when (route) {
                Route.Dashboard -> "dashboard"
                Route.Settings -> "settings"
                is Route.App -> route.app.key
                Route.TwitchLogin, is Route.TwitchGame, is Route.TwitchChannel, is Route.TwitchLive, is Route.TwitchVod -> "twitch"
                is Route.YouTubeChannel, is Route.YouTubeWatch -> "youtube"
            }
            if (route is Route.App) app.settings.lastApp = route.app
        }
    }

    // Deep links, app tiles and Home Assistant / phone commands that change the screen.
    fun open(command: RemoteControl.Command) {
        val enabled = app.enabledApps()
        when (command) {
            RemoteControl.Command.Home -> nav.showSection(Route.Dashboard)
            is RemoteControl.Command.OpenApp -> if (command.app in enabled) nav.showSection(Route.App(command.app))
            is RemoteControl.Command.OpenLive -> if (AppId.TWITCH in enabled) {
                val followed = app.twitch.followed.value.items()
                val known = followed.firstOrNull { it.login == command.login }
                val list = if (known != null) followed else listOf(StreamInfo(null, command.login, command.login))
                nav.open(Route.TwitchLive(list, command.login, followed = known != null), Route.App(AppId.TWITCH))
            }
            is RemoteControl.Command.OpenChannel -> if (AppId.TWITCH in enabled) nav.open(Route.TwitchChannel(command.login), Route.App(AppId.TWITCH))
            is RemoteControl.Command.OpenVod -> if (AppId.TWITCH in enabled) scope.launch {
                val video = withContext(Dispatchers.IO) { runCatching { app.helix.video(command.id) }.getOrNull() }
                if (video != null) nav.open(Route.TwitchVod(video), Route.App(AppId.TWITCH))
            }
            is RemoteControl.Command.OpenYouTube -> if (AppId.YOUTUBE in enabled) {
                nav.open(Route.YouTubeWatch(YtVideo(command.videoId, "", "", "")), Route.App(AppId.YOUTUBE))
            }
            is RemoteControl.Command.OpenYouTubeChannel -> if (AppId.YOUTUBE in enabled) {
                nav.open(Route.YouTubeChannel(command.url), Route.App(AppId.YOUTUBE))
            }
            is RemoteControl.Command.YouTubeSearch -> if (AppId.YOUTUBE in enabled) {
                pendingYouTubeSearch.value = command.query
                nav.showSection(Route.App(AppId.YOUTUBE))
            }
            else -> Unit
        }
    }
    LaunchedEffect(Unit) {
        RemoteControl.pendingLink.collect { link ->
            if (link != null) {
                RemoteControl.pendingLink.value = null
                open(link)
            }
        }
    }
    LaunchedEffect(Unit) { RemoteControl.commands.collect { if (it.opensScreen) open(it) } }

    Box(Modifier.fillMaxSize().background(Palette.canvas)) {
        val route = nav.current
        // A new route instance (e.g. another channel opened from Home Assistant) gets fresh state.
        key(route) {
            when (route) {
                is Route.Section -> HubShell(app, nav, route) {
                    saveable.SaveableStateProvider(route.key()) {
                        when (route) {
                            Route.Dashboard -> Dashboard(app, nav, onPhoneRemote = { phoneRemote = true })
                            Route.Settings -> SettingsScreen(app, nav, onPhoneRemote = { phoneRemote = true })
                            is Route.App -> AccentScope(accentFor(route.app)) {
                                when (route.app) {
                                    AppId.TWITCH -> TwitchHome(viewModel<TwitchViewModel>(), nav)
                                    AppId.YOUTUBE -> {
                                        val vm = viewModel<YouTubeViewModel>()
                                        LaunchedEffect(Unit) {
                                            pendingYouTubeSearch.value?.let { q ->
                                                pendingYouTubeSearch.value = null
                                                vm.tab.value = YouTubeTab.SEARCH
                                                vm.search(q)
                                            }
                                        }
                                        YouTubeHome(vm, nav)
                                    }
                                }
                            }
                        }
                    }
                }
                Route.TwitchLogin -> AccentScope(Accent.Twitch) {
                    val vm = viewModel<TwitchViewModel>()
                    LoginScreen(app, onDone = {
                        vm.repo.onLoginChanged()
                        vm.tab.value = TwitchTab.START
                        nav.pop()
                    })
                }
                is Route.TwitchGame -> AccentScope(Accent.Twitch) {
                    saveable.SaveableStateProvider("game-${route.game.id}") { GameStreamsScreen(viewModel(), route.game, nav) }
                }
                is Route.TwitchChannel -> AccentScope(Accent.Twitch) {
                    saveable.SaveableStateProvider("channel-${route.login}") { ChannelScreen(viewModel(), route.login, nav) }
                }
                is Route.TwitchLive -> AccentScope(Accent.Twitch) {
                    LivePlayerScreen(
                        app = app,
                        channels = route.channels,
                        startLogin = route.login,
                        followedSource = route.followed,
                        onExit = { nav.pop() || nav.back() },
                        onChannel = { login -> nav.openTwitchChannel(login) },
                    )
                }
                is Route.TwitchVod -> AccentScope(Accent.Twitch) {
                    val vm = viewModel<TwitchViewModel>()
                    VodPlayerScreen(
                        app = app,
                        video = route.video,
                        onExit = {
                            nav.pop()
                            vm.refreshHistory()
                        },
                        onChannel = { login -> nav.openTwitchChannel(login) },
                    )
                }
                is Route.YouTubeChannel -> AccentScope(Accent.YouTube) {
                    saveable.SaveableStateProvider("yt-channel-${route.url}") { YtChannelScreen(viewModel(), route.url, nav) }
                }
                is Route.YouTubeWatch -> AccentScope(Accent.YouTube) {
                    val vm = viewModel<YouTubeViewModel>()
                    WatchScreen(
                        app = app,
                        start = route.video,
                        queue = route.queue,
                        onExit = {
                            nav.pop()
                            vm.refreshHistory()
                        },
                        onChannel = { url, name -> nav.openYouTubeChannel(url, name) },
                    )
                }
            }
        }
        if (phoneRemote) PhoneRemoteDialog(app, onDismiss = { phoneRemote = false })
        UpdateOverlay()
    }
}

/** Search query from the phone remote, picked up by the YouTube screen. */
private val pendingYouTubeSearch = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

/** Top-level sections get the navigation rail on the left. */
@Composable
private fun HubShell(app: PurpleApp, nav: Navigator, section: Route.Section, content: @Composable () -> Unit) {
    val enabled by app.enabledAppsState.collectAsState()
    val items = buildList {
        add(RailItem("dashboard", "Start", icon = Icons.Filled.Home))
        enabled.forEach { add(RailItem(it.key, it.label, app = it, accent = accentFor(it))) }
        add(RailItem("settings", "Einstellungen", icon = Icons.Filled.Settings))
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(start = Tokens.Layout.railCollapsed)) { content() }
        NavRail(
            items = items,
            selected = section.key(),
            onSelect = { key ->
                val target = when (key) {
                    "dashboard" -> Route.Dashboard
                    "settings" -> Route.Settings
                    else -> AppId.fromKey(key)?.let { Route.App(it) } ?: Route.Dashboard
                }
                if (target != section) nav.showSection(target)
            },
            footer = app.twitch.loggedInName.value?.let { "Twitch: $it" },
            modifier = Modifier.zIndex(1f),
        )
    }
}
