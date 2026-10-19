package dev.purpletv.remote

import dev.purpletv.core.AppLog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dev.purpletv.BuildConfig
import dev.purpletv.PurpleApp
import dev.purpletv.R
import dev.purpletv.core.AppId
import dev.purpletv.core.HUB_NAME
import dev.purpletv.core.items
import dev.purpletv.data.Settings
import dev.purpletv.hub.MainActivity
import dev.purpletv.remote.RemoteControl.Command
import dev.purpletv.twitch.api.NotLoggedInException
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.youtube.api.YouTubeAccount
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.parseVideoId
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

/**
 * Small web server on the local network, run twice: plain HTTP on [PORT] and HTTPS on
 * [HTTPS_PORT] (self-signed; browsers only allow screen sharing on HTTPS).
 *
 *  - `/api/...` for Home Assistant and the Couch web app (see README for the endpoints),
 *  - `/` the Couch web app for phones and computers, `/send?url=` to send a link to the TV,
 *  - `/play/...` Twitch streams for the browser player, `/receiver` the TV's screen share page,
 *  - `/design` the design system reference.
 *
 * Every API call except /api/info needs the pairing code shown in the settings
 * (header `Authorization: Bearer <code>` or `?token=<code>`).
 */
class RemoteServer(private val app: PurpleApp, private val secure: Boolean = false) : NanoHTTPD(if (secure) HTTPS_PORT else PORT) {

    companion object {
        const val PORT = 8765
        const val HTTPS_PORT = 8766
        private const val SERVICE_TYPE = "_purpletv._tcp."
        private val TLS_PASSWORD = "couchtv".toCharArray()

        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

        private fun host() = localAddresses().firstOrNull() ?: "tv"

        /** Opens the Couch web app already paired with this TV. */
        fun pairingUrl(settings: Settings): String = "http://${host()}:$PORT/?code=${settings.remoteCode}"

        /** Same over HTTPS, needed for sharing the screen from a computer. */
        fun secureUrl(settings: Settings): String = "https://${host()}:$HTTPS_PORT/?code=${settings.remoteCode}"

        private val TYPES = mapOf(
            "html" to "text/html; charset=utf-8",
            "js" to "application/javascript; charset=utf-8",
            "css" to "text/css; charset=utf-8",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "json" to "application/json",
        )

        /** Pages of the web app: every one is index.html, the script picks the view. */
        private val PAGES = mapOf(
            "/" to "index.html",
            "/send" to "index.html",
            "/design" to "design.html",
            "/receiver" to "receiver.html",
        )
    }

    private val context: Context get() = app
    private val settings get() = app.settings
    private var nsd: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    fun startAll() {
        try {
            if (secure) makeSecure(sslFactory(), null)
            start(SOCKET_READ_TIMEOUT, true)
            if (!secure) advertise()
        } catch (e: Exception) {
            Log.w("RemoteServer", "could not start (secure=$secure)", e)
        }
    }

    fun stopAll() {
        runCatching { nsdListener?.let { nsd?.unregisterService(it) } }
        nsdListener = null
        stop()
    }

    private fun sslFactory() = run {
        val keyStore = KeyStore.getInstance("PKCS12")
        context.resources.openRawResource(R.raw.couch_tls).use { keyStore.load(it, TLS_PASSWORD) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, TLS_PASSWORD)
        makeSSLSocketFactory(keyStore, kmf)
    }

