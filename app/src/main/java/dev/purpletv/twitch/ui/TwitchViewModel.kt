package dev.purpletv.twitch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.purpletv.PurpleApp
import dev.purpletv.core.Load
import dev.purpletv.data.WatchHistory
import dev.purpletv.design.FocusMemory
import dev.purpletv.twitch.api.ChannelInfo
import dev.purpletv.twitch.api.Game
import dev.purpletv.twitch.api.NotLoggedInException
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

enum class TwitchTab(val label: String) {
    START("Start"),
    FOLLOWED("Live"),
    TOP("Entdecken"),
    GAMES("Kategorien"),
    SEARCH("Suche"),
}

data class ChannelPage(
    val info: ChannelInfo?,
    val live: StreamInfo?,
    val videos: List<VideoInfo>,
    val highlights: List<VideoInfo>,
)

/** Screen state of the Twitch app. The shared lists live in [dev.purpletv.twitch.TwitchRepository]. */
class TwitchViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PurpleApp
    val repo = app.twitch

    val tab = MutableStateFlow(if (app.auth.isLoggedIn) TwitchTab.START else TwitchTab.SEARCH)

    private val _gameStreams = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val gameStreams: StateFlow<Load<List<StreamInfo>>> = _gameStreams
    private val _search = MutableStateFlow<Load<List<StreamInfo>>?>(null)
    val search: StateFlow<Load<List<StreamInfo>>?> = _search
    private val _channelPage = MutableStateFlow<Load<ChannelPage>>(Load.Loading)
    val channelPage: StateFlow<Load<ChannelPage>> = _channelPage
    private val _continueWatching = MutableStateFlow<List<WatchHistory.Entry>>(emptyList())
    val continueWatching: StateFlow<List<WatchHistory.Entry>> = _continueWatching

    val focusMemory = FocusMemory()

    init {
        repo.start()
        refreshHistory()
    }

    fun refreshHistory() {
        _continueWatching.value = app.history.continueWatching()
    }

    private fun <T> load(target: MutableStateFlow<Load<T>>, block: () -> T) {
        target.value = Load.Loading
        viewModelScope.launch {
            target.value = withContext(Dispatchers.IO) {
                try {
                    Load.Ok(block())
                } catch (e: NotLoggedInException) {
                    Load.NeedLogin
                } catch (e: Exception) {
                    Load.Error(e.message ?: "Fehler")
                }
            }
        }
    }

    fun openGame(game: Game) = load(_gameStreams) { app.helix.topStreams(gameId = game.id) }

    fun openChannel(login: String) = load(_channelPage) {
        val info = app.helix.channel(login)
        val id = info?.id
        kotlinx.coroutines.runBlocking {
            supervisorScope {
                val live = async { runCatching { app.helix.stream(login) }.getOrNull() }
                val videos = async { if (id == null) emptyList() else app.helix.videos(id, "archive") }
                val highlights = async { if (id == null) emptyList() else runCatching { app.helix.videos(id, "highlight") }.getOrDefault(emptyList()) }
                ChannelPage(info, live.await(), videos.await(), highlights.await())
            }
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _search.value = Load.Loading
        viewModelScope.launch {
            _search.value = withContext(Dispatchers.IO) {
                try {
                    Load.Ok(app.helix.searchChannels(q))
                } catch (e: NotLoggedInException) {
                    // Without login we can still open a channel by its exact name.
                    val user = runCatching { app.gql.user(q) }.getOrNull()
                    if (user != null) {
                        Load.Ok(listOf(StreamInfo(user.id, user.login, user.displayName, avatarUrl = user.avatarUrl, isLive = user.isLive)))
                    } else {
                        Load.Error("Kanal \"$q\" nicht gefunden")
                    }
                } catch (e: Exception) {
                    Load.Error(e.message ?: "Fehler")
                }
            }
        }
    }
}
