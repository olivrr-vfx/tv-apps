package dev.purpletv.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.player.StreamPlayer
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo
import dev.purpletv.twitch.formatAgo
import dev.purpletv.twitch.formatUptime
import dev.purpletv.twitch.formatViewers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

class HomeActions(
    val play: (List<StreamInfo>, Int) -> Unit,
    val vod: (VideoInfo) -> Unit,
    val channel: (String) -> Unit,
    val game: (Game) -> Unit,
    val login: () -> Unit,
)

private val NAV_COLLAPSED = 84.dp

private val Tab.icon: ImageVector
    get() = when (this) {
        Tab.START -> Icons.Filled.Home
        Tab.FOLLOWED -> Icons.Filled.LiveTv
        Tab.TOP -> Icons.Filled.Explore
        Tab.GAMES -> Icons.Filled.Category
        Tab.SEARCH -> Icons.Filled.Search
        Tab.SETTINGS -> Icons.Filled.Settings
    }

@Composable
fun HomeScreen(vm: HomeViewModel, actions: HomeActions) {
    val tab by vm.tab.collectAsState()

    // Keep the lists fresh while the app is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(120_000)
            vm.refreshFollowed(silent = true)
            vm.refreshTop(silent = true)
        }
    }
    LaunchedEffect(Unit) { vm.refreshHistory() }

    Box(Modifier.fillMaxSize().background(Colors.Background)) {
        Box(Modifier.fillMaxSize().padding(start = NAV_COLLAPSED)) {
            Crossfade(tab, label = "tab") { t ->
                when (t) {
                    Tab.START -> StartTab(vm, actions)
                    Tab.FOLLOWED -> PageWithTitle("Live – Gefolgt") {
                        StreamListPane(vm, "followed", vm.followed, actions, empty = "Keiner deiner Kanäle ist gerade live.")
                    }
                    Tab.TOP -> PageWithTitle("Beliebt auf Twitch") {
                        StreamListPane(vm, "top", vm.top, actions, empty = "Keine Streams gefunden.")
                    }
                    Tab.GAMES -> PageWithTitle("Kategorien") { GamesPane(vm, actions) }
                    Tab.SEARCH -> PageWithTitle("Suche") { SearchPane(vm, actions) }
                    Tab.SETTINGS -> PageWithTitle("Einstellungen") { SettingsScreen(vm, actions.login) }
                }
            }
        }
        NavRail(vm, tab, Modifier.zIndex(1f))
    }
}

