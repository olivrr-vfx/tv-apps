package dev.purpletv.remote

import dev.purpletv.core.AppId
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.youtube.api.YtVideo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared state between the UI and the local control API (Home Assistant, phone remote).
 * Screens publish what is on, the API reads it and sends commands back.
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
        /** Seek to an absolute position (videos) in seconds. */
        data class Seek(val positionSec: Long) : Command
        /** Seek relative to the current position in seconds. */
        data class SeekBy(val deltaSec: Long) : Command
        /** A remote control key, see [Keys]. */
        data class Key(val name: String) : Command

        /** Back to the dashboard. */
        data object Home : Command
        data class OpenApp(val app: AppId) : Command
        data class OpenLive(val login: String) : Command
        data class OpenChannel(val login: String) : Command
        data class OpenVod(val id: String) : Command
        data class OpenYouTube(val videoId: String) : Command
        data class OpenYouTubeChannel(val url: String) : Command
        data class YouTubeSearch(val query: String) : Command
        /** Someone wants to share their screen or camera; [id] is the signaling session. */
        data class ShowScreenShare(val id: String) : Command
        data class CloseScreenShare(val id: String) : Command
        /** A photo or video was sent from a phone; [id] is the item in [dev.purpletv.remote.MediaInbox]. */
        data class ShowMedia(val id: String) : Command
        data object OpenYouTubeLogin : Command

        val opensScreen: Boolean
            get() = this is Home || this is OpenApp || this is OpenLive || this is OpenChannel || this is OpenVod ||
                this is OpenYouTube || this is OpenYouTubeChannel || this is YouTubeSearch ||
                this is ShowScreenShare || this is CloseScreenShare || this is ShowMedia || this == OpenYouTubeLogin
    }

    /** Remote keys accepted by /api/key, mapped to Android key codes in MainActivity. */
    object Keys {
        val all = listOf("up", "down", "left", "right", "ok", "back", "menu", "play_pause", "channel_up", "channel_down")
    }

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16)
    val commands: SharedFlow<Command> = _commands

    fun send(command: Command) = _commands.tryEmit(command)

    /** Link the activity was started or re-opened with, handled once the UI is ready. */
    val pendingLink = MutableStateFlow<Command?>(null)

    /** Key presses from the phone remote; MainActivity turns them into real key events. */
    val keys = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** What is playing right now, published by the player screens. */
    data class NowPlaying(
        val app: AppId,
        /** "live", "vod" (Twitch past broadcast) or "video" (YouTube). */
        val kind: String,
        val id: String,
        val title: String,
        val channel: String,
        /** Twitch login or YouTube channel URL. */
        val channelId: String,
        val thumbnail: String,
        val avatar: String = "",
        val game: String = "",
        val viewers: Long = 0,
        val startedAt: String = "",
        val durationSec: Long = 0,
        val positionSec: Long = 0,
        val isLive: Boolean = kind == "live",
    )

    /** True while MainActivity is visible. */
    @Volatile var foreground = false

    /** Current screen: "dashboard", "twitch", "youtube", "settings", "screen", "media". */
    @Volatile var screen = "dashboard"
    @Volatile var nowPlaying: NowPlaying? = null
    @Volatile var paused = false
    @Volatile var muted = false
    @Volatile var volume = 1f
    @Volatile var followedLive: List<StreamInfo> = emptyList()
    @Volatile var youtubeFeed: List<YtVideo> = emptyList()

    fun clearPlayer() {
        nowPlaying = null
        paused = false
    }

    fun stateJson(version: String, enabledApps: List<AppId>): JSONObject {
        val media = nowPlaying
        val screenName = when {
            !foreground -> "background"
            media?.kind == "live" -> "live"
            media?.kind == "vod" -> "vod"
            media?.kind == "video" -> "video"
            else -> screen
        }
        return JSONObject()
            .put("version", version)
            .put("screen", screenName)
            .put("app", if (!foreground) JSONObject.NULL else media?.app?.key ?: screen.takeIf { AppId.fromKey(it) != null } ?: JSONObject.NULL)
            .put("apps", JSONArray(enabledApps.map { it.key }))
            .put("playing", foreground && media != null && !paused)
            .put("paused", paused)
            .put("muted", muted)
            .put("volume", volume.toDouble())
            .put("media", media?.let(::mediaJson) ?: JSONObject.NULL)
            // Kept for Home Assistant integrations from version 1.x.
            .put("channel", media?.takeIf { it.app == AppId.TWITCH && it.kind == "live" }?.let(::legacyChannelJson) ?: JSONObject.NULL)
            .put("vod", media?.takeIf { it.app == AppId.TWITCH && it.kind == "vod" }?.let(::legacyVodJson) ?: JSONObject.NULL)
            .put("followed_live", JSONArray(followedLive.map(::streamJson)))
            .put("youtube_feed", JSONArray(youtubeFeed.take(30).map(::videoJson)))
    }

    private fun mediaJson(m: NowPlaying) = JSONObject()
        .put("app", m.app.key)
        .put("kind", m.kind)
        .put("id", m.id)
        .put("title", m.title)
        .put("channel", m.channel)
        .put("channel_id", m.channelId)
        .put("thumbnail", m.thumbnail)
        .put("avatar", m.avatar)
        .put("game", m.game)
        .put("viewers", m.viewers)
        .put("started_at", m.startedAt)
        .put("duration", m.durationSec)
        .put("position", m.positionSec)
        .put("live", m.isLive)

    private fun legacyChannelJson(m: NowPlaying) = JSONObject()
        .put("login", m.channelId)
        .put("display_name", m.channel)
        .put("title", m.title)
        .put("game", m.game)
        .put("viewers", m.viewers)
        .put("started_at", m.startedAt)
        .put("thumbnail", m.thumbnail)
        .put("avatar", m.avatar)

    private fun legacyVodJson(m: NowPlaying) = JSONObject()
        .put("id", m.id)
        .put("login", m.channelId)
        .put("display_name", m.channel)
        .put("title", m.title)
        .put("thumbnail", m.thumbnail)
        .put("duration", m.durationSec)
        .put("position", m.positionSec)

    fun streamJson(s: StreamInfo): JSONObject = JSONObject()
        .put("login", s.login)
        .put("display_name", s.displayName)
        .put("title", s.title)
        .put("game", s.gameName)
        .put("viewers", s.viewers)
        .put("started_at", s.startedAt)
        .put("thumbnail", s.thumbnail(640, 360))
        .put("avatar", s.avatarUrl)

    fun videoJson(v: YtVideo): JSONObject = JSONObject()
        .put("id", v.id)
        .put("title", v.title)
        .put("channel", v.channelName)
        .put("channel_url", v.channelUrl)
        .put("thumbnail", v.thumbnail)
        .put("duration", v.durationSec)
        .put("uploaded", v.uploadedText)
        .put("uploaded_ms", v.uploadedMillis)
        .put("live", v.isLive)
}
