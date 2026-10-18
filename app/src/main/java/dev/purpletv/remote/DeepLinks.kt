package dev.purpletv.remote

import android.net.Uri

/**
 * App links usable from Home Assistant, adb or any launcher:
 *   purpletv://watch/<login>    play a live stream
 *   purpletv://channel/<login>  open the channel page (live + VODs)
 *   purpletv://vod/<id>         play a VOD
 *   purpletv://home             back to the start page
 * twitch.tv links (https://www.twitch.tv/<login>, /videos/<id>) work as well.
 */
object DeepLinks {

    fun parse(uri: Uri?): RemoteControl.Command? {
        uri ?: return null
        val segments = uri.pathSegments.orEmpty()
        return when (uri.scheme) {
            "purpletv" -> {
                val arg = segments.firstOrNull()
                when (uri.host) {
                    "watch", "live" -> arg?.let { RemoteControl.Command.OpenLive(it.lowercase()) }
                    "channel" -> arg?.let { RemoteControl.Command.OpenChannel(it.lowercase()) }
                    "vod", "video" -> arg?.let { RemoteControl.Command.OpenVod(it) }
                    "home" -> RemoteControl.Command.Home
                    else -> null
                }
            }
            "http", "https" -> when {
                segments.size >= 2 && segments[0] == "videos" -> RemoteControl.Command.OpenVod(segments[1])
                segments.size == 1 -> RemoteControl.Command.OpenLive(segments[0].lowercase())
                else -> null
            }
            else -> null
        }
    }

    fun uriFor(command: RemoteControl.Command): Uri? = when (command) {
        is RemoteControl.Command.OpenLive -> Uri.parse("purpletv://watch/${command.login}")
        is RemoteControl.Command.OpenChannel -> Uri.parse("purpletv://channel/${command.login}")
        is RemoteControl.Command.OpenVod -> Uri.parse("purpletv://vod/${command.id}")
        RemoteControl.Command.Home -> Uri.parse("purpletv://home")
        else -> null
    }
}
