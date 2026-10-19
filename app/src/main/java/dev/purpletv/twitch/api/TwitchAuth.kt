package dev.purpletv.twitch.api

import dev.purpletv.core.AppLog
import dev.purpletv.data.Http
import dev.purpletv.data.HttpStatusException
import dev.purpletv.data.Settings
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OAuth "Device Code Grant" flow: the TV shows a code, the user confirms it on their phone.
 * By default it logs in through Twitch's own TV client (twitch.tv/activate, nothing to set up).
 * Optionally the user's own app from dev.twitch.tv (public, or confidential with its secret).
 *
 * Access tokens live about 4 hours, the refresh token rotates on every refresh and can only be
 * used once. Two refreshes racing with the same refresh token make Twitch reject the second
 * one, so refreshing is single-flight: whoever comes second just picks up the new token.
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

    companion object {
        /** Twitch's Android TV app; allows the device login without registering an app. */
        const val TV_CLIENT_ID = "ue6666qo983tsx6so1t0vnawi233wa"
    }

    private val scopes = "user:read:follows"
    private val lock = Any()

    val isLoggedIn get() = settings.accessToken != null

    /** Client for a new login. */
    private val loginClientId get() = settings.clientId.ifBlank { TV_CLIENT_ID }

    /** Client the current tokens belong to; API calls must send this one. */
    val apiClientId get() = settings.authClientId ?: TV_CLIENT_ID

    /** Logged in through Twitch's TV client: account data comes from GQL instead of Helix. */
    val usesTvClient get() = isLoggedIn && apiClientId == TV_CLIENT_ID

    fun startDeviceFlow(): DeviceCode {
        val json = JSONObject(
            Http.postForm(
                "https://id.twitch.tv/oauth2/device",
                mapOf("client_id" to loginClientId, "scopes" to scopes)
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
                buildMap {
                    put("client_id", loginClientId)
                    if (loginClientId == settings.clientId && settings.clientSecret.isNotBlank()) put("client_secret", settings.clientSecret)
                    put("scopes", scopes)
                    put("device_code", code.deviceCode)
                    put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }
            )
        )
        settings.authClientId = loginClientId
        settings.saveTokens(json.getString("access_token"), json.optString("refresh_token").ifBlank { null })
        AppLog.i("Twitch", "Angemeldet über ${if (loginClientId == TV_CLIENT_ID) "Twitch-TV-Client" else "eigene App"}, gültig ${json.optInt("expires_in")} s")
        settings.lastAuthError = null
        if (validate()) PollResult.Success else PollResult.Failed("Token ungültig")
    } catch (e: HttpStatusException) {
        when (val message = errorMessage(e)) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "expired_token", "invalid device code" -> PollResult.Failed("Code abgelaufen")
            "access_denied" -> PollResult.Failed("Anmeldung abgelehnt")
            else -> PollResult.Failed(message.ifBlank { "Fehler ${e.code}" })
        }
    }

    /**
     * Checks the stored token, fills in user id / login and refreshes ahead of time when the
     * token is about to expire. Network problems keep the login (the TV may just be offline).
     */
    fun validate(): Boolean {
        val token = settings.accessToken ?: return false
        return try {
            val json = Http.getJson("https://id.twitch.tv/oauth2/validate", mapOf("Authorization" to "OAuth $token"))
            settings.userId = json.getString("user_id")
            settings.userLogin = json.getString("login")
            if (settings.userName == null) settings.userName = json.getString("login")
            // 0 means the token does not expire.
            val expiresIn = json.optInt("expires_in", 0)
            if (expiresIn in 1 until 30 * 60) refresh(token)
            true
        } catch (e: HttpStatusException) {
            AppLog.e("Twitch", "Token-Prüfung: HTTP ${e.code} ${errorMessage(e)}")
            if (e.code == 401) refresh(token) else true
        } catch (e: IOException) {
            true
        }
    }

    /**
     * @param failedToken the access token that was rejected. If another caller already replaced
     *   it, nothing is refreshed and the caller simply retries with the new token.
     */
    fun refresh(failedToken: String?): Boolean = synchronized(lock) {
        val current = settings.accessToken
        if (failedToken != null && current != null && current != failedToken) return true
        val refresh = settings.refreshToken ?: run {
            fail("Kein Refresh-Token gespeichert", logout = true)
            return false
        }
        try {
            val json = JSONObject(
                Http.postForm(
                    "https://id.twitch.tv/oauth2/token",
                    buildMap {
                        put("client_id", apiClientId)
                        if (apiClientId == settings.clientId && settings.clientSecret.isNotBlank()) put("client_secret", settings.clientSecret)
                        put("grant_type", "refresh_token")
                        put("refresh_token", refresh)
                    }
                )
            )
            settings.saveTokens(json.getString("access_token"), json.optString("refresh_token").ifBlank { refresh })
            true
        } catch (e: HttpStatusException) {
            val message = errorMessage(e)
            // Only a definitive "this refresh token is dead" ends the session; anything else
            // (rate limits, Twitch hiccups) is retried next time.
            // "missing client secret": the client cannot refresh at all (Twitch's TV client, or an own
            // app registered as confidential without its secret). Waiting does not help either.
            val dead = (e.code == 400 || e.code == 401) &&
                (message.contains("invalid", ignoreCase = true) || message.contains("secret", ignoreCase = true))
            val text = if (message.contains("secret", ignoreCase = true)) {
                "Anmeldung abgelaufen, bitte neu anmelden"
            } else {
                "Token-Erneuerung fehlgeschlagen (${e.code}): ${message.ifBlank { "unbekannt" }}"
            }
            fail(text, logout = dead)
            false
        } catch (e: IOException) {
            fail("Token-Erneuerung: keine Verbindung (${e.message})", logout = false)
            false
        }
    }

    private fun fail(message: String, logout: Boolean) {
        AppLog.e("Twitch", message + if (logout) " → abgemeldet" else "")
        val time = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date())
        settings.lastAuthError = "$time – $message" + if (logout) " → abgemeldet" else ""
        if (logout) settings.clearAuth()
    }

    private fun errorMessage(e: HttpStatusException) =
        runCatching { JSONObject(e.body).optString("message") }.getOrDefault("")

    fun logout() = settings.clearAuth()
}
