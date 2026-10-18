package dev.purpletv.youtube.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.purpletv.PurpleApp
import dev.purpletv.core.Load
import dev.purpletv.design.FocusMemory
import dev.purpletv.youtube.YouTubeHistory
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.YtChannelPage
import dev.purpletv.youtube.api.YtSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YouTubeTab(val label: String) {
    START("Start"),
    SUBSCRIPTIONS("Abos"),
    SEARCH("Suche"),
    HISTORY("Verlauf"),
}

class YouTubeViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PurpleApp
    val repo = app.youtube

    val tab = MutableStateFlow(YouTubeTab.START)
    val query = MutableStateFlow("")

    private val _search = MutableStateFlow<Load<YtSearchResult>?>(null)
    val search: StateFlow<Load<YtSearchResult>?> = _search
    private val _channel = MutableStateFlow<Load<YtChannelPage>>(Load.Loading)
    val channel: StateFlow<Load<YtChannelPage>> = _channel
    private var channelUrl: String? = null
    private val _history = MutableStateFlow<List<YouTubeHistory.Entry>>(emptyList())
    val history: StateFlow<List<YouTubeHistory.Entry>> = _history

    val focusMemory = FocusMemory()

    init {
        repo.start()
        refreshHistory()
    }

    fun refreshHistory() {
        _history.value = repo.history.continueWatching(40)
    }

    fun search(q: String = query.value) {
        val text = q.trim()
        if (text.isEmpty()) return
        query.value = text
        _search.value = Load.Loading
        viewModelScope.launch {
            _search.value = withContext(Dispatchers.IO) {
                runCatching {
                    YouTubeClient.init(app.settings.youtubeRegion)
                    YouTubeClient.search(text)
                }.fold({ result ->
                    Load.Ok(result.copy(videos = result.videos.filter { !(app.settings.youtubeHideShorts && it.isShort) }))
                }, { Load.Error(it.message ?: "Fehler") })
            }
        }
    }

    fun openChannel(url: String, force: Boolean = false) {
        if (!force && url == channelUrl && _channel.value is Load.Ok) return
        channelUrl = url
        _channel.value = Load.Loading
        viewModelScope.launch {
            _channel.value = withContext(Dispatchers.IO) {
                runCatching {
                    YouTubeClient.init(app.settings.youtubeRegion)
                    YouTubeClient.channel(url)
                }.fold({ Load.Ok(it) }, { Load.Error(it.message ?: "Fehler") })
            }
        }
    }
}
