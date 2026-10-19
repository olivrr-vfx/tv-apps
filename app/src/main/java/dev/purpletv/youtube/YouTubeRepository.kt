package dev.purpletv.youtube

import dev.purpletv.core.AppLog
import android.content.Context
import dev.purpletv.core.Load
import dev.purpletv.data.Settings
import dev.purpletv.remote.RemoteControl
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.YtChannel
import dev.purpletv.youtube.api.YtVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide YouTube data. There is no Google login: subscriptions are kept on the TV and the feed
 * is built from each channel's public RSS feed.
 */
class YouTubeRepository(context: Context, private val settings: Settings) {
    companion object {
        const val FEED_INTERVAL_MS = 15 * 60_000L
        private const val CHANNEL_CACHE_MS = 10 * 60_000L
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000, 20_000, 40_000)
    }

    private val prefs = context.getSharedPreferences("youtube", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val history = YouTubeHistory(context)

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<List<YtChannel>> = _subscriptions

    private val _feed = MutableStateFlow<Load<List<YtVideo>>>(Load.Loading)
    val feed: StateFlow<Load<List<YtVideo>>> = _feed

    /** Trending shelves by kiosk id. */
    val shelves = YouTubeClient.shelves.associate { it.id to MutableStateFlow<Load<List<YtVideo>>>(Load.Loading) }

    private val channelCache = ConcurrentHashMap<String, Pair<Long, List<YtVideo>>>()
    private var started = false
    private var lastFeedRefresh = 0L
    private var loop: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) { YouTubeClient.init(settings.youtubeRegion) }
        refreshFeed()
        refreshShelves()
    }

    fun onForeground() {
        if (!started) start() else if (System.currentTimeMillis() - lastFeedRefresh > FEED_INTERVAL_MS) refreshFeed(silent = true)
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                delay(FEED_INTERVAL_MS)
                refreshFeed(silent = true)
            }
        }
    }

    fun onBackground() {
        loop?.cancel()
        loop = null
    }

    // --- Subscriptions ---------------------------------------------------------------------

    private fun loadSubscriptions(): List<YtChannel> = runCatching {
        val arr = JSONArray(prefs.getString("subscriptions", "[]"))
        (0 until arr.length()).map { YtChannel.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveSubscriptions(list: List<YtChannel>) {
        _subscriptions.value = list
        prefs.edit().putString("subscriptions", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun isSubscribed(channelId: String) = _subscriptions.value.any { it.id == channelId }

    fun subscribe(channel: YtChannel) {
        if (isSubscribed(channel.id)) return
        saveSubscriptions(_subscriptions.value + channel.copy(description = "", banner = ""))
        refreshFeed(silent = true)
    }

    fun unsubscribe(channelId: String) {
        saveSubscriptions(_subscriptions.value.filterNot { it.id == channelId })
        channelCache.remove(channelId)
        val current = (_feed.value as? Load.Ok)?.data ?: return
        _feed.value = Load.Ok(current.filterNot { v -> _subscriptions.value.none { it.url == v.channelUrl || it.name == v.channelName } })
    }

    /** Subscribe by link, @handle or channel id (phone remote, settings). */
    suspend fun subscribeByInput(input: String): YtChannel {
        val channel = withContext(Dispatchers.IO) {
            YouTubeClient.init(settings.youtubeRegion)
            YouTubeClient.channelInfo(input)
        }
        subscribe(channel)
        return channel
    }

    // --- Feed ------------------------------------------------------------------------------

    fun refreshFeed(silent: Boolean = false) {
        lastFeedRefresh = System.currentTimeMillis()
        val subs = _subscriptions.value
        if (subs.isEmpty()) {
            _feed.value = Load.Ok(emptyList())
            RemoteControl.youtubeFeed = emptyList()
            return
        }
        if (!silent || _feed.value !is Load.Ok) _feed.value = Load.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                YouTubeClient.init(settings.youtubeRegion)
                val limit = Semaphore(6)
                val now = System.currentTimeMillis()
                coroutineScope {
                    subs.map { ch ->
                        async {
                            channelCache[ch.id]?.takeIf { now - it.first < CHANNEL_CACHE_MS }?.second
                                ?: limit.withPermit {
                                    runCatching { YouTubeClient.latestUploads(ch) }
                                        .onFailure { AppLog.e("YouTube", "Feed ${ch.name}", it) }.getOrNull()
                                }
                                    ?.also { channelCache[ch.id] = now to it }
                        }
                    }.awaitAll()
                }
            }
            val failedAll = result.all { it == null }
            val videos = result.filterNotNull().flatten()
                .filter { !(settings.youtubeHideShorts && it.isShort) }
                .distinctBy { it.id }
                .sortedByDescending { it.uploadedMillis }
                .take(120)
            if (failedAll && silent && _feed.value is Load.Ok) return@launch
            _feed.value = if (failedAll) Load.Error("YouTube ist gerade nicht erreichbar.") else Load.Ok(videos)
            if (failedAll && !silent) {
                delay(RETRY_DELAYS_MS[1])
                if (_feed.value is Load.Error) refreshFeed(silent = true)
            }
            if (!failedAll) RemoteControl.youtubeFeed = videos
        }
    }

    fun refreshShelves() {
        shelves.keys.forEach { refreshShelf(it) }
    }

    private fun refreshShelf(id: String, attempt: Int = 0) {
        val flow = shelves[id] ?: return
        if (flow.value !is Load.Ok) flow.value = Load.Loading
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    YouTubeClient.init(settings.youtubeRegion)
                    YouTubeClient.shelf(id).filter { !(settings.youtubeHideShorts && it.isShort) }
                }
            }
            flow.value = r.fold({ Load.Ok(it) }, {
                AppLog.e("YouTube", "Regal $id", it)
                Load.Error(it.message ?: "Fehler")
            })
            // E.g. the TV woke up before its Wi-Fi: try again a few times.
            if (r.isFailure && attempt < RETRY_DELAYS_MS.size) {
                delay(RETRY_DELAYS_MS[attempt])
                if (flow.value is Load.Error) refreshShelf(id, attempt + 1)
            }
        }
    }

    /** The network came back: load again whatever failed. */
    fun retryFailed() {
        if (!started) return
        shelves.forEach { (id, flow) -> if (flow.value is Load.Error) refreshShelf(id) }
        if (_feed.value is Load.Error) refreshFeed()
    }
}

