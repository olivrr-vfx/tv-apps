package dev.purpletv.twitch

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

    /** Blocking. Resolves a login to (userId, displayName) without needing a user token. */
    fun user(login: String): Pair<String, String>? {
        val safe = login.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        val body = JSONObject().put("query", "query { user(login: \"$safe\") { id displayName } }")
        val user = JSONObject(Http.postJson(URL, body, headers))
            .optJSONObject("data")?.optJSONObject("user") ?: return null
        return user.getString("id") to user.optString("displayName", safe)
    }
}
