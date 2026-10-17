package dev.purpletv.chat

import android.util.Log
import dev.purpletv.data.Http
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class Emote(val name: String, val url: String, val aspect: Float = 1f)

/** Third-party emotes: 7TV and BetterTTV, global and per channel. All calls are blocking. */
object EmoteRepository {
    private var global: Map<String, Emote>? = null
    private val channels = ConcurrentHashMap<String, Map<String, Emote>>()

    fun twitchEmoteUrl(id: String) = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0"

    fun forChannel(channelId: String?): Map<String, Emote> {
        val g = global ?: loadGlobal().also { global = it }
        if (channelId == null) return g
        val c = channels[channelId] ?: loadChannel(channelId).also { channels[channelId] = it }
        return g + c
    }

    private fun loadGlobal(): Map<String, Emote> {
        val out = mutableMapOf<String, Emote>()
        safely("bttv global") {
            JSONArray(Http.get("https://api.betterttv.net/3/cached/emotes/global")).bttv(out)
        }
        safely("7tv global") {
            Http.getJson("https://7tv.io/v3/emote-sets/global").optJSONArray("emotes")?.sevenTv(out)
        }
        return out
    }

    private fun loadChannel(id: String): Map<String, Emote> {
        val out = mutableMapOf<String, Emote>()
        safely("bttv channel") {
            val json = Http.getJson("https://api.betterttv.net/3/cached/users/twitch/$id")
            json.optJSONArray("sharedEmotes")?.bttv(out)
            json.optJSONArray("channelEmotes")?.bttv(out)
        }
        safely("7tv channel") {
            Http.getJson("https://7tv.io/v3/users/twitch/$id")
                .optJSONObject("emote_set")?.optJSONArray("emotes")?.sevenTv(out)
        }
        return out
    }

    private fun JSONArray.bttv(out: MutableMap<String, Emote>) {
        for (i in 0 until length()) {
            val e = getJSONObject(i)
            val name = e.optString("code")
            if (name.isBlank()) continue
            out[name] = Emote(name, "https://cdn.betterttv.net/emote/${e.getString("id")}/2x")
        }
    }

    private fun JSONArray.sevenTv(out: MutableMap<String, Emote>) {
        for (i in 0 until length()) {
            val e = getJSONObject(i)
            val name = e.optString("name")
            if (name.isBlank()) continue
            val host = e.optJSONObject("data")?.optJSONObject("host") ?: continue
            val base = host.optString("url").let { if (it.startsWith("//")) "https:$it" else it }
            val file = host.optJSONArray("files")?.let { files ->
                (0 until files.length()).map { files.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == "2x.webp" }
            }
            val aspect = file?.let { f: JSONObject ->
                val w = f.optInt("width"); val h = f.optInt("height")
                if (w > 0 && h > 0) w.toFloat() / h else 1f
            } ?: 1f
            out[name] = Emote(name, "$base/2x.webp", aspect)
        }
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w("Emotes", "$what failed: ${e.message}")
        }
    }
}