/** How far each YouTube video was watched, for resuming and "Weiter schauen". */
class YouTubeHistory(context: Context) {
    private val prefs = context.getSharedPreferences("yt_history", Context.MODE_PRIVATE)

    data class Entry(val video: YtVideo, val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
        val progress get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    fun position(videoId: String): Long? = entry(videoId)?.takeIf { it.progress < 0.95f }?.positionMs

    fun save(video: YtVideo, positionMs: Long, durationMs: Long) {
        if (video.isLive || positionMs < 20_000) return
        val json = JSONObject().put("video", video.toJson()).put("pos", positionMs).put("dur", durationMs).put("ts", System.currentTimeMillis())
        prefs.edit().putString(video.id, json.toString()).apply()
        prune()
    }

    fun continueWatching(limit: Int = 20): List<Entry> =
        prefs.all.keys.mapNotNull { entry(it) }
            .filter { it.progress in 0.01f..0.95f }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    private fun entry(id: String): Entry? = runCatching {
        val j = JSONObject(prefs.getString(id, null) ?: return null)
        Entry(YtVideo.fromJson(j.getJSONObject("video")), j.optLong("pos"), j.optLong("dur"), j.optLong("ts"))
    }.getOrNull()

    private fun prune() {
        val all = prefs.all.keys.mapNotNull { entry(it) }
        if (all.size <= 150) return
        val editor = prefs.edit()
        all.sortedByDescending { it.updatedAt }.drop(150).forEach { editor.remove(it.video.id) }
        editor.apply()
    }
}
