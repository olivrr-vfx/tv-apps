package dev.purpletv.remote

import android.net.Uri
import dev.purpletv.core.AppId
import dev.purpletv.remote.RemoteControl.Command
import dev.purpletv.youtube.api.parseVideoId

/**
 * App links usable from Home Assistant, adb or any launcher:
 *   purpletv://home                        dashboard
 *   purpletv://app/<twitch|youtube>        open an app
 *   purpletv://twitch, purpletv://youtube  same
 *   purpletv://watch/<login>               Twitch live stream
 *   purpletv://channel/<login>             Twitch channel page (live + VODs)
 *   purpletv://vod/<id>                    Twitch VOD
 *   purpletv://youtube/watch/<id>          YouTube video
 *   purpletv://youtube/channel?url=<url>   YouTube channel
 *   purpletv://youtube/search?q=<text>     YouTube search
 * twitch.tv and youtube.com / youtu.be links work as well.
 */
object DeepLinks {

    fun parse(uri: Uri?): Command? {
        uri ?: return null
        val segments = uri.pathSegments.orEmpty()
        val arg = segments.firstOrNull()
        return when (uri.scheme) {
            "purpletv" -> when (uri.host) {
                "watch", "live" -> arg?.let { Command.OpenLive(it.lowercase()) }
                "channel" -> arg?.let { Command.OpenChannel(it.lowercase()) }
                "vod", "video" -> arg?.let { Command.OpenVod(it) }
                "home", "dashboard" -> Command.Home
                "app" -> AppId.fromKey(arg)?.let { Command.OpenApp(it) }
                "twitch" -> when (arg) {
                    null -> Command.OpenApp(AppId.TWITCH)
                    "watch", "live" -> segments.getOrNull(1)?.let { Command.OpenLive(it.lowercase()) }
                    "channel" -> segments.getOrNull(1)?.let { Command.OpenChannel(it.lowercase()) }
                    "vod" -> segments.getOrNull(1)?.let { Command.OpenVod(it) }
                    else -> Command.OpenLive(arg.lowercase())
                }
                "youtube" -> when (arg) {
                    null -> Command.OpenApp(AppId.YOUTUBE)
                    "watch", "video" -> segments.getOrNull(1)?.let { Command.OpenYouTube(it) }
                    "channel" -> (uri.getQueryParameter("url") ?: segments.getOrNull(1)?.let { channelUrl(it) })?.let { Command.OpenYouTubeChannel(it) }
                    "search" -> uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.let { Command.YouTubeSearch(it) }
                    else -> parseVideoId(arg)?.let { Command.OpenYouTube(it) }
                }
                else -> null
            }
            "http", "https" -> parseWeb(uri.toString(), uri.host.orEmpty(), segments)
            else -> null
        }
    }

    /** Free text from the API: twitch/youtube links, "yt:<id>", or a Twitch channel name. */
    fun parseText(text: String): Command? {
        val t = text.trim()
        if (t.startsWith("http")) return parse(Uri.parse(t))
        if (t.startsWith("purpletv://")) return parse(Uri.parse(t))
        return null
    }

    private fun parseWeb(url: String, host: String, segments: List<String>): Command? = when {
        host.endsWith("youtube.com") || host == "youtu.be" -> when {
            segments.firstOrNull()?.startsWith("@") == true || segments.firstOrNull() == "channel" -> Command.OpenYouTubeChannel(url)
            else -> parseVideoId(url)?.let { Command.OpenYouTube(it) }
        }
        segments.size >= 2 && segments[0] == "videos" -> Command.OpenVod(segments[1])
        segments.size == 1 -> Command.OpenLive(segments[0].lowercase())
        else -> null
    }

    private fun channelUrl(idOrHandle: String) =
        if (idOrHandle.startsWith("@")) "https://www.youtube.com/$idOrHandle" else "https://www.youtube.com/channel/$idOrHandle"

    fun uriFor(command: Command): Uri? = when (command) {
        is Command.OpenLive -> Uri.parse("purpletv://watch/${command.login}")
        is Command.OpenChannel -> Uri.parse("purpletv://channel/${command.login}")
        is Command.OpenVod -> Uri.parse("purpletv://vod/${command.id}")
        is Command.OpenApp -> Uri.parse("purpletv://app/${command.app.key}")
        is Command.OpenYouTube -> Uri.parse("purpletv://youtube/watch/${command.videoId}")
        is Command.OpenYouTubeChannel -> Uri.parse("purpletv://youtube/channel?url=${Uri.encode(command.url)}")
        is Command.YouTubeSearch -> Uri.parse("purpletv://youtube/search?q=${Uri.encode(command.query)}")
        Command.Home -> Uri.parse("purpletv://home")
        else -> null
    }
}
