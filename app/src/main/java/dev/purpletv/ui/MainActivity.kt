package dev.purpletv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.purpletv.PurpleApp
import dev.purpletv.update.UpdateState
import dev.purpletv.update.Updater
import kotlinx.coroutines.launch
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo

sealed interface Route {
    data object Home : Route
    data object Login : Route
    data class GameStreams(val game: Game) : Route
    data class Channel(val login: String) : Route
    data class Player(val channels: List<StreamInfo>, val index: Int) : Route
    data class Vod(val video: VideoInfo) : Route
}

class MainActivity : ComponentActivity() {
    private var lastUpdateCheck = 0L

    override fun onStart() {
        super.onStart()
        if (!(application as PurpleApp).settings.autoUpdate) return
        val now = System.currentTimeMillis()
        // Re-check right away when coming back from the permission screen.
        if (Updater.state.value == UpdateState.NeedsPermission || now - lastUpdateCheck > 30 * 60_000) {
            lastUpdateCheck = now
            lifecycleScope.launch { Updater.checkAndInstall(this@MainActivity) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PurpleTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val vm: HomeViewModel = viewModel()
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    val saveable = rememberSaveableStateHolder()

    fun push(route: Route) {
        stack = stack + route
    }
    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    fun openChannel(login: String) {
        // Coming from the channel page into a player and back to the same channel: just go back.
        val previous = stack.getOrNull(stack.size - 2)
        if (previous is Route.Channel && previous.login == login) pop() else push(Route.Channel(login))
    }

    val actions = HomeActions(
        play = { list, index -> push(Route.Player(list, index)) },
        vod = { video -> push(Route.Vod(video)) },
        channel = { login -> openChannel(login) },
        game = { game ->
            vm.openGame(game)
            push(Route.GameStreams(game))
        },
        login = { push(Route.Login) },
    )

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Colors.Background)) {
        when (val route = stack.last()) {
            Route.Home -> saveable.SaveableStateProvider("home") { HomeScreen(vm, actions) }
            is Route.GameStreams -> saveable.SaveableStateProvider("game-${route.game.id}") {
                GameStreamsScreen(vm, route.game, actions)
            }
            is Route.Channel -> saveable.SaveableStateProvider("channel-${route.login}") {
                ChannelScreen(
                    vm, route.login,
                    onPlayLive = { stream -> push(Route.Player(listOf(stream), 0)) },
                    onVod = { video -> push(Route.Vod(video)) },
                    onLogin = actions.login,
                )
            }
            Route.Login -> LoginScreen(
                app = vm.app,
                onDone = {
                    vm.onLoginChanged()
                    vm.tab.value = Tab.START
                    pop()
                },
            )
            is Route.Player -> PlayerScreen(
                app = vm.app,
                channels = route.channels,
                startIndex = route.index,
                onExit = { pop() },
                onChannel = { login -> openChannel(login) },
            )
            is Route.Vod -> VodPlayerScreen(
                app = vm.app,
                video = route.video,
                onExit = {
                    pop()
                    vm.refreshHistory()
                },
                onChannel = { login -> openChannel(login) },
            )
        }
        UpdateOverlay()
    }
}
