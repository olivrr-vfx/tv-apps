package dev.purpletv.twitch

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class StreamInfo(
    val userId: String?,
    val login: String,
    val displayName: String,
    val title: String = "",
    val gameName: String = "",
    val viewers: Int = 0,
    val startedAt: String = "",
    val thumbnailUrl: String = "",
    val avatarUrl: String = "",
    val isLive: Boolean = true,
) {
    fun thumbnail(width: Int = 440, height: Int = 248): String {
        if (thumbnailUrl.isEmpty()) return ""
        // Twitch caches previews for ~5 minutes, bust it at the same rate.
        val bucket = System.currentTimeMillis() / 300_000
        return thumbnailUrl.replace("{width}", "$width").replace("{height}", "$height") + "?t=$bucket"
    }

    fun startedAtMillis(): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(startedAt)?.time
    }.getOrNull()
}

data class Game(val id: String, val name: String, val boxArtUrl: String) {
    fun boxArt(width: Int = 188, height: Int = 250) =
        boxArtUrl.replace("{width}", "$width").replace("{height}", "$height")
}

fun formatViewers(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.GERMANY, "%.1f Mio.", n / 1_000_000.0)
    n >= 10_000 -> String.format(Locale.GERMANY, "%.1fK", n / 1000.0)
    else -> String.format(Locale.GERMANY, "%,d", n)
}

fun formatUptime(startMillis: Long?): String {
    if (startMillis == null) return ""
    val s = (System.currentTimeMillis() - startMillis) / 1000
    if (s < 0) return ""
    return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
