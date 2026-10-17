package dev.purpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.formatViewers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlay: (List<StreamInfo>, Int) -> Unit,
    onGame: (Game) -> Unit,
    onLogin: () -> Unit,
) {
    val tab by vm.tab.collectAsState()
    val name by vm.loggedInName.collectAsState()
    val tabFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (vm.focusMemory.isEmpty()) runCatching { tabFocus.requestFocus() }
    }
    // Keep the followed list fresh while the app is open.
    LaunchedEffect(tab) {
        while (true) {
            delay(120_000)
            when (tab) {
                Tab.FOLLOWED -> vm.refreshFollowed(silent = true)
                Tab.TOP -> vm.refreshTop(silent = true)
                else -> Unit
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Purple", color = Colors.Purple, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("TV", color = Colors.Text, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(18.dp))
            Tab.entries.forEach { t ->
                PillButton(
                    text = t.label,
                    selected = t == tab,
                    onClick = { vm.tab.value = t },
                    onFocus = { vm.tab.value = t },
                    modifier = if (t == tab) Modifier.focusRequester(tabFocus) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(name ?: "Nicht angemeldet", color = Colors.TextDim, fontSize = 13.sp)
        }

        when (tab) {
            Tab.FOLLOWED -> StreamListPane(vm, "followed", vm.followed, onPlay, onLogin, empty = "Keiner deiner Kanäle ist gerade live.")
            Tab.TOP -> StreamListPane(vm, "top", vm.top, onPlay, onLogin, empty = "Keine Streams gefunden.")
            Tab.GAMES -> GamesPane(vm, onGame, onLogin)
            Tab.SEARCH -> SearchPane(vm, onPlay)
            Tab.SETTINGS -> SettingsScreen(vm, onLogin)
        }
    }
}

@Composable
fun GameStreamsScreen(vm: HomeViewModel, game: Game, onPlay: (List<StreamInfo>, Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            game.name,
            modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 12.dp),
            color = Colors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        StreamListPane(vm, "game-${game.id}", vm.gameStreams, onPlay, onLogin = {}, empty = "Niemand streamt das gerade.", autoFocus = true)
    }
}

@Composable
private fun StreamListPane(
    vm: HomeViewModel,
    key: String,
    flow: StateFlow<Load<List<StreamInfo>>>,
    onPlay: (List<StreamInfo>, Int) -> Unit,
    onLogin: () -> Unit,
    empty: String,
    autoFocus: Boolean = false,
) {
    when (val state = flow.collectAsState().value) {
        Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
        Load.NeedLogin -> LoginPrompt(onLogin)
        is Load.Error -> Centered { Text("Fehler: ${state.message}", color = Colors.TextDim) }
        is Load.Ok -> if (state.data.isEmpty()) {
            Centered { Text(empty, color = Colors.TextDim, fontSize = 18.sp) }
        } else {
            StreamGrid(vm, key, state.data, onPlay, autoFocus)
        }
    }
}

@Composable
fun StreamGrid(
    vm: HomeViewModel,
    key: String,
    streams: List<StreamInfo>,
    onPlay: (List<StreamInfo>, Int) -> Unit,
    autoFocus: Boolean = false,
) {
    val gridState = rememberLazyGridState()
    val restore = remember { FocusRequester() }
    val restoreIndex = (vm.focusMemory[key] ?: if (autoFocus) 0 else null)?.coerceIn(0, streams.lastIndex)

    LaunchedEffect(key) {
        if (restoreIndex == null) return@LaunchedEffect
        gridState.scrollToItem(restoreIndex)
        delay(50)
        runCatching { restore.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(streams, key = { _, s -> s.login }) { index, stream ->
            StreamCard(
                stream,
                onClick = {
                    vm.focusMemory[key] = index
                    val live = streams.filter { it.isLive }
                    if (stream.isLive) onPlay(live, live.indexOf(stream)) else onPlay(listOf(stream), 0)
                },
                modifier = if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier,
            )
        }
    }
}

@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(onClick = onClick, modifier = modifier.fillMaxWidth(), background = Colors.Surface, focusedBackground = Colors.SurfaceHigh) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Colors.SurfaceHigh)) {
                if (stream.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = stream.thumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (stream.avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = stream.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(CircleShape).align(Alignment.Center),
                    )
                }
                if (stream.isLive) {
                    LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                    if (stream.viewers > 0) {
                        Text(
                            "${formatViewers(stream.viewers)} Zuschauer",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Text(
                        "Offline",
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        color = Colors.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(stream.displayName, color = Colors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stream.title.ifBlank { " " }, color = Colors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stream.gameName.ifBlank { " " }, color = Colors.PurpleLight, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GamesPane(vm: HomeViewModel, onGame: (Game) -> Unit, onLogin: () -> Unit) {
    when (val state = vm.games.collectAsState().value) {
        Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
        Load.NeedLogin -> LoginPrompt(onLogin)
        is Load.Error -> Centered { Text("Fehler: ${state.message}", color = Colors.TextDim) }
        is Load.Ok -> LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.data, key = { _, g -> g.id }) { _, game ->
                Focusable(onClick = { onGame(game) }, background = Colors.Surface) {
                    Column {
                        AsyncImage(
                            model = game.boxArt(),
                            contentDescription = game.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Colors.SurfaceHigh),
                        )
                        Text(
                            game.name,
                            modifier = Modifier.padding(6.dp),
                            color = Colors.Text,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPane(vm: HomeViewModel, onPlay: (List<StreamInfo>, Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val result by vm.search.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 48.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Kanalname…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Colors.Purple,
                    focusedTextColor = Colors.Text,
                    unfocusedTextColor = Colors.Text,
                ),
                modifier = Modifier.width(420.dp),
            )
            Spacer(Modifier.width(16.dp))
            PillButton("Suchen", onClick = { vm.search(query) })
        }
        when (val r = result) {
            null -> Centered {
                Text(
                    "Suche nach einem Kanal. Ohne Login kannst du einen Kanal über seinen exakten Namen öffnen.",
                    color = Colors.TextDim,
                )
            }
            Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
            Load.NeedLogin -> Centered { Text("Bitte anmelden", color = Colors.TextDim) }
            is Load.Error -> Centered { Text(r.message, color = Colors.TextDim) }
            is Load.Ok -> if (r.data.isEmpty()) Centered { Text("Nichts gefunden", color = Colors.TextDim) }
            else StreamGrid(vm, "search-$query", r.data, onPlay)
        }
    }
}

@Composable
fun LoginPrompt(onLogin: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Melde dich mit deinem Twitch-Account an, um diese Liste zu sehen.", color = Colors.TextDim, fontSize = 16.sp)
            Spacer(Modifier.padding(8.dp))
            PillButton("Anmelden", onClick = onLogin, selected = true)
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) { content() }
}
