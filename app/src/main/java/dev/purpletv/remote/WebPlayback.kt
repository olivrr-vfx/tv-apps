package dev.purpletv.remote

import android.net.Uri
import dev.purpletv.PurpleApp
import dev.purpletv.data.Http
import dev.purpletv.twitch.player.AdBlockEngine
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Lets browsers (Mac, phone) watch Twitch through the TV: the TV fetches the playback token,
 * runs the playlists through the same ad blocker as the TV player and hands everything out under
 * its own address. Segments are relayed too, so the browser never talks to Twitch directly
 * (no CORS problems, same behaviour everywhere).
 *
 * Every stream gets a random session id; proxied URLs only work with it and only for Twitch hosts.
 */
class WebPlayback(private val app: PurpleApp) {

    private class Session(val engine: AdBlockEngine?, val createdAt: Long = System.currentTimeMillis())

    private val sessions = ConcurrentHashMap<String, Session>()
    private val random = SecureRandom()

    private val allowedHosts = listOf("ttvnw.net", "twitch.tv", "jtvnw.net", "cloudfront.net", "akamaized.net")

    private fun newSession(engine: AdBlockEngine?): String {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.createdAt > 8 * 3600_000L }
        val id = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        sessions[id] = Session(engine)
        return id
    }

    /** Master playlist for a live channel, or null when it is offline. Blocking. */
    fun liveMaster(login: String): String? {
        val gql = app.gql
        val token = gql.playbackToken(login) ?: return null
        val usher = gql.usherUrl(login, token, lowLatency = false)
        val engine = AdBlockEngine(gql, { app.settings.adBlock }, { false }).apply { reset(login) }
        val id = newSession(engine)
        val text = engine.intercept(Uri.parse(usher))?.let { String(it) } ?: Http.get(usher)
        return rewrite(text, usher, id)
    }

    /** Master playlist of a Twitch VOD. Blocking. */
    fun vodMaster(videoId: String): String? {
        val gql = app.gql
        val token = gql.vodToken(videoId) ?: return null
        val url = gql.vodUsherUrl(videoId, token)
        return rewrite(Http.get(url), url, newSession(null))
    }

    /** Relays a playlist (rewritten again) or a media segment. */
    fun proxy(sessionId: String?, url: String?): Response {
        val session = sessionId?.let { sessions[it] }
        if (session == null || url.isNullOrBlank()) return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "unknown session")
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        if (allowedHosts.none { host == it || host.endsWith(".$it") }) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "host not allowed")
        }
        val path = runCatching { URI(url).path }.getOrNull().orEmpty()
        if (path.endsWith(".m3u8")) {
            val text = session.engine?.intercept(Uri.parse(url))?.let { String(it) } ?: Http.get(url)
            return playlist(rewrite(text, url, sessionId))
        }
        val response = Http.client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "segment ${response.code}")
        }
        val type = response.header("Content-Type") ?: "video/mp2t"
        val length = body.contentLength()
        return (if (length > 0) NanoHTTPD.newFixedLengthResponse(Response.Status.OK, type, body.byteStream(), length)
        else NanoHTTPD.newChunkedResponse(Response.Status.OK, type, body.byteStream())).apply {
            addHeader("Cache-Control", "no-cache")
        }
    }

    fun playlist(text: String): Response =
        NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", text).apply {
            addHeader("Cache-Control", "no-cache")
        }

    private fun proxied(url: String, sessionId: String) =
        "/play/p?s=$sessionId&u=" + URLEncoder.encode(url, "UTF-8")

    private val uriAttr = Regex("URI=\"([^\"]+)\"")

    /** Points every URI in the playlist (variants, segments, init sections) at the proxy. */
    private fun rewrite(text: String, baseUrl: String, sessionId: String): String {
        val base = URI(baseUrl)
        return text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith("#") -> if (trimmed.startsWith("#EXT-X-TWITCH-PREFETCH")) {
                    // Low-latency hint only Twitch's player understands.
                    ""
                } else {
                    uriAttr.replace(line) { m -> "URI=\"${proxied(base.resolve(m.groupValues[1]).toString(), sessionId)}\"" }
                }
                else -> proxied(base.resolve(trimmed).toString(), sessionId)
            }
        }
    }
}
