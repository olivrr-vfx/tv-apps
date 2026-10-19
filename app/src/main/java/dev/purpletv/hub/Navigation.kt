package dev.purpletv.hub

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.purpletv.core.AppId
import dev.purpletv.twitch.api.Game
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.VideoInfo
import dev.purpletv.youtube.api.YtVideo

sealed interface Route {
    /** Top-level sections, shown inside the hub shell with the navigation rail. */
    sealed interface Section : Route
    data object Dashboard : Section
    data class App(val app: AppId) : Section
    data object Settings : Section

    // Twitch
    data object TwitchLogin : Route
    data class TwitchGame(val game: Game) : Route
    data class TwitchChannel(val login: String) : Route
    /**
     * Live player. With [followed] the zap list follows the live-updating followed list instead of
     * the snapshot in [channels], so channels that went offline drop out.
     */
    data class TwitchLive(val channels: List<StreamInfo>, val login: String, val followed: Boolean = false) : Route
    data class TwitchVod(val video: VideoInfo) : Route

    // YouTube
    data object YouTubeLogin : Route
    data class YouTubeChannel(val url: String, val name: String = "") : Route
    data class YouTubeWatch(val video: YtVideo, val queue: List<YtVideo> = emptyList()) : Route

    // Sharing from phones and computers
    data class ScreenShare(val id: String) : Route
    data class Media(val id: String) : Route
}

/** Back stack of the single activity. The first entry is always a [Route.Section]. */
@Stable
class Navigator(root: Route.Section) {
    var root: Route.Section by mutableStateOf(root)
    var stack: List<Route> by mutableStateOf(listOf(root))
        private set

    val current get() = stack.last()
    val section get() = stack.first() as Route.Section

    fun push(route: Route) {
        stack = stack + route
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    /** Switch the top-level section (rail), dropping anything opened on top. */
    fun showSection(section: Route.Section) {
        stack = listOf(section)
    }

    /** Opens [route] on top of [section], e.g. for deep links. */
    fun open(route: Route, section: Route.Section = this.section) {
        stack = if (route is Route.Section) listOf(route) else listOf(section, route)
    }

    /** Back: pop, then fall back to the root section. False = leave the app. */
    fun back(): Boolean {
        if (pop()) return true
        if (section != root) {
            showSection(root)
            return true
        }
        return false
    }

    /** Coming from a channel page into a player and back to the same channel: just go back. */
    fun openTwitchChannel(login: String) {
        val previous = stack.getOrNull(stack.size - 2)
        if (previous is Route.TwitchChannel && previous.login == login) pop() else push(Route.TwitchChannel(login))
    }

    fun openYouTubeChannel(url: String, name: String) {
        val previous = stack.getOrNull(stack.size - 2)
        if (previous is Route.YouTubeChannel && previous.url == url) pop() else push(Route.YouTubeChannel(url, name))
    }
}
