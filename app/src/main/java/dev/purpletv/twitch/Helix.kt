package dev.purpletv.twitch

import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class NotLoggedInException : Exception("Nicht angemeldet")

/** Official Twitch API. All calls are blocking and need a logged in user. */
class Helix(private val settings: Settings, private val auth: TwitchAuth) {

    private fun get(path: String, retry: Boolean = true): JSONObject {
        val token = settings.accessToken ?: throw NotLoggedInException()
        return try {
            Http.getJson(
                "https://api.twitch.tv/helix/$path",
                mapOf("Client-Id" to settings.clientId, "Authorization" to "Bearer $token")
            )
        } catch (e: HttpStatusException) {
            if (e.code == 401 && retry && auth.refresh()) get(path, retry = false)
            else if (e.code == 401) throw NotLoggedInException()
            else throw e
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun parseStreams(json: JSONObject) = json.getJSONArray("data").mapObjects {
        StreamInfo(
            userId = it.getString("user_id"),
            login = it.getString("user_login"),
            displayName = it.optString("user_name").ifBlank { it.getString("user_login") },
            title = it.optString("title"),
            gameName = it.optString("game_name"),
            viewers = it.optInt("viewer_count"),
            startedAt = it.optString("started_at"),
            thumbnailUrl = it.optString("thumbnail_url"),
        )
    }

    fun followedStreams(): List<StreamInfo> {
        val userId = settings.userId ?: throw NotLoggedInException()
        val out = mutableListOf<StreamInfo>()
        var cursor: String? = null
        do {
            val json = get("streams/followed?user_id=$userId&first=100" + (cursor?.let { "&after=${enc(it)}" } ?: ""))
            out += parseStreams(json)
            cursor = json.optJSONObject("pagination")?.optString("cursor")?.ifBlank { null }
        } while (cursor != null && out.size < 300)
        return out.sortedByDescending { it.viewers }
    }

    fun topStreams(gameId: String? = null, language: String? = null): List<StreamInfo> {
        var path = "streams?first=60"
        if (gameId != null) path += "&game_id=$gameId"
        if (language != null) path += "&language=$language"
        return parseStreams(get(path))
    }

    fun topGames(): List<Game> = get("games/top?first=60").getJSONArray("data").mapObjects {
        Game(it.getString("id"), it.getString("name"), it.optString("box_art_url"))
    }

    fun searchChannels(query: String): List<StreamInfo> =
        get("search/channels?first=40&query=${enc(query)}").getJSONArray("data").mapObjects {
            StreamInfo(
                userId = it.getString("id"),
                login = it.getString("broadcaster_login"),
                displayName = it.optString("display_name").ifBlank { it.getString("broadcaster_login") },
                title = it.optString("title"),
                gameName = it.optString("game_name"),
                startedAt = it.optString("started_at"),
                avatarUrl = it.optString("thumbnail_url"),
                isLive = it.optBoolean("is_live"),
            )
        }.sortedByDescending { it.isLive }

    fun stream(login: String): StreamInfo? = parseStreams(get("streams?user_login=${enc(login)}")).firstOrNull()

    fun loadSelf() {
        val user = get("users").getJSONArray("data").optJSONObject(0) ?: return
        settings.userName = user.optString("display_name").ifBlank { null }
    }
}
