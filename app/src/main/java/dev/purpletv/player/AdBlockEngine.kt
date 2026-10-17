package dev.purpletv.player

import android.net.Uri
import android.util.Log
import dev.purpletv.data.Http
import dev.purpletv.twitch.TwitchGql
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AdStatus {
    /** No ad in the stream. */
    NONE,
    /** Ad detected, an ad-free backup stream is shown instead. */
    BLOCKED_BACKUP,
    /** Ad detected, ad segments are removed; picture freezes until the ad is over. */
    BLOCKED_STRIP,
    /** Ad could not be removed (or ad blocking is disabled). */
    SHOWING,
}

/**
 * Sits between ExoPlayer and the network for every playlist request. Watches the Twitch media
 * playlists for stitched ads and replaces them.
 *
 * Called from ExoPlayer's loader threads, so everything blocking is fine here.
 */
class AdBlockEngine(
    private val gql: TwitchGql,
    private val adBlockEnabled: () -> Boolean,
    private val lowLatency: () -> Boolean,
) {
    private val _status = MutableStateFlow(AdStatus.NONE)
    val status: StateFlow<AdStatus> = _status

    private class Backup(val kind: TwitchGql.PlayerKind, val variants: List<PlaylistProcessor.Variant>, val fetchedAt: Long)

    @Volatile private var login: String? = null
    @Volatile private var mainVariants: List<PlaylistProcessor.Variant> = emptyList()
    private val backups = mutableMapOf<TwitchGql.PlayerKind, Backup>()
    private var lastGoodKind: TwitchGql.PlayerKind? = null
    private var backupBlockedUntil = 0L

    /** Variants of the main stream, available once the master playlist was loaded. */
    val variants get() = mainVariants

    @Synchronized
    fun reset(login: String) {
        this.login = login
        mainVariants = emptyList()
        backups.clear()
        backupBlockedUntil = 0
        _status.value = AdStatus.NONE
    }

    /** Returns replacement bytes for [uri], or null to let the request pass through untouched. */
    fun intercept(uri: Uri): ByteArray? {
        val host = uri.host ?: return null
        if (!host.endsWith("ttvnw.net") || uri.path?.endsWith(".m3u8") != true) return null
        val url = uri.toString()
        val text = Http.get(url)
        if (host.startsWith("usher")) {
            mainVariants = PlaylistProcessor.parseMaster(text)
            return text.toByteArray()
        }
        return processMedia(url, text).toByteArray()
    }

    private fun processMedia(url: String, text: String): String {
        val result = PlaylistProcessor.process(text, lowLatency())
        if (!result.hadAds) {
            _status.value = AdStatus.NONE
            return result.playlist
        }
        if (!adBlockEnabled()) {
            _status.value = AdStatus.SHOWING
            return text
        }
        val wanted = mainVariants.firstOrNull { it.uri == url }
        val backup = synchronized(this) { backupPlaylist(wanted, url) }
        if (backup != null) {
            _status.value = AdStatus.BLOCKED_BACKUP
            return backup
        }
        _status.value = if (result.allAds) AdStatus.SHOWING else AdStatus.BLOCKED_STRIP
        return result.playlist
    }

    private fun backupPlaylist(wanted: PlaylistProcessor.Variant?, baseUrl: String): String? {
        val now = System.currentTimeMillis()
        if (now < backupBlockedUntil) return null
        val login = login ?: return null
        val kinds = listOfNotNull(lastGoodKind) + TwitchGql.BACKUPS.filter { it != lastGoodKind }
        for (kind in kinds) {
            try {
                var backup = backups[kind]
                if (backup == null || now - backup.fetchedAt > 10 * 60_000) {
                    val token = gql.playbackToken(login, kind) ?: continue
                    val master = Http.get(gql.usherUrl(login, token, lowLatency()))
                    backup = Backup(kind, PlaylistProcessor.parseMaster(master), now)
                    backups[kind] = backup
                }
                val variant = PlaylistProcessor.pickMatching(backup.variants, wanted) ?: continue
                val media = Http.get(variant.uri)
                val processed = PlaylistProcessor.process(media, lowLatency = false, baseUrl = variant.uri)
                if (!processed.hadAds) {
                    lastGoodKind = kind
                    return processed.playlist
                }
            } catch (e: Exception) {
                Log.w("AdBlock", "backup ${kind.playerType} failed", e)
                backups.remove(kind)
            }
        }
        // Every backup had ads as well; don't hammer the API on each playlist refresh.
        backupBlockedUntil = now + 8_000
        Log.i("AdBlock", "no ad-free backup for $baseUrl")
        return null
    }
}
