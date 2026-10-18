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

    fun startedAtMillis(): Long? = parseTwitchDate(startedAt)
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

data class ChannelInfo(
    val id: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String = "",
    val description: String = "",
    val offlineImageUrl: String = "",
) {
    fun toStream(isLive: Boolean = true) = StreamInfo(id, login, displayName, avatarUrl = avatarUrl, isLive = isLive)
}

data class VideoInfo(
    val id: String,
    val userId: String,
    val login: String,
    val userName: String,
    val title: String,
    /** Twitch format, e.g. "3h2m15s". */
    val duration: String,
    val createdAt: String,
    val thumbnailUrl: String,
    val viewCount: Int = 0,
    val type: String = "archive",
) {
    fun thumbnail(width: Int = 440, height: Int = 248): String =
        thumbnailUrl.replace("%{width}", "$width").replace("%{height}", "$height")
            .replace("{width}", "$width").replace("{height}", "$height")

    val durationSec: Long
        get() {
            var total = 0L
            Regex("(\\d+)([hms])").findAll(duration).forEach {
                val n = it.groupValues[1].toLong()
                total += when (it.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
            }
            return total
        }

    fun createdAtMillis(): Long? = parseTwitchDate(createdAt)
}

fun parseTwitchDate(s: String): Long? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(s)?.time
}.getOrNull()

fun formatDuration(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun formatAgo(millis: Long?): String {
    if (millis == null) return ""
    val minutes = (System.currentTimeMillis() - millis) / 60_000
    return when {
        minutes < 60 -> "vor ${minutes.coerceAtLeast(1)} Min."
        minutes < 24 * 60 -> "vor ${minutes / 60} Std."
        minutes < 2 * 24 * 60 -> "gestern"
        minutes < 30L * 24 * 60 -> "vor ${minutes / (24 * 60)} Tagen"
        minutes < 365L * 24 * 60 -> "vor ${minutes / (30 * 24 * 60)} Monat" + if (minutes >= 60L * 24 * 60) "en" else ""
        else -> "vor über einem Jahr"
    }
}