    /** Announces the API via mDNS so Home Assistant discovers the TV by itself. */
    private fun advertise() {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = HUB_NAME
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
            val body = HashMap<String, String>()
            if (session.method == Method.POST) runCatching { session.parseBody(body) }
            val path = session.uri.trimEnd('/').ifEmpty { "/" }
            when {
                path.startsWith("/api/") -> cors(handleApi(session, path, body))
                path.startsWith("/play/") -> cors(handlePlay(session, path))
                else -> handleStatic(path)
            }
        }
    } catch (e: Exception) {
        Log.w("RemoteServer", "request failed", e)
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "error"))
    }

    private fun cors(response: Response): Response = response.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    // --- Web app ---------------------------------------------------------------------------

    private fun handleStatic(path: String): Response {
        if (path == "/manifest.webmanifest") return manifest()
        val name = PAGES[path] ?: path.removePrefix("/").takeIf { Regex("^[a-z0-9.-]+$").matches(it) }
            ?: return notFound()
        val bytes = runCatching { context.assets.open("web/$name").use { it.readBytes() } }.getOrNull() ?: return notFound()
        val type = TYPES[name.substringAfterLast('.')] ?: "application/octet-stream"
        return newFixedLengthResponse(Response.Status.OK, type, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "no-cache")
        }
    }

    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

    /** The manifest carries the code in start_url, so the home screen app opens already paired. */
    private fun manifest(): Response {
        val body = JSONObject()
            .put("name", HUB_NAME)
            .put("short_name", HUB_NAME)
            .put("start_url", "/?code=${settings.remoteCode}")
            .put("display", "standalone")
            .put("background_color", "#09090E")
            .put("theme_color", "#09090E")
            .put("icons", JSONArray()
                .put(JSONObject().put("src", "/icon-180.png").put("sizes", "180x180").put("type", "image/png"))
                .put(JSONObject().put("src", "/icon-512.png").put("sizes", "512x512").put("type", "image/png")))
        return newFixedLengthResponse(Response.Status.OK, "application/manifest+json", body.toString())
    }

    // --- Browser playback ------------------------------------------------------------------

    private fun handlePlay(session: IHTTPSession, path: String): Response {
        fun param(name: String) = session.parameters[name]?.firstOrNull()
        // Segment and playlist relays carry their own random session id.
        if (path == "/play/p") return app.webPlayback.proxy(param("s"), param("u"))
        if (param("token") != settings.remoteCode) return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "invalid code"))
        val vod = Regex("^/play/twitch/vod/(\\d+)\\.m3u8$").find(path)?.groupValues?.get(1)
        val live = Regex("^/play/twitch/([A-Za-z0-9_]+)\\.m3u8$").find(path)?.groupValues?.get(1)
        val text = when {
            vod != null -> app.webPlayback.vodMaster(vod)
            live != null -> app.webPlayback.liveMaster(live.lowercase())
            else -> return notFound()
        } ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", if (vod != null) "Video nicht gefunden" else "Kanal ist offline"))
        return app.webPlayback.playlist(text)
    }

    // --- API -------------------------------------------------------------------------------

    private fun handleApi(session: IHTTPSession, path: String, body: Map<String, String>): Response {
        if (path == "/api/info") {
            return json(Response.Status.OK, JSONObject()
                .put("name", HUB_NAME)
                .put("version", BuildConfig.VERSION_NAME)
                .put("apps", JSONArray(app.enabledApps().map { it.key }))
                .put("https_port", HTTPS_PORT))
        }
        val params = session.parameters
        fun param(name: String) = params[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val auth = session.headers["authorization"]?.removePrefix("Bearer")?.trim() ?: param("token")
        if (auth != settings.remoteCode) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "invalid code"))
        }

        if (session.method == Method.GET) return handleGet(path, ::param)
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, JSONObject().put("error", "use GET or POST"))

        // Calls that answer with data instead of (only) changing the screen.
        when (path) {
            "/api/youtube/subscribe" -> {
                val input = param("url") ?: param("channel") ?: return badRequest("url")
                val channel = runCatching { runBlocking { app.youtube.subscribeByInput(input) } }.getOrElse {
                    return json(Response.Status.BAD_REQUEST, JSONObject().put("error", it.message ?: "Kanal nicht gefunden"))
                }
                return ok().put("channel", channel.toJson()).let { json(Response.Status.OK, it) }
            }
            "/api/youtube/import" -> {
                // Google Takeout subscriptions file (upload) or pasted text.
                val text = body["file"]?.let { File(it).readText() } ?: param("text") ?: return badRequest("file")
                val channels = YouTubeAccount.parseTakeout(text)
                if (channels.isEmpty()) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Keine Kanäle in der Datei gefunden"))
                val added = app.youtube.importChannels(channels)
                return json(Response.Status.OK, ok().put("found", channels.size).put("added", added))
            }
            "/api/youtube/account" -> {
                // Google client for the YouTube login, typed comfortably on the phone or Mac.
                param("client_id")?.let { app.youtube.account.clientId = it }
                param("client_secret")?.let { app.youtube.account.clientSecret = it }
                if (param("open") == "true") dispatch(Command.OpenYouTubeLogin)
                return json(Response.Status.OK, ok().put("configured", app.youtube.account.isConfigured))
            }
            "/api/youtube/unsubscribe" -> {
                app.youtube.unsubscribe(param("id") ?: return badRequest("id"))
                return json(Response.Status.OK, ok())
            }
            "/api/key" -> {
                val key = param("code")?.lowercase() ?: return badRequest("code")
                if (key !in RemoteControl.Keys.all) return badRequest("code")
                if (!RemoteControl.foreground) return json(Response.Status.CONFLICT, JSONObject().put("error", "$HUB_NAME ist auf dem TV nicht im Vordergrund"))
                RemoteControl.keys.tryEmit(key)
                return json(Response.Status.OK, ok())
            }
            "/api/screen/offer" -> {
                val payload = runCatching { JSONObject(body["postData"].orEmpty()) }.getOrNull() ?: return badRequest("sdp")
                val sdp = payload.optString("sdp").ifEmpty { return badRequest("sdp") }
                val share = ScreenShare.start(sdp, payload.optString("kind", "screen"), payload.optString("from", "Gerät"))
                dispatch(Command.ShowScreenShare(share.id))
                return json(Response.Status.OK, ok().put("id", share.id))
            }
            "/api/screen/answer" -> {
                val id = param("id") ?: return badRequest("id")
                val sdp = runCatching { JSONObject(body["postData"].orEmpty()).getString("sdp") }.getOrNull() ?: return badRequest("sdp")
                ScreenShare.answer(id, sdp, localAddresses().firstOrNull())
                return json(Response.Status.OK, ok())
            }
            "/api/screen/stop" -> {
                val id = param("id") ?: return badRequest("id")
                ScreenShare.end(id)
                RemoteControl.send(Command.CloseScreenShare(id))
                return json(Response.Status.OK, ok())
            }
            "/api/media" -> {
                val temp = body["file"]?.let(::File) ?: return badRequest("file")
                val name = param("file") ?: param("name") ?: "upload.jpg"
                if (!MediaInbox.isSupported(name)) return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Nur Fotos und Videos"))
                val item = MediaInbox.add(context, temp, name)
                if (param("show") != "false") dispatch(Command.ShowMedia(item.id))
                return json(Response.Status.OK, ok().put("id", item.id))
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
                DeepLinks.parseText(target)
                    ?: if (Regex("^[A-Za-z0-9_]{2,25}$").matches(target)) Command.OpenLive(target.lowercase())
                    else return json(Response.Status.BAD_REQUEST, JSONObject().put("error", "Diesen Link kennt $HUB_NAME nicht"))
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
        dispatch(command)
        return json(Response.Status.OK, ok())
    }

    private fun handleGet(path: String, param: (String) -> String?): Response = when (path) {
        "/api/state" -> json(Response.Status.OK, RemoteControl.stateJson(BuildConfig.VERSION_NAME, app.enabledApps())
            .put("name", HUB_NAME)
            .put("https_port", HTTPS_PORT))
        "/api/log" -> newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8",
            "Couch ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
                AppLog.text().ifEmpty { "Keine Fehler seit dem Start." })
        "/api/twitch/live" -> json(Response.Status.OK, JSONObject().put("channels", JSONArray(app.twitch.followed.value.items().map(RemoteControl::streamJson))))
        "/api/twitch/top" -> json(Response.Status.OK, JSONObject().put("channels", JSONArray(app.twitch.top.value.items().map(RemoteControl::streamJson))))
        "/api/twitch/search" -> {
            val q = param("q") ?: return badRequest("q")
            val result = runCatching {
                try {
                    app.helix.searchChannels(q)
                } catch (e: NotLoggedInException) {
                    listOfNotNull(app.gql.user(q)?.let { StreamInfo(it.id, it.login, it.displayName, avatarUrl = it.avatarUrl, isLive = it.isLive) })
                }
            }.getOrElse { return json(Response.Status.SERVICE_UNAVAILABLE, JSONObject().put("error", it.message ?: "Twitch nicht erreichbar")) }
            json(Response.Status.OK, JSONObject().put("channels", JSONArray(result.map { RemoteControl.streamJson(it).put("live", it.isLive) })))
        }
        "/api/youtube/feed" -> json(Response.Status.OK, JSONObject().put("videos", JSONArray(app.youtube.feed.value.items().take(60).map(RemoteControl::videoJson))))
        "/api/youtube/shelves" -> json(Response.Status.OK, JSONObject().put("shelves", JSONArray(YouTubeClient.shelves.map { shelf ->
            JSONObject().put("id", shelf.id).put("title", shelf.title)
                .put("videos", JSONArray(app.youtube.shelves[shelf.id]?.value?.items().orEmpty().take(24).map(RemoteControl::videoJson)))
        })))
        "/api/youtube/subscriptions" -> json(Response.Status.OK, JSONObject().put("channels", JSONArray(app.youtube.subscriptions.value.map { it.toJson() })))
        "/api/youtube/search" -> {
            val q = param("q") ?: return badRequest("q")
            YouTubeClient.init(settings.youtubeRegion)
            val result = runCatching { YouTubeClient.search(q) }.getOrElse {
                return json(Response.Status.SERVICE_UNAVAILABLE, JSONObject().put("error", it.message ?: "YouTube nicht erreichbar"))
            }
            val subscribed = app.youtube.subscriptions.value.map { it.id }.toSet()
            json(Response.Status.OK, JSONObject()
                .put("videos", JSONArray(result.videos.map(RemoteControl::videoJson)))
                .put("channels", JSONArray(result.channels.map { it.toJson().put("subscribers", it.subscribers).put("subscribed", it.id in subscribed) })))
        }
        "/api/history" -> json(Response.Status.OK, JSONObject().put("items", JSONArray(historyJson())))
        "/api/screen/session" -> {
            val share = ScreenShare.get(param("id")) ?: return json(Response.Status.NOT_FOUND, JSONObject().put("error", "Keine Freigabe"))
            json(Response.Status.OK, JSONObject().put("id", share.id).put("offer", share.offer).put("kind", share.kind).put("from", share.from).put("ended", share.ended))
        }
        "/api/screen/answer" -> {
            val share = ScreenShare.get(param("id")) ?: return json(Response.Status.OK, JSONObject().put("ended", true))
            json(Response.Status.OK, JSONObject().put("answer", share.answer ?: JSONObject.NULL).put("ended", share.ended))
        }
        "/api/youtube/account" -> json(Response.Status.OK, JSONObject()
            .put("configured", app.youtube.account.isConfigured)
            .put("logged_in", app.youtube.account.isLoggedIn)
            .put("name", app.youtube.account.accountName ?: JSONObject.NULL)
            .put("client_id", app.youtube.account.clientId))
        "/api/media" -> json(Response.Status.OK, JSONObject().put("items", JSONArray(MediaInbox.all().map {
            JSONObject().put("id", it.id).put("name", it.name).put("video", it.isVideo)
        })))
        else -> json(Response.Status.METHOD_NOT_ALLOWED, JSONObject().put("error", "use POST"))
    }

    /** Twitch VODs and YouTube videos started earlier, newest first. */
    private fun historyJson(): List<JSONObject> {
        val twitch = app.history.continueWatching(15).map {
            JSONObject().put("app", "twitch").put("id", it.video.id).put("title", it.video.title).put("channel", it.video.userName)
                .put("thumbnail", it.video.thumbnail(640, 360)).put("progress", it.progress.toDouble()).put("duration", it.video.durationSec)
                .put("updated", it.updatedAt)
        }
        val youtube = app.youtube.history.continueWatching(15).map {
            JSONObject().put("app", "youtube").put("id", it.video.id).put("title", it.video.title).put("channel", it.video.channelName)
                .put("thumbnail", it.video.thumbnail).put("progress", it.progress.toDouble()).put("duration", it.video.durationSec)
                .put("position", it.positionMs / 1000).put("updated", it.updatedAt)
        }
        return (twitch + youtube).sortedByDescending { it.optLong("updated") }
    }

    /** Sends a command to the UI, or starts Couch first when it is in the background. */
    private fun dispatch(command: Command) {
        if (command.opensScreen && !RemoteControl.foreground) launch(DeepLinks.uriFor(command)) else RemoteControl.send(command)
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

    private fun ok() = JSONObject().put("ok", true)

    private fun badRequest(param: String) =
        json(Response.Status.BAD_REQUEST, JSONObject().put("error", "missing or invalid parameter '$param'"))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
