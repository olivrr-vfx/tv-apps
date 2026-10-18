package dev.purpletv.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dev.purpletv.BuildConfig
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.core.items
import dev.purpletv.data.Settings
import dev.purpletv.hub.MainActivity
import dev.purpletv.remote.RemoteControl.Command
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.parseVideoId
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Small HTTP server on the local network:
 *  - `/api/...` for Home Assistant and the phone remote (see README for the endpoints),
 *  - `/` the phone remote web app (add it to the iPhone home screen),
 *  - `/design` the design system reference.
 *
 * Every API call except /api/info needs the pairing code shown in the settings
 * (header `Authorization: Bearer <code>` or `?token=<code>`).
 */
class RemoteServer(private val app: PurpleApp) : NanoHTTPD(PORT) {

    companion object {
        const val PORT = 8765
        private const val SERVICE_TYPE = "_purpletv._tcp."

        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

        /** Opens the phone remote already paired with this TV. */
        fun pairingUrl(settings: Settings): String =
            "http://${localAddresses().firstOrNull() ?: "tv"}:$PORT/?code=${settings.remoteCode}"

        private val STATIC = mapOf(
            "/" to ("web/index.html" to "text/html; charset=utf-8"),
            "/index.html" to ("web/index.html" to "text/html; charset=utf-8"),
            "/app.js" to ("web/app.js" to "application/javascript; charset=utf-8"),
            "/app.css" to ("web/app.css" to "text/css; charset=utf-8"),
            "/tokens.css" to ("web/tokens.css" to "text/css; charset=utf-8"),
            "/design" to ("web/design.html" to "text/html; charset=utf-8"),
            "/icon.svg" to ("web/icon.svg" to "image/svg+xml"),
            "/icon-180.png" to ("web/icon-180.png" to "image/png"),
            "/icon-512.png" to ("web/icon-512.png" to "image/png"),
        )
    }

    private val context: Context get() = app
    private val settings get() = app.settings
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    fun startAll() {
        try {
            start(SOCKET_READ_TIMEOUT, true)
            advertise()
        } catch (e: Exception) {
            Log.w("RemoteServer", "could not start", e)
        }
    }

