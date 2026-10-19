package dev.purpletv.youtube.api

import android.content.Context
import dev.purpletv.BuildConfig
import dev.purpletv.core.AppLog
import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Google login for YouTube through the OAuth device flow (code on the TV, confirm on the phone),
 * used to take over the account's subscriptions. Google only allows this with a client of type
 * "TVs and Limited Input devices" from the user's own Google Cloud project, so the client id and
 * secret are configured once (README, or built in through the GitHub variables).
 */
class YouTubeAccount(context: Context) {

    private val prefs = context.getSharedPreferences("youtube_account", Context.MODE_PRIVATE)

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresInSec: Int)

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Success : PollResult
        data class Failed(val message: String) : PollResult
    }

    var clientId: String
        get() = prefs.getString("client_id", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.YOUTUBE_CLIENT_ID
        set(value) = prefs.edit().putString("client_id", value.trim()).apply()
    var clientSecret: String
        get() = prefs.getString("client_secret", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.YOUTUBE_CLIENT_SECRET
        set(value) = prefs.edit().putString("client_secret", value.trim()).apply()

    val isConfigured get() = clientId.isNotBlank() && clientSecret.isNotBlank()
    val isLoggedIn get() = prefs.getString("refresh_token", null) != null
    val accountName: String? get() = prefs.getString("name", null)
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        private set(value) = prefs.edit().putLong("last_sync", value).apply()

    private val scope = "https://www.googleapis.com/auth/youtube.readonly"

    fun startDeviceFlow(): DeviceCode {
        val json = JSONObject(Http.postForm("https://oauth2.googleapis.com/device/code", mapOf("client_id" to clientId, "scope" to scope)))
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.optString("verification_url", "https://www.google.com/device"),
            intervalSec = json.optInt("interval", 5),
            expiresInSec = json.optInt("expires_in", 1800),
        )
    }

    fun poll(code: DeviceCode): PollResult = try {
        val json = JSONObject(Http.postForm("https://oauth2.googleapis.com/token", mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "device_code" to code.deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        )))
        saveTokens(json)
        runCatching { loadName() }
        AppLog.i("YouTube", "Google-Konto verbunden")
        PollResult.Success
    } catch (e: HttpStatusException) {
        when (val error = errorOf(e)) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "access_denied" -> PollResult.Failed("Anmeldung abgelehnt")
            "expired_token" -> PollResult.Failed("Code abgelaufen")
            else -> PollResult.Failed("Google: ${error.ifBlank { "Fehler ${e.code}" }}")
        }
    }

    private fun saveTokens(json: JSONObject) {
        prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putLong("expires_at", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000 - 60_000)
            .apply { json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { putString("refresh_token", it) } }
            .commit()
    }

    /** A valid access token, refreshed when needed. Null when logged out. */
    @Synchronized
    private fun token(): String? {
        val refresh = prefs.getString("refresh_token", null) ?: return null
        val current = prefs.getString("access_token", null)
        if (current != null && System.currentTimeMillis() < prefs.getLong("expires_at", 0)) return current
        return try {
            saveTokens(JSONObject(Http.postForm("https://oauth2.googleapis.com/token", mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "refresh_token" to refresh,
                "grant_type" to "refresh_token",
            ))))
            prefs.getString("access_token", null)
        } catch (e: HttpStatusException) {
            AppLog.e("YouTube", "Google-Token konnte nicht erneuert werden: ${errorOf(e)}")
            if (errorOf(e) == "invalid_grant") logout()
            null
        }
    }

    private fun api(path: String): JSONObject {
        val token = token() ?: throw IllegalStateException("Nicht bei Google angemeldet")
        return Http.getJson("https://www.googleapis.com/youtube/v3/$path", mapOf("Authorization" to "Bearer $token"))
    }

    private fun loadName() {
        val item = api("channels?part=snippet&mine=true").optJSONArray("items")?.optJSONObject(0) ?: return
        prefs.edit().putString("name", item.optJSONObject("snippet")?.optString("title")).apply()
    }

    /** All channels the account is subscribed to. */
    fun subscriptions(): List<YtChannel> {
        val out = mutableListOf<YtChannel>()
        var page: String? = null
        do {
            val json = api("subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical" +
                (page?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: ""))
            val items = json.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val snippet = items!!.getJSONObject(i).optJSONObject("snippet") ?: continue
                val id = snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty()
                if (id.isEmpty()) continue
                val thumbs = snippet.optJSONObject("thumbnails")
                val avatar = (thumbs?.optJSONObject("medium") ?: thumbs?.optJSONObject("default"))?.optString("url").orEmpty()
                out += YtChannel(id, "https://www.youtube.com/channel/$id", snippet.optString("title"), avatar)
            }
            page = json.optString("nextPageToken").ifBlank { null }
        } while (page != null && out.size < 2000)
        lastSync = System.currentTimeMillis()
        return out
    }

    fun logout() {
        prefs.edit().remove("access_token").remove("refresh_token").remove("expires_at").remove("name").commit()
    }

    private fun errorOf(e: HttpStatusException) = runCatching { JSONObject(e.body).optString("error") }.getOrDefault("")

    companion object {
        /**
         * Channels from a Google Takeout export (YouTube → subscriptions.csv, in German Takeout
         * "Abos.csv"): one line per channel with its id, url and title.
         */
        fun parseTakeout(csv: String): List<YtChannel> = csv.lineSequence().mapNotNull { line ->
            val id = Regex("UC[0-9A-Za-z_-]{22}").find(line)?.value ?: return@mapNotNull null
            val cols = line.split(',')
            val title = cols.drop(2).joinToString(",").trim().trim('"').ifBlank { id }
            YtChannel(id, "https://www.youtube.com/channel/$id", title, "")
        }.distinctBy { it.id }.toList()
    }
}
