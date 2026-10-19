package dev.purpletv.twitch.api

import dev.purpletv.data.Http
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Twitch's internal GraphQL API, the same one the website uses. Needed for stream playback
 * tokens, which the official Helix API does not offer.
 */
class TwitchGql(private val deviceId: String) {

    data class Token(val value: String, val signature: String)

    /** playerType/platform combinations to try when the main stream shows an ad. */
    data class PlayerKind(val playerType: String, val platform: String)

    companion object {
        const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val URL = "https://gql.twitch.tv/gql"

        val MAIN = PlayerKind("site", "web")
        val BACKUPS = listOf(
            PlayerKind("embed", "web"),
            PlayerKind("popout", "web"),
            PlayerKind("autoplay", "android"),
            PlayerKind("picture-by-picture", "web"),
        )

        private const val TOKEN_QUERY =
            "query PlaybackAccessToken_Template(\$login: String!, \$isLive: Boolean!, \$vodID: ID!, \$isVod: Boolean!, \$playerType: String!, \$platform: String!) {" +
                " streamPlaybackAccessToken(channelName: \$login, params: {platform: \$platform, playerBackend: \"mediaplayer\", playerType: \$playerType}) @include(if: \$isLive) { value signature __typename }" +
                " videoPlaybackAccessToken(id: \$vodID, params: {platform: \$platform, playerBackend: \"mediaplayer\", playerType: \$playerType}) @include(if: \$isVod) { value signature __typename } }"
    }

    private val headers get() = mapOf(
        "Client-ID" to WEB_CLIENT_ID,
        "Device-ID" to deviceId,
        "Origin" to "https://www.twitch.tv",
        "Referer" to "https://www.twitch.tv/",
    )

    /** Blocking. Returns null when the channel does not exist. */
    fun playbackToken(login: String, kind: PlayerKind = MAIN): Token? =
        token(login = login, vodId = null, kind = kind)

    /** Blocking. Returns null when the video does not exist. */
    fun vodToken(vodId: String): Token? = token(login = "", vodId = vodId, kind = MAIN)

    private fun token(login: String, vodId: String?, kind: PlayerKind): Token? {
        val body = JSONObject()
            .put("operationName", "PlaybackAccessToken_Template")
            .put("query", TOKEN_QUERY)
            .put(
                "variables", JSONObject()
                    .put("isLive", vodId == null)
                    .put("login", login)
                    .put("isVod", vodId != null)
                    .put("vodID", vodId ?: "")
                    .put("playerType", kind.playerType)
                    .put("platform", kind.platform)
            )
        val json = JSONObject(Http.postJson(URL, body, headers))
        val field = if (vodId == null) "streamPlaybackAccessToken" else "videoPlaybackAccessToken"
        val token = json.optJSONObject("data")?.optJSONObject(field) ?: return null
        return Token(token.getString("value"), token.getString("signature"))
    }

    fun vodUsherUrl(vodId: String, token: Token): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val params = linkedMapOf(
            "allow_source" to "true",
            "allow_audio_only" to "true",
            "p" to Random.nextInt(1_000_000, 9_999_999).toString(),
            "player_backend" to "mediaplayer",
            "playlist_include_framerate" to "true",
            "supported_codecs" to "avc1",
            "nauthsig" to token.signature,
            "nauth" to token.value,
        )
        return "https://usher.ttvnw.net/vod/$vodId.m3u8?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }

