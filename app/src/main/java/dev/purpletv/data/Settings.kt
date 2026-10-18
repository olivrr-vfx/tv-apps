package dev.purpletv.data

import android.content.Context
import dev.purpletv.BuildConfig
import java.util.UUID

enum class ChatMode(val label: String) {
    OFF("Aus"),
    SIDE("Seitenleiste"),
    OVERLAY("Overlay");

    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class DefaultQuality(val label: String, val maxHeight: Int?) {
    AUTO("Auto", null),
    SOURCE("Quelle", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360);

    fun next() = entries[(ordinal + 1) % entries.size]
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("purpletv", Context.MODE_PRIVATE)

    private fun string(key: String) = prefs.getString(key, null)?.takeIf { it.isNotBlank() }
    private fun put(key: String, value: String?) = prefs.edit().putString(key, value).apply()
    private fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    /** Client-ID of the user's own app registered on dev.twitch.tv (used for login + Helix). */
    var clientId: String
        get() = string("client_id") ?: BuildConfig.TWITCH_CLIENT_ID
        set(value) = put("client_id", value.trim())

    var accessToken: String?
        get() = string("access_token")
        private set(value) = put("access_token", value)
    var refreshToken: String?
        get() = string("refresh_token")
        private set(value) = put("refresh_token", value)

    /**
     * Refresh tokens are single-use, so both tokens must reach the disk together and right away.
     * With apply() a TV that is switched off at the wrong moment would keep the already-used
     * refresh token and the login would be lost.
     */
    fun saveTokens(access: String, refresh: String?) {
        prefs.edit()
            .putString("access_token", access)
            .apply { if (refresh != null) putString("refresh_token", refresh) }
            .commit()
    }

    /** Last login/refresh problem, shown in the settings to make login issues debuggable. */
    var lastAuthError: String?
        get() = string("last_auth_error")
        set(value) = put("last_auth_error", value)
    var userId: String?
        get() = string("user_id")
        set(value) = put("user_id", value)
    var userLogin: String?
        get() = string("user_login")
        set(value) = put("user_login", value)
    var userName: String?
        get() = string("user_name")
        set(value) = put("user_name", value)

    var adBlock: Boolean
        get() = prefs.getBoolean("ad_block", true)
        set(value) = putBool("ad_block", value)
    var muteAds: Boolean
        get() = prefs.getBoolean("mute_ads", true)
        set(value) = putBool("mute_ads", value)
    var lowLatency: Boolean
        get() = prefs.getBoolean("low_latency", true)
        set(value) = putBool("low_latency", value)
    var thirdPartyEmotes: Boolean
        get() = prefs.getBoolean("third_party_emotes", true)
        set(value) = putBool("third_party_emotes", value)
    var autoUpdate: Boolean
        get() = prefs.getBoolean("auto_update", true)
        set(value) = putBool("auto_update", value)
    var livePreview: Boolean
        get() = prefs.getBoolean("live_preview", true)
        set(value) = putBool("live_preview", value)
    var showStats: Boolean
        get() = prefs.getBoolean("show_stats", false)
        set(value) = putBool("show_stats", value)

    var chatMode: ChatMode
        get() = runCatching { ChatMode.valueOf(string("chat_mode")!!) }.getOrDefault(ChatMode.SIDE)
        set(value) = put("chat_mode", value.name)
    var chatFontSize: Int
        get() = prefs.getInt("chat_font", 13)
        set(value) = prefs.edit().putInt("chat_font", value).apply()
    var defaultQuality: DefaultQuality
        get() = runCatching { DefaultQuality.valueOf(string("quality")!!) }.getOrDefault(DefaultQuality.AUTO)
        set(value) = put("quality", value.name)

    val deviceId: String
        get() = string("device_id") ?: UUID.randomUUID().toString().replace("-", "").also { put("device_id", it) }

    fun clearAuth() {
        prefs.edit()
            .remove("access_token").remove("refresh_token")
            .remove("user_id").remove("user_login").remove("user_name")
            .commit()
    }
}
