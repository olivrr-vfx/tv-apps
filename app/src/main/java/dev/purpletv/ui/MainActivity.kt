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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.StreamInfo

sealed interface Route {
    data object Home : Route
    data object Login : Route
    data class GameStreams(val game: Game) : Route
    data class Player(val channels: List<StreamInfo>, val index: Int) : Route
}

class MainActivity : ComponentActivity() {
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

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(Modifier.fillMaxSize().background(Colors.Background)) {
        when (val route = stack.last()) {
            Route.Home -> saveable.SaveableStateProvider("home") {
                HomeScreen(
                    vm = vm,
                    onPlay = { list, index -> push(Route.Player(list, index)) },
                    onGame = { game ->
                        vm.openGame(game)
                        push(Route.GameStreams(game))
                    },
                    onLogin = { push(Route.Login) },
                )
            }
            is Route.GameStreams -> saveable.SaveableStateProvider("game-${route.game.id}") {
                GameStreamsScreen(vm, route.game, onPlay = { list, index -> push(Route.Player(list, index)) })
            }
            Route.Login -> LoginScreen(
                app = vm.app,
                onDone = {
                    vm.onLoginChanged()
                    vm.tab.value = Tab.FOLLOWED
                    pop()
                },
            )
            is Route.Player -> PlayerScreen(
                app = vm.app,
                channels = route.channels,
                startIndex = route.index,
                onExit = { pop() },
            )
        }
    }
}