    fun usherUrl(login: String, token: Token, lowLatency: Boolean): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val params = linkedMapOf(
            "allow_source" to "true",
            "allow_audio_only" to "true",
            "fast_bread" to lowLatency.toString(),
            "p" to Random.nextInt(1_000_000, 9_999_999).toString(),
            "player_backend" to "mediaplayer",
            "playlist_include_framerate" to "true",
            "reassignments_supported" to "true",
            "supported_codecs" to "avc1",
            "platform" to "web",
            "sig" to token.signature,
            "token" to token.value,
        )
        return "https://usher.ttvnw.net/api/channel/hls/${login.lowercase()}.m3u8?" +
            params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
    }

    data class User(val id: String, val login: String, val displayName: String, val avatarUrl: String, val isLive: Boolean)

    /** Blocking. Resolves a login without needing a user token. Null when the channel does not exist. */
    fun user(login: String): User? {
        val safe = login.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        val body = JSONObject().put("query", "query { user(login: \"$safe\") { id login displayName profileImageURL(width: 150) stream { id } } }")
        val user = JSONObject(Http.postJson(URL, body, headers))
            .optJSONObject("data")?.optJSONObject("user") ?: return null
        return User(
            id = user.getString("id"),
            login = user.optString("login", safe),
            displayName = user.optString("displayName", safe),
            avatarUrl = user.optString("profileImageURL"),
            isLive = user.optJSONObject("stream") != null,
        )
    }

    // --- Public data without a login ------------------------------------------------------
    // The same queries the website runs for logged out visitors. Used for everything that is
    // not tied to the account, so browsing works without a login and when Helix has trouble.

    private fun query(query: String, variables: JSONObject = JSONObject(), auth: Pair<String, String>? = null): JSONObject {
        // With a login: the user's token, sent with the client that issued it.
        val h = auth?.let { (clientId, token) -> headers + mapOf("Client-ID" to clientId, "Authorization" to "OAuth $token") } ?: headers
        val json = JSONObject(Http.postJson(URL, JSONObject().put("query", query).put("variables", variables), h))
        val data = json.optJSONObject("data")
        val message = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
        // A failing field comes back as null next to an error; treat that as a failure, not as "nothing found".
        if (data == null || (message != null && data.keys().asSequence().all { data.isNull(it) })) {
            throw Exception("Twitch antwortet nicht richtig" + (message?.let { ": $it" } ?: ""))
        }
        return data
    }

    private fun JSONObject.edges(): List<JSONObject> {
        val edges = optJSONArray("edges") ?: return emptyList()
        return (0 until edges.length()).mapNotNull { edges.optJSONObject(it)?.let { e -> e.optJSONObject("node") ?: e.optJSONObject("item") } }
    }

    private fun JSONObject.str(name: String) = if (isNull(name)) "" else optString(name)

    private fun JSONObject.toStream(owner: JSONObject? = null): StreamInfo? {
        val user = owner ?: optJSONObject("broadcaster") ?: return null
        return StreamInfo(
            userId = user.str("id"),
            login = user.str("login").ifEmpty { return null },
            displayName = user.str("displayName").ifEmpty { user.str("login") },
            title = str("title"),
            gameName = optJSONObject("game")?.let { it.str("displayName").ifEmpty { it.str("name") } }.orEmpty(),
            viewers = optInt("viewersCount"),
            startedAt = str("createdAt"),
            thumbnailUrl = str("previewImageURL"),
            avatarUrl = user.str("profileImageURL"),
        )
    }

    private fun JSONObject.toVideo() = VideoInfo(
        id = str("id"),
        userId = optJSONObject("owner")?.str("id").orEmpty(),
        login = optJSONObject("owner")?.str("login").orEmpty(),
        userName = optJSONObject("owner")?.let { it.str("displayName").ifEmpty { it.str("login") } }.orEmpty(),
        title = str("title"),
        duration = optLong("lengthSeconds").let { "${it / 3600}h${(it % 3600) / 60}m${it % 60}s" },
        createdAt = str("createdAt"),
        thumbnailUrl = str("previewThumbnailURL"),
        viewCount = optInt("viewCount"),
        type = str("broadcastType").lowercase().ifEmpty { "archive" },
    )

    private val streamFields =
        "id title viewersCount createdAt previewImageURL(width: 440, height: 248) " +
            "broadcaster { id login displayName profileImageURL(width: 150) } game { id name displayName }"
    private val videoFields =
        "id title lengthSeconds createdAt viewCount broadcastType previewThumbnailURL(width: 440, height: 248) " +
            "owner { id login displayName }"
    private val userFields = "id login displayName description profileImageURL(width: 300) offlineImageURL"

    /** Most watched live streams, optionally of one category and in one language ("DE"). */
    fun topStreams(gameId: String? = null, language: String? = null, first: Int = 30): List<StreamInfo> {
        val lang = language?.uppercase()?.filter { it.isLetter() }?.takeIf { it.length == 2 }
        val options = "{sort: VIEWER_COUNT" + (lang?.let { ", broadcasterLanguages: [$it]" } ?: "") + "}"
        return if (gameId != null) {
            query("query(\$id: ID!) { game(id: \$id) { streams(first: $first, options: $options) { edges { node { $streamFields } } } } }",
                JSONObject().put("id", gameId))
                .optJSONObject("game")?.optJSONObject("streams")?.edges().orEmpty().mapNotNull { it.toStream() }
        } else {
            query("query { streams(first: $first, options: $options) { edges { node { $streamFields } } } }")
                .optJSONObject("streams")?.edges().orEmpty().mapNotNull { it.toStream() }
        }
    }

    fun topGames(first: Int = 30): List<Game> =
        query("query { games(first: $first) { edges { node { id name displayName boxArtURL(width: 285, height: 380) } } } }")
            .optJSONObject("games")?.edges().orEmpty().map {
                Game(it.str("id"), it.str("displayName").ifEmpty { it.str("name") }, it.str("boxArtURL"))
            }

    /** Channels matching [text], live ones first. */
    fun searchChannels(text: String): List<StreamInfo> =
        query("query(\$q: String!) { searchUsers(userQuery: \$q, first: 30) { edges { node { id login displayName profileImageURL(width: 150) stream { $streamFields } } } } }",
            JSONObject().put("q", text))
            .optJSONObject("searchUsers")?.edges().orEmpty().mapNotNull { user ->
                user.optJSONObject("stream")?.toStream(user)?.copy(avatarUrl = user.str("profileImageURL"))
                    ?: StreamInfo(user.str("id"), user.str("login").ifEmpty { return@mapNotNull null },
                        user.str("displayName").ifEmpty { user.str("login") }, avatarUrl = user.str("profileImageURL"), isLive = false)
            }.sortedByDescending { it.isLive }

    /** The live stream of [login], or null when offline. */
    fun stream(login: String): StreamInfo? {
        val user = query("query(\$login: String!) { user(login: \$login) { id login displayName profileImageURL(width: 150) stream { $streamFields } } }",
            JSONObject().put("login", login.lowercase())).optJSONObject("user") ?: return null
        return user.optJSONObject("stream")?.toStream(user)
    }

    fun channel(login: String): ChannelInfo? {
        val user = query("query(\$login: String!) { user(login: \$login) { $userFields } }",
            JSONObject().put("login", login.lowercase())).optJSONObject("user") ?: return null
        return ChannelInfo(user.str("id"), user.str("login"), user.str("displayName").ifEmpty { user.str("login") },
            user.str("profileImageURL"), user.str("description"), user.str("offlineImageURL"))
    }

    /** Past broadcasts ("archive") or highlights of a channel, newest first. */
    fun videos(userId: String, type: String): List<VideoInfo> {
        val kind = if (type == "highlight") "HIGHLIGHT" else "ARCHIVE"
        return query("query(\$id: ID!) { user(id: \$id) { videos(first: 30, type: $kind, sort: TIME) { edges { node { $videoFields } } } } }",
            JSONObject().put("id", userId))
            .optJSONObject("user")?.optJSONObject("videos")?.edges().orEmpty().map { it.toVideo() }
    }

    // --- Account data (login through Twitch's TV client) -----------------------------------

    /** Followed channels that are live, most viewers first. [auth] = client id to token. */
    fun followedLive(auth: Pair<String, String>): List<StreamInfo> {
        val out = mutableListOf<StreamInfo>()
        var cursor: String? = null
        do {
            val user = query("query(\$after: Cursor) { currentUser { followedLiveUsers(first: 30, after: \$after) { edges { cursor node { id login displayName profileImageURL(width: 150) stream { $streamFields } } } pageInfo { hasNextPage } } } }",
                JSONObject().put("after", cursor ?: JSONObject.NULL), auth).optJSONObject("currentUser") ?: throw NotLoggedInException()
            val conn = user.optJSONObject("followedLiveUsers") ?: break
            val edges = conn.optJSONArray("edges") ?: break
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                cursor = edge.str("cursor").ifEmpty { null }
                val node = edge.optJSONObject("node") ?: continue
                node.optJSONObject("stream")?.toStream(node)?.copy(avatarUrl = node.str("profileImageURL"))?.let { out += it }
            }
            val more = conn.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true
        } while (more && cursor != null && out.size < 300)
        return out.distinctBy { it.login }.sortedByDescending { it.viewers }
    }

    /** All followed channels, most recently followed first. */
    fun followedChannels(auth: Pair<String, String>): List<ChannelInfo> {
        val out = mutableListOf<ChannelInfo>()
        var cursor: String? = null
        do {
            val user = query("query(\$after: Cursor) { currentUser { follows(first: 30, after: \$after) { edges { cursor node { id login displayName profileImageURL(width: 150) } } pageInfo { hasNextPage } } } }",
                JSONObject().put("after", cursor ?: JSONObject.NULL), auth).optJSONObject("currentUser") ?: throw NotLoggedInException()
            val conn = user.optJSONObject("follows") ?: break
            val edges = conn.optJSONArray("edges") ?: break
            for (i in 0 until edges.length()) {
                val edge = edges.optJSONObject(i) ?: continue
                cursor = edge.str("cursor").ifEmpty { null }
                val n = edge.optJSONObject("node") ?: continue
                out += ChannelInfo(n.str("id"), n.str("login"), n.str("displayName").ifEmpty { n.str("login") }, n.str("profileImageURL"))
            }
            val more = conn.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true
        } while (more && cursor != null && out.size < 500)
        return out.distinctBy { it.id }
    }

    /** id, login and display name of the logged in user. */
    fun self(auth: Pair<String, String>): ChannelInfo? {
        val u = query("query { currentUser { id login displayName profileImageURL(width: 150) } }", auth = auth)
            .optJSONObject("currentUser") ?: return null
        return ChannelInfo(u.str("id"), u.str("login"), u.str("displayName").ifEmpty { u.str("login") }, u.str("profileImageURL"))
    }

    fun video(id: String): VideoInfo? =
        query("query(\$id: ID!) { video(id: \$id) { $videoFields } }", JSONObject().put("id", id))
            .optJSONObject("video")?.toVideo()
}
