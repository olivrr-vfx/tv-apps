package dev.purpletv.data

import android.content.Context
import dev.purpletv.twitch.api.VideoInfo
import org.json.JSONObject

/** Remembers how far each VOD was watched, for resuming and the "Weiter schauen" row. */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)

    data class Entry(val video: VideoInfo, val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
        val progress get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    fun position(videoId: String): Long? = entry(videoId)?.takeIf { it.progress < 0.95f }?.positionMs

    fun save(video: VideoInfo, positionMs: Long, durationMs: Long) {
        if (positionMs < 30_000) return
        val json = JSONObject()
            .put("id", video.id).put("userId", video.userId).put("login", video.login)
            .put("userName", video.userName).put("title", video.title).put("duration", video.duration)
            .put("createdAt", video.createdAt).put("thumb", video.thumbnailUrl).put("type", video.type)
            .put("pos", positionMs).put("dur", durationMs).put("ts", System.currentTimeMillis())
        prefs.edit().putString(video.id, json.toString()).apply()
        prune()
    }

    /** Unfinished videos, most recently watched first. */
    fun continueWatching(limit: Int = 20): List<Entry> =
        prefs.all.keys.mapNotNull { entry(it) }
            .filter { it.progress < 0.95f }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    private fun entry(id: String): Entry? = runCatching {
        val j = JSONObject(prefs.getString(id, null) ?: return null)
        Entry(
            VideoInfo(
                id = j.getString("id"), userId = j.optString("userId"), login = j.optString("login"),
                userName = j.optString("userName"), title = j.optString("title"), duration = j.optString("duration"),
                createdAt = j.optString("createdAt"), thumbnailUrl = j.optString("thumb"), type = j.optString("type"),
            ),
            j.optLong("pos"), j.optLong("dur"), j.optLong("ts"),
        )
    }.getOrNull()

    private fun prune() {
        val all = prefs.all.keys.mapNotNull { entry(it) }
        if (all.size <= 100) return
        val editor = prefs.edit()
        all.sortedByDescending { it.updatedAt }.drop(100).forEach { editor.remove(it.video.id) }
        editor.apply()
    }
}
