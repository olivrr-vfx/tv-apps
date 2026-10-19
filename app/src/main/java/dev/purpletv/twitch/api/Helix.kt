package dev.purpletv.twitch.api

import dev.purpletv.core.AppLog
import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class NotLoggedInException : Exception("Nicht angemeldet")

/**
 * Official Twitch API. All calls are blocking. Account data (followed channels) needs a login;
 * public data (top streams, categories, search, channels, videos) comes from [gql] without one
 * and only falls back to Helix when that fails.
 */
class Helix(private val settings: Settings, private val auth: TwitchAuth, private val gql: TwitchGql) {

    private inline fun <T> public(fromGql: () -> T, fromHelix: () -> T): T = try {
        fromGql()
    } catch (e: Exception) {
        AppLog.e("Twitch", "GQL fehlgeschlagen", e)
        if (!auth.isLoggedIn) throw e
        fromHelix()
    }

    /** userId -> profile image, filled lazily so cards can show avatars. */
    private val avatars = ConcurrentHashMap<String, String>()

    private fun get(path: String, retry: Boolean = true): JSONObject {
        val token = settings.accessToken ?: throw NotLoggedInException()
        return try {
            Http.getJson(
                "https://api.twitch.tv/helix/$path",
                mapOf("Client-Id" to settings.clientId, "Authorization" to "Bearer $token")
            )
        } catch (e: HttpStatusException) {
            if (e.code != 401) throw e
            when {
                retry && auth.refresh(token) -> get(path, retry = false)
                !auth.isLoggedIn -> throw NotLoggedInException()
                else -> throw Exception("Twitch-Anmeldung konnte gerade nicht erneuert werden, versuch es gleich nochmal")
            }
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

    private fun withAvatars(streams: List<StreamInfo>): List<StreamInfo> {
        runCatching { loadAvatars(streams.mapNotNull { it.userId }) }
        return streams.map { s -> s.userId?.let { avatars[it] }?.let { s.copy(avatarUrl = it) } ?: s }
    }

    private fun loadAvatars(ids: List<String>) {
        ids.filterNot { avatars.containsKey(it) }.distinct().chunked(100).forEach { chunk ->
            users(chunk.joinToString("&") { "id=$it" }).forEach { avatars[it.id] = it.avatarUrl }
        }
    }

    private fun users(query: String): List<ChannelInfo> = get("users?$query").getJSONArray("data").mapObjects {
        ChannelInfo(
            id = it.getString("id"),
            login = it.getString("login"),
            displayName = it.optString("display_name").ifBlank { it.getString("login") },
            avatarUrl = it.optString("profile_image_url"),
            description = it.optString("description"),
            offlineImageUrl = it.optString("offline_image_url"),
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
        return withAvatars(out.sortedByDescending { it.viewers })
    }

    /** All followed channels (live or not), most recently followed first. */
    fun followedChannels(): List<ChannelInfo> {
        val userId = settings.userId ?: throw NotLoggedInException()
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val json = get("channels/followed?user_id=$userId&first=100" + (cursor?.let { "&after=${enc(it)}" } ?: ""))
            json.getJSONArray("data").mapObjects { it.getString("broadcaster_id") }.let { ids += it }
            cursor = json.optJSONObject("pagination")?.optString("cursor")?.ifBlank { null }
        } while (cursor != null && ids.size < 500)
        val byId = ids.chunked(100).flatMap { chunk -> users(chunk.joinToString("&") { "id=$it" }) }
            .onEach { avatars[it.id] = it.avatarUrl }
            .associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun topStreams(gameId: String? = null, language: String? = null): List<StreamInfo> =
        public({ gql.topStreams(gameId, language) }) { helixTopStreams(gameId, language) }

    private fun helixTopStreams(gameId: String?, language: String?): List<StreamInfo> {
        var path = "streams?first=60"
        if (gameId != null) path += "&game_id=$gameId"
        if (language != null) path += "&language=$language"
        return withAvatars(parseStreams(get(path)))
    }

    fun topGames(): List<Game> = public({ gql.topGames() }) { helixTopGames() }

    private fun helixTopGames(): List<Game> = get("games/top?first=60").getJSONArray("data").mapObjects {
        Game(it.getString("id"), it.getString("name"), it.optString("box_art_url"))
    }

    fun searchChannels(query: String): List<StreamInfo> =
        public({ gql.searchChannels(query) }) { helixSearchChannels(query) }

    private fun helixSearchChannels(query: String): List<StreamInfo> =
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

    fun stream(login: String): StreamInfo? =
        public({ gql.stream(login) }) { withAvatars(parseStreams(get("streams?user_login=${enc(login)}"))).firstOrNull() }

    fun channel(login: String): ChannelInfo? = public({ gql.channel(login) }) { users("login=${enc(login)}").firstOrNull() }

    /** Past broadcasts (type archive) or highlights of a channel. */
    fun videos(userId: String, type: String): List<VideoInfo> =
        public({ gql.videos(userId, type) }) { helixVideos(userId, type) }

    private fun helixVideos(userId: String, type: String): List<VideoInfo> =
        get("videos?user_id=$userId&type=$type&first=60").getJSONArray("data").mapObjects {
            VideoInfo(
                id = it.getString("id"),
                userId = it.optString("user_id"),
                login = it.optString("user_login"),
                userName = it.optString("user_name"),
                title = it.optString("title"),
                duration = it.optString("duration"),
                createdAt = it.optString("created_at"),
                thumbnailUrl = it.optString("thumbnail_url"),
                viewCount = it.optInt("view_count"),
                type = it.optString("type"),
            )
        }

    fun video(id: String): VideoInfo? = public({ gql.video(id) }) { helixVideo(id) }

    private fun helixVideo(id: String): VideoInfo? =
        get("videos?id=${enc(id)}").getJSONArray("data").mapObjects {
            VideoInfo(
                id = it.getString("id"),
                userId = it.optString("user_id"),
                login = it.optString("user_login"),
                userName = it.optString("user_name"),
                title = it.optString("title"),
                duration = it.optString("duration"),
                createdAt = it.optString("created_at"),
                thumbnailUrl = it.optString("thumbnail_url"),
                viewCount = it.optInt("view_count"),
                type = it.optString("type"),
            )
        }.firstOrNull()

    fun loadSelf() {
        val user = users("").firstOrNull() ?: return
        settings.userName = user.displayName
        avatars[user.id] = user.avatarUrl
    }
}
