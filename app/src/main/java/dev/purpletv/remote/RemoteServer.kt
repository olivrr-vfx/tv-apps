package dev.purpletv.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dev.purpletv.BuildConfig
import dev.purpletv.data.Settings
import dev.purpletv.ui.MainActivity
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Small HTTP API on the local network so Home Assistant can see what's playing and control it.
 * Every call except /api/info needs the pairing code shown in the settings
 * (header `Authorization: Bearer <code>` or `?token=<code>`).
 */
class RemoteServer(private val context: Context, private val settings: Settings) : NanoHTTPD(PORT) {

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
    }

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
        if (session.method == Method.POST) runCatching { session.parseBody(HashMap()) }
        handle(session)
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "error"))
    }

    private fun handle(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/')
        if (path == "/api/info") {
            return json(Response.Status.OK, JSONObject().put("name", "PurpleTV").put("version", BuildConfig.VERSION_NAME))
        }
        val params = session.parameters
        fun param(name: String) = params[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val auth = session.headers["authorization"]?.removePrefix("Bearer")?.trim() ?: param("token")
        if (auth != settings.remoteCode) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "invalid code"))
        }
        if (path == "/api/state") return json(Response.Status.OK, RemoteControl.stateJson(BuildConfig.VERSION_NAME))
        if (session.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, JSONObject().put("error", "use POST"))

        val command: RemoteControl.Command = when (path) {
            "/api/play" -> RemoteControl.Command.Play
            "/api/pause" -> RemoteControl.Command.Pause
            "/api/toggle" -> RemoteControl.Command.Toggle
            "/api/next" -> RemoteControl.Command.Next
            "/api/previous" -> RemoteControl.Command.Previous
            "/api/home" -> RemoteControl.Command.Home
            "/api/mute" -> RemoteControl.Command.Mute(param("mute")?.toBooleanStrictOrNull() ?: !RemoteControl.muted)
            "/api/volume" -> RemoteControl.Command.Volume(param("level")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: return badRequest("level"))
            "/api/open" -> RemoteControl.Command.OpenLive(param("login")?.lowercase() ?: return badRequest("login"))
            "/api/channel" -> RemoteControl.Command.OpenChannel(param("login")?.lowercase() ?: return badRequest("login"))
            "/api/vod" -> RemoteControl.Command.OpenVod(param("id") ?: return badRequest("id"))
            else -> return json(Response.Status.NOT_FOUND, JSONObject().put("error", "unknown endpoint"))
        }

        val opens = command is RemoteControl.Command.OpenLive || command is RemoteControl.Command.OpenChannel || command is RemoteControl.Command.OpenVod
        if (opens && !RemoteControl.foreground) {
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
        json(Response.Status.BAD_REQUEST, JSONObject().put("error", "missing parameter '$param'"))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