@Composable
private fun NavRail(vm: HomeViewModel, tab: Tab, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 230.dp else NAV_COLLAPSED, label = "rail")
    val name by vm.loggedInName.collectAsState()
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (vm.focusMemory.isEmpty()) runCatching { initialFocus.requestFocus() }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Color(0xF2101014), Color(0xE6101014), Color.Transparent))
                else Brush.horizontalGradient(listOf(Color(0xFF101014), Color(0xFF101014)))
            )
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 28.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colors.purpleGradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text("Purple", color = Colors.PurpleLight, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("TV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
        Tab.entries.forEach { t ->
            NavItem(
                t, selected = t == tab, expanded = expanded,
                onSelect = { vm.tab.value = t },
                modifier = if (t == tab) Modifier.focusRequester(initialFocus) else Modifier,
            )
        }
        Spacer(Modifier.weight(1f))
        if (expanded) {
            Text(
                name ?: "Nicht angemeldet",
                modifier = Modifier.padding(start = 10.dp),
                color = Colors.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, expanded: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    Focusable(
        onClick = onSelect,
        onFocus = onSelect,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.03f,
        background = if (selected) Colors.Purple.copy(alpha = 0.22f) else Color.Transparent,
        focusedBackground = Color.White,
        glow = false,
    ) { focused ->
        val tint = when {
            focused -> Color.Black
            selected -> Colors.PurpleLight
            else -> Colors.TextDim
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(26.dp))
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(tab.label, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PageWithTitle(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            modifier = Modifier.padding(start = 40.dp, top = 28.dp, bottom = 6.dp),
            color = Colors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
        )
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Start tab: hero + rows

private data class Hero(
    val title: String,
    val headline: String,
    val details: String,
    val image: String,
    val avatar: String = "",
    val liveLogin: String? = null,
)

private fun StreamInfo.hero() = Hero(
    title = displayName,
    headline = title,
    details = listOfNotNull(
        gameName.ifBlank { null },
        if (viewers > 0) "${formatViewers(viewers)} Zuschauer" else null,
        formatUptime(startedAtMillis()).ifBlank { null },
    ).joinToString("  ·  "),
    image = thumbnail(1280, 720),
    avatar = avatarUrl,
    liveLogin = if (isLive) login else null,
)

private fun VideoInfo.hero() = Hero(
    title = userName,
    headline = title,
    details = listOfNotNull(formatAgo(createdAtMillis()).ifBlank { null }, "VOD").joinToString("  ·  "),
    image = thumbnail(1280, 720),
)

@Composable
private fun StartTab(vm: HomeViewModel, actions: HomeActions) {
    val followed by vm.followed.collectAsState()
    val top by vm.top.collectAsState()
    val games by vm.games.collectAsState()
    val channels by vm.channels.collectAsState()
    val history by vm.continueWatching.collectAsState()
    var hero by remember { mutableStateOf<Hero?>(null) }

    if (followed == Load.NeedLogin || top == Load.NeedLogin) {
        Welcome(actions.login)
        return
    }
    val liveFollowed = followed.items()
    val firstHero = liveFollowed.firstOrNull()?.hero() ?: top.items().firstOrNull()?.hero()
    val shown = hero ?: firstHero

    Box(Modifier.fillMaxSize()) {
        HeroBackground(shown, vm)
        LazyColumn(
            contentPadding = PaddingValues(top = 250.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (liveFollowed.isNotEmpty()) item("followed") {
                ContentRow("Live – Gefolgt") {
                    itemsIndexed(liveFollowed, key = { _, s -> s.login }) { i, s ->
                        RestorableCard(vm, "start-followed", i) { m ->
                            StreamCard(s, onClick = { vm.focusMemory["start-followed"] = i; actions.play(liveFollowed, i) }, modifier = m.width(260.dp), onFocus = { hero = s.hero() })
                        }
                    }
                }
            }
            if (history.isNotEmpty()) item("history") {
                ContentRow("Weiter schauen") {
                    itemsIndexed(history, key = { _, e -> e.video.id }) { i, e ->
                        RestorableCard(vm, "start-history", i) { m ->
                            VideoCard(
                                e.video,
                                onClick = { vm.focusMemory["start-history"] = i; actions.vod(e.video) },
                                modifier = m.width(260.dp),
                                progress = e.progress,
                                showChannel = true,
                                onFocus = { hero = e.video.hero() },
                            )
                        }
                    }
                }
            }
            val chans = channels.items()
            if (chans.isNotEmpty()) item("channels") {
                val liveLogins = liveFollowed.map { it.login }.toSet()
                val sorted = chans.sortedByDescending { it.login in liveLogins }
                ContentRow("Deine Kanäle") {
                    itemsIndexed(sorted, key = { _, c -> c.id }) { i, c ->
                        RestorableCard(vm, "start-channels", i) { m ->
                            ChannelCard(c, live = c.login in liveLogins, onClick = { vm.focusMemory["start-channels"] = i; actions.channel(c.login) }, modifier = m.width(124.dp))
                        }
                    }
                }
            }
            val topStreams = top.items()
            if (topStreams.isNotEmpty()) item("top") {
                ContentRow("Beliebt auf Twitch") {
                    itemsIndexed(topStreams, key = { _, s -> s.login }) { i, s ->
                        RestorableCard(vm, "start-top", i) { m ->
                            StreamCard(s, onClick = { vm.focusMemory["start-top"] = i; actions.play(topStreams, i) }, modifier = m.width(260.dp), onFocus = { hero = s.hero() })
                        }
                    }
                }
            }
            val gameList = games.items()
            if (gameList.isNotEmpty()) item("games") {
                ContentRow("Top-Kategorien") {
                    itemsIndexed(gameList, key = { _, g -> g.id }) { i, g ->
                        RestorableCard(vm, "start-games", i) { m ->
                            GameCard(g, onClick = { vm.focusMemory["start-games"] = i; actions.game(g) }, modifier = m.width(124.dp))
                        }
                    }
                }
            }
            if (followed == Load.Loading && top == Load.Loading) item("loading") {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Colors.Purple)
                }
            }
        }
    }
}

@Composable
private fun ContentRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(start = 40.dp, top = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

/** Gives the card that was clicked last a FocusRequester so focus lands there again on return. */
@Composable
private fun RestorableCard(vm: HomeViewModel, key: String, index: Int, content: @Composable (Modifier) -> Unit) {
    if (vm.focusMemory[key] == index) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { requester.requestFocus() }
        }
        content(Modifier.focusRequester(requester))
    } else {
        content(Modifier)
    }
}

@Composable
private fun HeroBackground(hero: Hero?, vm: HomeViewModel) {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
        Crossfade(hero?.image, label = "hero") { image ->
            if (image != null) {
                AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (vm.app.settings.livePreview) hero?.liveLogin?.let { LivePreview(it, vm) }
        // Fade the picture into the page so the rows stay readable.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Colors.Background, Colors.Background.copy(alpha = 0.7f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Colors.Background)))
        hero?.let {
            Column(Modifier.padding(start = 40.dp, top = 40.dp).width(560.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (it.avatar.isNotEmpty()) {
                        Avatar(it.avatar, 40.dp, live = it.liveLogin != null)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (it.liveLogin != null) {
                        LiveBadge()
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(it.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text(it.headline, color = Colors.Text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(it.details, color = Colors.PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Muted low-res live preview of the focused stream, started after a short delay. */
@Composable
private fun LivePreview(login: String, vm: HomeViewModel) {
    val context = LocalContext.current
    val player = remember { StreamPlayer(context, vm.app.settings, vm.app.gql, PlayerMode.PREVIEW) }
    val state by player.state.collectAsState()
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(login) {
        player.stop()
        delay(1_200)
        player.play(login)
    }
    AnimatedVisibility(state == PlaybackState.Playing, enter = fadeIn(), exit = fadeOut()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    isFocusable = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Welcome(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Colors.PurpleDeep.copy(alpha = 0.5f), Colors.Background))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Willkommen bei PurpleTV", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("Melde dich an, um deine Kanäle, VODs und Empfehlungen zu sehen.", color = Colors.TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(24.dp))
            PillButton("Mit Twitch anmelden", onClick = onLogin, selected = true, icon = Icons.Filled.Login)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Grids

@Composable
fun GameStreamsScreen(vm: HomeViewModel, game: Game, actions: HomeActions) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 40.dp, top = 28.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(game.boxArt(90, 120), null, modifier = Modifier.size(45.dp, 60.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(16.dp))
            Text(game.name, color = Colors.Text, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        StreamListPane(vm, "game-${game.id}", vm.gameStreams, actions, empty = "Niemand streamt das gerade.", autoFocus = true)
    }
}

@Composable
private fun StreamListPane(
    vm: HomeViewModel,
    key: String,
    flow: StateFlow<Load<List<StreamInfo>>>,
    actions: HomeActions,
    empty: String,
    autoFocus: Boolean = false,
) {
    when (val state = flow.collectAsState().value) {
        Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
        Load.NeedLogin -> LoginPrompt(actions.login)
        is Load.Error -> Centered { Text("Fehler: ${state.message}", color = Colors.TextDim) }
        is Load.Ok -> if (state.data.isEmpty()) {
            Centered { Text(empty, color = Colors.TextDim, fontSize = 18.sp) }
        } else {
            StreamGrid(vm, key, state.data, autoFocus) { list, index -> actions.play(list, index) }
        }
    }
}

@Composable
fun StreamGrid(
    vm: HomeViewModel,
    key: String,
    streams: List<StreamInfo>,
    autoFocus: Boolean = false,
    onClick: (List<StreamInfo>, Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val restore = remember { FocusRequester() }
    val restoreIndex = (vm.focusMemory[key] ?: if (autoFocus) 0 else null)?.coerceIn(0, streams.lastIndex)

    LaunchedEffect(key) {
        if (restoreIndex == null) return@LaunchedEffect
        gridState.scrollToItem(restoreIndex)
        delay(80)
        runCatching { restore.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(streams, key = { _, s -> s.login }) { index, stream ->
            StreamCard(
                stream,
                onClick = {
                    vm.focusMemory[key] = index
                    onClick(streams, index)
                },
                modifier = if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier,
            )
        }
    }
}

@Composable
private fun GamesPane(vm: HomeViewModel, actions: HomeActions) {
    when (val state = vm.games.collectAsState().value) {
        Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
        Load.NeedLogin -> LoginPrompt(actions.login)
        is Load.Error -> Centered { Text("Fehler: ${state.message}", color = Colors.TextDim) }
        is Load.Ok -> LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.data, key = { _, g -> g.id }) { _, game ->
                GameCard(game, onClick = { actions.game(game) })
            }
        }
    }
}

@Composable
private fun SearchPane(vm: HomeViewModel, actions: HomeActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val result by vm.search.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 40.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Kanalname…") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Colors.TextDim) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Colors.Purple,
                    unfocusedBorderColor = Colors.SurfaceHigh,
                    focusedContainerColor = Colors.Surface,
                    unfocusedContainerColor = Colors.Surface,
                    focusedTextColor = Colors.Text,
                    unfocusedTextColor = Colors.Text,
                ),
                modifier = Modifier.width(460.dp),
            )
            Spacer(Modifier.width(16.dp))
            PillButton("Suchen", onClick = { vm.search(query) }, selected = true)
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
            is Load.Ok -> if (r.data.isEmpty()) {
                Centered { Text("Nichts gefunden", color = Colors.TextDim) }
            } else if (vm.app.auth.isLoggedIn) {
                // Search results open the channel page (live stream + VODs).
                StreamGrid(vm, "search-$query", r.data) { list, index -> actions.channel(list[index].login) }
            } else {
                StreamGrid(vm, "search-$query", r.data) { list, index -> actions.play(list, index) }
            }
        }
    }
}

@Composable
fun LoginPrompt(onLogin: () -> Unit) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Melde dich mit deinem Twitch-Account an, um diese Liste zu sehen.", color = Colors.TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            PillButton("Anmelden", onClick = onLogin, selected = true, icon = Icons.Filled.Login)
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { content() }
}
