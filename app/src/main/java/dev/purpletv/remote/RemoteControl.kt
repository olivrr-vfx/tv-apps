package dev.purpletv.remote

import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared state between the UI and the local control API (Home Assistant).
 * The players publish what is on screen, the API reads it and sends commands back.
 */
object RemoteControl {

    sealed interface Command {
        data object Play : Command
        data object Pause : Command
        data object Toggle : Command
        data class Mute(val mute: Boolean) : Command
        data class Volume(val level: Float) : Command
        data object Next : Command
        data object Previous : Command
        data object Home : Command
        data class OpenLive(val login: String) : Command
        data class OpenChannel(val login: String) : Command
        data class OpenVod(val id: String) : Command
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands

    fun send(command: Command) = _commands.tryEmit(command)

    /** Link the activity was started or re-opened with, handled once the UI is ready. */
    val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<Command?>(null)

    /** True while MainActivity is visible. */
    @Volatile var foreground = false

    @Volatile var live: StreamInfo? = null
    @Volatile var vod: VideoInfo? = null
    @Volatile var vodPositionMs = 0L
    @Volatile var paused = false
    @Volatile var muted = false
    @Volatile var volume = 1f
    @Volatile var followedLive: List<StreamInfo> = emptyList()

    fun clearPlayer() {
        live = null
        vod = null
        paused = false
    }

    fun stateJson(version: String): JSONObject {
        val live = live
        val vod = vod
        val screen = when {
            !foreground -> "background"
            live != null -> "live"
            vod != null -> "vod"
            else -> "home"
        }
        return JSONObject()
            .put("version", version)
            .put("screen", screen)
            .put("playing", foreground && (live != null || vod != null) && !paused)
            .put("paused", paused)
            .put("muted", muted)
            .put("volume", volume.toDouble())
            .put("channel", live?.let(::streamJson) ?: JSONObject.NULL)
            .put(
                "vod",
                vod?.let {
                    JSONObject()
                        .put("id", it.id).put("login", it.login).put("display_name", it.userName)
                        .put("title", it.title).put("thumbnail", it.thumbnail(640, 360))
                        .put("duration", it.durationSec).put("position", vodPositionMs / 1000)
                } ?: JSONObject.NULL
            )
            .put("followed_live", JSONArray(followedLive.map(::streamJson)))
    }

    private fun streamJson(s: StreamInfo) = JSONObject()
        .put("login", s.login)
        .put("display_name", s.displayName)
        .put("title", s.title)
        .put("game", s.gameName)
        .put("viewers", s.viewers)
        .put("started_at", s.startedAt)
        .put("thumbnail", s.thumbnail(640, 360))
        .put("avatar", s.avatarUrl)
}
