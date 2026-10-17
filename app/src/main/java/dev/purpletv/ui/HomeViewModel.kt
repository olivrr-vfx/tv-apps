package dev.purpletv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.purpletv.PurpleApp
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.NotLoggedInException
import dev.purpletv.twitch.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NeedLogin : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

enum class Tab(val label: String) {
    FOLLOWED("Gefolgt"),
    TOP("Beliebt"),
    GAMES("Kategorien"),
    SEARCH("Suche"),
    SETTINGS("Einstellungen"),
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PurpleApp

    val tab = MutableStateFlow(if (app.auth.isLoggedIn) Tab.FOLLOWED else Tab.SEARCH)

    private val _followed = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val followed: StateFlow<Load<List<StreamInfo>>> = _followed
    private val _top = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val top: StateFlow<Load<List<StreamInfo>>> = _top
    private val _games = MutableStateFlow<Load<List<Game>>>(Load.Loading)
    val games: StateFlow<Load<List<Game>>> = _games
    private val _gameStreams = MutableStateFlow<Load<List<StreamInfo>>>(Load.Loading)
    val gameStreams: StateFlow<Load<List<StreamInfo>>> = _gameStreams
    private val _search = MutableStateFlow<Load<List<StreamInfo>>?>(null)
    val search: StateFlow<Load<List<StreamInfo>>?> = _search

    val loggedInName = MutableStateFlow(app.settings.userName)

    /** Index of the card to focus when returning to a list, keyed by list name. */
    val focusMemory = mutableMapOf<String, Int>()

    init {
        if (app.auth.isLoggedIn) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { app.auth.validate(); app.helix.loadSelf() }
                loggedInName.value = app.settings.userName
            }
        }
        refreshAll()
    }

    private fun <T> load(target: MutableStateFlow<Load<T>>, showLoading: Boolean = true, block: () -> T) {
        if (showLoading || target.value !is Load.Ok) target.value = Load.Loading
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

    fun refreshAll() {
        refreshFollowed()
        load(_top) { app.helix.topStreams() }
        load(_games) { app.helix.topGames() }
    }

    fun refreshFollowed(silent: Boolean = false) = load(_followed, showLoading = !silent) { app.helix.followedStreams() }
    fun refreshTop(silent: Boolean = false) = load(_top, showLoading = !silent) { app.helix.topStreams() }

    fun openGame(game: Game) = load(_gameStreams) { app.helix.topStreams(gameId = game.id) }

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