    fun stopAll() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }
        nsdListener = null
        stop()
    }

    /** Announces the API via mDNS so Home Assistant discovers the TV by itself. */
    private fun advertise() {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "PurpleTV"
            serviceType = SERVICE_TYPE
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        nsd = manager
        nsdListener = listener
    }

    override fun serve(session: IHTTPSession): Response = try {
        if (session.method == Method.OPTIONS) {
            cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        } else {
            if (session.method == Method.POST) runCatching { session.parseBody(HashMap()) }
            val path = session.uri.trimEnd('/').ifEmpty { "/" }
            if (path.startsWith("/api/")) cors(handleApi(session, path)) else handleStatic(session, path)
        }
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "error"))
    }

    private fun cors(response: Response): Response = response.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    // --- Web app ---------------------------------------------------------------------------

    private fun handleStatic(session: IHTTPSession, path: String): Response {
        if (path == "/manifest.webmanifest") return manifest(session.parameters["code"]?.firstOrNull())
        val (asset, mime) = STATIC[path] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val bytes = runCatching { context.assets.open(asset).use { it.readBytes() } }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "no-cache")
        }
    }

    /** The manifest carries the code in start_url, so the home screen app opens already paired. */
    private fun manifest(code: String?): Response {
        val start = if (code.isNullOrBlank()) "/" else "/?code=$code"
        val body = JSONObject()
            .put("name", "PurpleTV Fernbedienung")
            .put("short_name", "PurpleTV")
            .put("start_url", start)
            .put("display", "standalone")
            .put("background_color", "#09090E")
            .put("theme_color", "#09090E")
            .put("icons", JSONArray()
                .put(JSONObject().put("src", "/icon-180.png").put("sizes", "180x180").put("type", "image/png"))
                .put(JSONObject().put("src", "/icon-512.png").put("sizes", "512x512").put("type", "image/png")))
        return newFixedLengthResponse(Response.Status.OK, "application/manifest+json", body.toString())
    }

    // --- API -------------------------------------------------------------------------------

    private fun handleApi(session: IHTTPSession, path: String): Response {
        if (path == "/api/info") {
            return json(Response.Status.OK, JSONObject().put("name", "PurpleTV").put("version", BuildConfig.VERSION_NAME)
                .put("apps", JSONArray(app.enabledApps().map { it.key })))
        }
        val params = session.parameters
        fun param(name: String) = params[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val auth = session.headers["authorization"]?.removePrefix("Bearer")?.trim() ?: param("token")
        if (auth != settings.remoteCode) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "invalid code"))
        }

        if (session.method == Method.GET) {
            return when (path) {
                "/api/state" -> json(Response.Status.OK, RemoteControl.stateJson(BuildConfig.VERSION_NAME, app.enabledApps()))
                "/api/twitch/live" -> json(Response.Status.OK, JSONObject().put("channels", JSONArray(app.twitch.followed.value.items().map(RemoteControl::streamJson))))
                "/api/youtube/feed" -> json(Response.Status.OK, JSONObject().put("videos", JSONArray(app.youtube.feed.value.items().take(60).map(RemoteControl::videoJson))))
                "/api/youtube/subscriptions" -> json(Response.Status.OK, JSONObject().put("channels", JSONArray(app.youtube.subscriptions.value.map { it.toJson() })))
                "/api/youtube/search" -> {
                    val q = param("q") ?: return badRequest("q")
                    YouTubeClient.init(settings.youtubeRegion)
                    val result = runCatching { YouTubeClient.search(q) }.getOrElse {
                        return json(Response.Status.SERVICE_UNAVAILABLE, JSONObject().put("error", it.message ?: "YouTube nicht erreichbar"))
                    }
                    json(Response.Status.OK, JSONObject()
                        .put("videos", JSONArray(result.videos.map(RemoteControl::videoJson)))
                        .put("channels", JSONArray(result.channels.map { it.toJson().put("subscribers", it.subscribers) })))
                }
                else -> json(Response.Status.METHOD_NOT_ALLOWED, JSONObject().put("error", "use POST"))
            }
        }
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, JSONObject().put("error", "use GET or POST"))

        // Management calls that answer with data instead of a screen change.
        when (path) {
            "/api/youtube/subscribe" -> {
                val input = param("url") ?: param("channel") ?: return badRequest("url")
                val channel = runCatching { runBlocking { app.youtube.subscribeByInput(input) } }.getOrElse {
                    return json(Response.Status.BAD_REQUEST, JSONObject().put("error", it.message ?: "Kanal nicht gefunden"))
                }
                return json(Response.Status.OK, JSONObject().put("ok", true).put("channel", channel.toJson()))
            }
            "/api/youtube/unsubscribe" -> {
                app.youtube.unsubscribe(param("id") ?: return badRequest("id"))
                return json(Response.Status.OK, JSONObject().put("ok", true))
            }
            "/api/key" -> {
                val key = param("code")?.lowercase() ?: return badRequest("code")
                if (key !in RemoteControl.Keys.all) return badRequest("code")
                if (!RemoteControl.foreground) return json(Response.Status.CONFLICT, JSONObject().put("error", "PurpleTV ist nicht im Vordergrund"))
                RemoteControl.keys.tryEmit(key)
                return json(Response.Status.OK, JSONObject().put("ok", true))
            }
        }

        val command: Command = when (path) {
            "/api/play" -> Command.Play
            "/api/pause" -> Command.Pause
            "/api/toggle" -> Command.Toggle
            "/api/next" -> Command.Next
            "/api/previous" -> Command.Previous
            "/api/home" -> Command.Home
            "/api/mute" -> Command.Mute(param("mute")?.toBooleanStrictOrNull() ?: !RemoteControl.muted)
            "/api/volume" -> Command.Volume(param("level")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: return badRequest("level"))
            "/api/seek" -> param("position")?.toLongOrNull()?.let { Command.Seek(it) }
                ?: param("delta")?.toLongOrNull()?.let { Command.SeekBy(it) }
                ?: return badRequest("position")
            "/api/app" -> when (val name = param("name")?.lowercase()) {
                "dashboard", "home" -> Command.Home
                else -> AppId.fromKey(name)?.let { Command.OpenApp(it) } ?: return badRequest("name")
            }
            "/api/open" -> {
                val target = param("login") ?: param("url") ?: return badRequest("login")
                DeepLinks.parseText(target) ?: Command.OpenLive(target.lowercase())
            }
            "/api/channel" -> Command.OpenChannel(param("login")?.lowercase() ?: return badRequest("login"))
            "/api/vod" -> Command.OpenVod(param("id") ?: return badRequest("id"))
            "/api/youtube" -> {
                val raw = param("id") ?: param("url") ?: return badRequest("id")
                Command.OpenYouTube(parseVideoId(raw) ?: return badRequest("id"))
            }
            "/api/youtube/channel" -> Command.OpenYouTubeChannel(param("url") ?: return badRequest("url"))
            "/api/youtube/search" -> Command.YouTubeSearch(param("q") ?: return badRequest("q"))
            else -> return json(Response.Status.NOT_FOUND, JSONObject().put("error", "unknown endpoint"))
        }

        if (command.opensScreen && !RemoteControl.foreground) {
            // App is in the background: bring it up through its deep link.
            launch(DeepLinks.uriFor(command))
        } else {
            RemoteControl.send(command)
        }
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun launch(uri: Uri?) {
        uri ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }.onFailure { Log.w("RemoteServer", "launch failed", it) }
    }

    private fun badRequest(param: String) =
        json(Response.Status.BAD_REQUEST, JSONObject().put("error", "missing or invalid parameter '$param'"))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
