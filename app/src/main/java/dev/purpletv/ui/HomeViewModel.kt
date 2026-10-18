package dev.purpletv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.purpletv.PurpleApp
import dev.purpletv.data.WatchHistory
import dev.purpletv.twitch.ChannelInfo
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.NotLoggedInException
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NeedLogin : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

fun <T> Load<List<T>>.items(): List<T> = (this as? Load.Ok)?.data.orEmpty()

enum class Tab(val label: String) {
    START("Start"),
    FOLLOWED("Live"),
    TOP("Entdecken"),
    GAMES("Kategorien"),
    SEARCH("Suche"),
    SETTINGS("Einstellungen"),
}

data class ChannelPage(
    val info: ChannelInfo?,
    val live: StreamInfo?,
    val videos: List<VideoInfo>,
    val highlights: List<VideoInfo>,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PurpleApp

    val tab = MutableStateFlow(if (app.auth.isLoggedIn) Tab.START else Tab.SEARCH)

    private val _followed = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val followed: StateFlow<Load<List<StreamInfo>>> = _followed
    private val _channels = MutableStateFlow<Load<List<ChannelInfo>>>(Load.Loading)
    val channels: StateFlow<Load<List<ChannelInfo>>> = _channels
    private val _top = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val top: StateFlow<Load<List<StreamInfo>>> = _top
    private val _games = MutableStateFlow<Load<List<Game>>>(Load.Loading)
    val games: StateFlow<Load<List<Game>>> = _games
    private val _gameStreams = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val gameStreams: StateFlow<Load<List<StreamInfo>>> = _gameStreams
    private val _search = MutableStateFlow<Load<List<StreamInfo>>?>(null)
    val search: StateFlow<Load<List<StreamInfo>>?> = _search
    private val _channelPage = MutableStateFlow<Load<ChannelPage>>(Load.Loading)
    val channelPage: StateFlow<Load<ChannelPage>> = _channelPage
    private val _continueWatching = MutableStateFlow<List<WatchHistory.Entry>>(emptyList())
    val continueWatching: StateFlow<List<WatchHistory.Entry>> = _continueWatching

    val loggedInName = MutableStateFlow(app.settings.userName)

    /** Index of the card to focus when returning to a list, keyed by list name. */
    val focusMemory = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            // Validate (and refresh if needed) once before firing the parallel list requests,
            // so they don't all race to refresh an expired token.
            if (app.auth.isLoggedIn) {
                withContext(Dispatchers.IO) { runCatching { app.auth.validate() } }
                launch(Dispatchers.IO) {
                    runCatching { app.helix.loadSelf() }
                    loggedInName.value = app.settings.userName
                }
            }
            refreshAll()
        }
    }

    private fun <T> load(target: MutableStateFlow<Load<T>>, showLoading: Boolean = true, block: () -> T) {
        if (showLoading || target.value !is Load.Ok) target.value = Load.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Load.Ok(block())
                } catch (e: NotLoggedInException) {
                    Load.NeedLogin
                } catch (e: Exception) {
                    Load.Error(e.message ?: "Fehler")
                }
            }
            // Keep showing old data if a silent refresh fails.
            if (result is Load.Ok || showLoading || target.value !is Load.Ok) target.value = result
        }
    }

    fun refreshAll() {
        refreshFollowed()
        load(_channels) { app.helix.followedChannels() }
        load(_top) { app.helix.topStreams() }
        load(_games) { app.helix.topGames() }
        refreshHistory()
    }

    fun refreshHistory() {
        _continueWatching.value = app.history.continueWatching()
    }

    fun refreshFollowed(silent: Boolean = false) = load(_followed, showLoading = !silent) { app.helix.followedStreams() }
    fun refreshTop(silent: Boolean = false) = load(_top, showLoading = !silent) { app.helix.topStreams() }

    fun openGame(game: Game) = load(_gameStreams) { app.helix.topStreams(gameId = game.id) }

    fun openChannel(login: String) {
        _channelPage.value = Load.Loading
        viewModelScope.launch {
            _channelPage.value = withContext(Dispatchers.IO) {
                try {
                    val info = app.helix.channel(login)
                    val id = info?.id
                    supervisorScope {
                        val live = async { runCatching { app.helix.stream(login) }.getOrNull() }
                        val videos = async { if (id == null) emptyList() else app.helix.videos(id, "archive") }
                        val highlights = async { if (id == null) emptyList() else runCatching { app.helix.videos(id, "highlight") }.getOrDefault(emptyList()) }
                        Load.Ok(ChannelPage(info, live.await(), videos.await(), highlights.await()))
                    }
                } catch (e: NotLoggedInException) {
                    Load.NeedLogin
                } catch (e: Exception) {
                    Load.Error(e.message ?: "Fehler")
                }
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
                    if (user != null) Load.Ok(listOf(StreamInfo(user.first, q.lowercase(), user.second, isLive = true)))
                    else Load.Error("Kanal \"$q\" nicht gefunden")
                } catch (e: Exception) {
                    Load.Error(e.message ?: "Fehler")
                }
            }
        }
    }

    fun onLoginChanged() {
        loggedInName.value = app.settings.userName
        refreshAll()
    }

    fun logout() {
        app.auth.logout()
        onLoginChanged()
    }
}
