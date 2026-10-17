package dev.purpletv.twitch

import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import org.json.JSONObject

/**
 * OAuth "Device Code Grant" flow: the TV shows a code, the user confirms it on their phone.
 * Works with a public client registered on dev.twitch.tv, no client secret needed.
 */
class TwitchAuth(private val settings: Settings) {

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSec: Int,
        val expiresInSec: Int,
    )

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Success : PollResult
        data class Failed(val message: String) : PollResult
    }

    private val scopes = "user:read:follows"

    val isLoggedIn get() = settings.accessToken != null
    val hasClientId get() = settings.clientId.isNotBlank()

    fun startDeviceFlow(): DeviceCode {
        val json = JSONObject(
            Http.postForm(
                "https://id.twitch.tv/oauth2/device",
                mapOf("client_id" to settings.clientId, "scopes" to scopes)
            )
        )
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            intervalSec = json.optInt("interval", 5),
            expiresInSec = json.optInt("expires_in", 1800),
        )
    }

    fun poll(code: DeviceCode): PollResult = try {
        val json = JSONObject(
            Http.postForm(
                "https://id.twitch.tv/oauth2/token",
                mapOf(
                    "client_id" to settings.clientId,
                    "scopes" to scopes,
                    "device_code" to code.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                )
            )
        )
        settings.accessToken = json.getString("access_token")
        settings.refreshToken = json.optString("refresh_token").ifBlank { null }
        if (validate()) PollResult.Success else PollResult.Failed("Token ungültig")
    } catch (e: HttpStatusException) {
        val message = runCatching { JSONObject(e.body).optString("message") }.getOrDefault("")
        when (message) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "expired_token", "invalid device code" -> PollResult.Failed("Code abgelaufen")
            "access_denied" -> PollResult.Failed("Anmeldung abgelehnt")
            else -> PollResult.Failed(message.ifBlank { "Fehler ${e.code}" })
        }
    }

    /** Checks the stored token and fills in user id / login. */
    fun validate(): Boolean {
        val token = settings.accessToken ?: return false
        return try {
            val json = Http.getJson("https://id.twitch.tv/oauth2/validate", mapOf("Authorization" to "OAuth $token"))
            settings.userId = json.getString("user_id")
            settings.userLogin = json.getString("login")
            if (settings.userName == null) settings.userName = json.getString("login")
            true
        } catch (e: HttpStatusException) {
            if (e.code == 401) refresh() else false
        }
    }

    /** Public clients get one-time-use refresh tokens; a new one comes with each refresh. */
    @Synchronized
    fun refresh(): Boolean {
        val refresh = settings.refreshToken ?: run { settings.clearAuth(); return false }
        return try {
            val json = JSONObject(
                Http.postForm(
                    "https://id.twitch.tv/oauth2/token",
                    mapOf(
                        "client_id" to settings.clientId,
                        "grant_type" to "refresh_token",
                        "refresh_token" to refresh,
                    )
                )
            )
            settings.accessToken = json.getString("access_token")
            settings.refreshToken = json.optString("refresh_token").ifBlank { refresh }
            true
        } catch (e: HttpStatusException) {
            if (e.code in 400..401) settings.clearAuth()
            false
        }
    }

    fun logout() = settings.clearAuth()
}
