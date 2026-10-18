package dev.purpletv.youtube.api

import org.json.JSONObject

data class YtVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelUrl: String,
    val thumbnail: String = thumbnailFor(id),
    val durationSec: Long = 0,
    val views: Long = -1,
    val uploadedText: String = "",
    val uploadedMillis: Long = 0,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val channelAvatar: String = "",
) {
    val url get() = "https://www.youtube.com/watch?v=$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("channelName", channelName).put("channelUrl", channelUrl)
        .put("thumbnail", thumbnail).put("duration", durationSec).put("views", views)
        .put("uploadedText", uploadedText).put("uploadedMillis", uploadedMillis)
        .put("live", isLive).put("short", isShort).put("avatar", channelAvatar)

    companion object {
        /** hqdefault exists for every video; it is 4:3 with bars, which ContentScale.Crop removes. */
        fun thumbnailFor(id: String) = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

        fun fromJson(j: JSONObject) = YtVideo(
            id = j.getString("id"),
            title = j.optString("title"),
            channelName = j.optString("channelName"),
            channelUrl = j.optString("channelUrl"),
            thumbnail = j.optString("thumbnail").ifEmpty { thumbnailFor(j.getString("id")) },
            durationSec = j.optLong("duration"),
            views = j.optLong("views", -1),
            uploadedText = j.optString("uploadedText"),
            uploadedMillis = j.optLong("uploadedMillis"),
            isLive = j.optBoolean("live"),
            isShort = j.optBoolean("short"),
            channelAvatar = j.optString("avatar"),
        )
    }
}

data class YtChannel(
    val id: String,
    val url: String,
    val name: String,
    val avatar: String = "",
    val subscribers: Long = -1,
    val banner: String = "",
    val description: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("name", name).put("avatar", avatar)

    companion object {
        fun fromJson(j: JSONObject) = YtChannel(
            id = j.getString("id"),
            url = j.optString("url").ifEmpty { "https://www.youtube.com/channel/${j.getString("id")}" },
            name = j.optString("name"),
            avatar = j.optString("avatar"),
        )
    }
}

data class YtChannelPage(val channel: YtChannel, val videos: List<YtVideo>, val live: List<YtVideo>)

data class YtSearchResult(val videos: List<YtVideo>, val channels: List<YtChannel>)

/** Accepts video ids, watch/short/live/youtu.be links. */
fun parseVideoId(input: String): String? {
    val s = input.trim()
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(s)) return s
    val patterns = listOf(
        Regex("[?&]v=([A-Za-z0-9_-]{11})"),
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
        Regex("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})"),
    )
    return patterns.firstNotNullOfOrNull { it.find(s)?.groupValues?.get(1) }
}

/** "UC..." from a /channel/ URL, null for handles (@name) and other forms. */
fun channelIdFromUrl(url: String): String? = Regex("/channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.get(1)

fun formatCount(n: Long): String = when {
    n < 0 -> ""
    n >= 1_000_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f Mrd.", n / 1_000_000_000.0)
    n >= 1_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f Mio.", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.GERMANY, "%.0f Tsd.", n / 1_000.0)
    else -> n.toString()
}
