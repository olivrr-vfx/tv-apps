package dev.purpletv.twitch.ui

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.purpletv.core.AppId
import dev.purpletv.core.Load
import dev.purpletv.core.items
import dev.purpletv.design.AppMark
import dev.purpletv.design.Avatar
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.ContentRow
import dev.purpletv.design.EmptyState
import dev.purpletv.design.LiveBadge
import dev.purpletv.design.LoadContent
import dev.purpletv.design.LoadingState
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.Palette
import dev.purpletv.design.RestorableFocus
import dev.purpletv.design.Thumbnail
import dev.purpletv.design.Tokens
import dev.purpletv.design.TopTabs
import dev.purpletv.design.TvButton
import dev.purpletv.design.TvTextField
import dev.purpletv.design.Type
import dev.purpletv.hub.Navigator
import dev.purpletv.hub.Route
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.twitch.api.Game
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.VideoInfo
import dev.purpletv.twitch.api.formatAgo
import dev.purpletv.twitch.api.formatUptime
import dev.purpletv.twitch.api.formatViewers
import dev.purpletv.twitch.player.StreamPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

private val TwitchTab.icon: ImageVector
    get() = when (this) {
        TwitchTab.START -> Icons.Filled.Home
        TwitchTab.FOLLOWED -> Icons.Filled.LiveTv
        TwitchTab.TOP -> Icons.Filled.Explore
        TwitchTab.GAMES -> Icons.Filled.Category
        TwitchTab.SEARCH -> Icons.Filled.Search
    }

/** Navigation actions of the Twitch screens. */
class TwitchActions(private val vm: TwitchViewModel, private val nav: Navigator) {
    fun play(list: List<StreamInfo>, index: Int, followed: Boolean = false) {
        val stream = list.getOrNull(index) ?: return
        nav.push(Route.TwitchLive(list, stream.login, followed))
    }
    fun vod(video: VideoInfo) = nav.push(Route.TwitchVod(video))
    fun channel(login: String) = nav.openTwitchChannel(login)
    fun game(game: Game) {
        vm.openGame(game)
        nav.push(Route.TwitchGame(game))
    }
    fun login() = nav.push(Route.TwitchLogin)
}

/** The Twitch app: header with tabs, content below. */
@Composable
fun TwitchHome(vm: TwitchViewModel, nav: Navigator) {
    val tab by vm.tab.collectAsState()
    val actions = remember(vm, nav) { TwitchActions(vm, nav) }
    val holder = remember { HeroHolder() }
    LaunchedEffect(Unit) { vm.refreshHistory() }

    Box(Modifier.fillMaxSize()) {
        if (tab == TwitchTab.START) HeroImage(holder, vm)
        Column(Modifier.fillMaxSize()) {
            TwitchHeader(vm, tab)
            when (tab) {
                TwitchTab.START -> StartTab(vm, actions, holder)
                TwitchTab.FOLLOWED -> StreamListPane(vm, "followed", vm.repo.followed, actions, followed = true,
                    empty = "Keiner deiner Kanäle ist gerade live.", onRetry = { vm.repo.refreshFollowed() })
                TwitchTab.TOP -> StreamListPane(vm, "top", vm.repo.top, actions, empty = "Keine Streams gefunden.",
                    onRetry = { vm.repo.refreshTop() })
                TwitchTab.GAMES -> GamesPane(vm, actions)
                TwitchTab.SEARCH -> SearchPane(vm, actions)
            }
        }
    }
}

@Composable
private fun TwitchHeader(vm: TwitchViewModel, tab: TwitchTab) {
    val name by vm.repo.loggedInName.collectAsState()
    val updated by vm.repo.followedUpdatedAt.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Layout.safeVertical, bottom = Tokens.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(AppId.TWITCH, 36.dp)
        Spacer(Modifier.width(Tokens.Space.md))
        Text("Twitch", color = Palette.text, style = Type.title)
        Spacer(Modifier.width(Tokens.Space.xl))
        TopTabs(
            tabs = TwitchTab.entries,
            selected = tab,
            label = { it.label },
            icon = { it.icon },
            onSelect = { vm.tab.value = it },
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(name ?: "Nicht angemeldet", color = Palette.textDim, style = Type.label)
            if (updated > 0) {
                Text("Live-Liste von ${clock(updated)}", color = Palette.textFaint, style = Type.caption)
            }
        }
    }
}

private fun clock(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(millis))

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
    // Same URL as the card thumbnail, so it's already in the image cache.
    image = thumbnail(),
    avatar = avatarUrl,
    liveLogin = if (isLive) login else null,
)

private fun VideoInfo.hero() = Hero(
    title = userName,
    headline = title,
    details = listOfNotNull(formatAgo(createdAtMillis()).ifBlank { null }, "Video").joinToString("  ·  "),
    image = thumbnail(),
)

/**
 * The focused card writes [target]; [shown] follows with a small delay so fast scrolling doesn't
 * swap images on every step. Only the hero composables read these, so focus changes don't
 * recompose the rows.
 */
@Stable
private class HeroHolder {
    var target by mutableStateOf<Hero?>(null)
    var shown by mutableStateOf<Hero?>(null)
}

private val HERO_HEIGHT = 168.dp

@Composable
private fun StartTab(vm: TwitchViewModel, actions: TwitchActions, holder: HeroHolder) {
    val followed by vm.repo.followed.collectAsState()
    val top by vm.repo.top.collectAsState()
    val games by vm.repo.games.collectAsState()
    val channels by vm.repo.channels.collectAsState()
    val history by vm.continueWatching.collectAsState()
    val memory = vm.focusMemory

    if (followed == Load.NeedLogin || top == Load.NeedLogin) {
        EmptyState(
            "Willkommen bei Twitch",
            body = "Melde dich an, um deine Kanäle, VODs und Empfehlungen zu sehen. Ohne Login kannst du Kanäle über die Suche öffnen.",
            icon = Icons.Filled.Login,
            action = "Mit Twitch anmelden",
            onAction = { actions.login() },
        )
        return
    }
    val liveFollowed = followed.items()
    val topStreams = top.items()
    val fallback = remember(liveFollowed, topStreams) { (liveFollowed.firstOrNull() ?: topStreams.firstOrNull())?.hero() }

    // A hero that went offline must not stay on screen.
    LaunchedEffect(liveFollowed) {
        val login = holder.target?.liveLogin ?: return@LaunchedEffect
        if (followed is Load.Ok && liveFollowed.none { it.login == login } && topStreams.none { it.login == login }) holder.target = null
    }
    LaunchedEffect(holder, fallback) {
        snapshotFlow { holder.target ?: fallback }.collectLatest {
            if (holder.shown != null) delay(Tokens.Motion.heroDelay.toLong())
            holder.shown = it
        }
    }

    Column(Modifier.fillMaxSize()) {
        HeroInfo(holder)
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (liveFollowed.isNotEmpty()) item("followed") {
                ContentRow("Live – Gefolgt", trailing = "${liveFollowed.size}") {
                    itemsIndexed(liveFollowed, key = { _, s -> s.login }) { i, s ->
                        RestorableFocus(memory, "start-followed", i) { m ->
                            StreamCard(s, onClick = { memory.remember("start-followed", i); actions.play(liveFollowed, i, followed = true) },
                                modifier = m.width(Tokens.Layout.cardWide), onFocus = { holder.target = s.hero() })
                        }
                    }
                }
            }
            if (history.isNotEmpty()) item("history") {
                ContentRow("Weiter schauen") {
                    itemsIndexed(history, key = { _, e -> e.video.id }) { i, e ->
                        RestorableFocus(memory, "start-history", i) { m ->
                            VodCard(e.video, onClick = { memory.remember("start-history", i); actions.vod(e.video) },
                                modifier = m.width(Tokens.Layout.cardWide), progress = e.progress, showChannel = true,
                                onFocus = { holder.target = e.video.hero() })
                        }
                    }
                }
            }
            val chans = channels.items()
            if (chans.isNotEmpty()) item("channels") {
                val liveLogins = remember(liveFollowed) { liveFollowed.map { it.login }.toSet() }
                val sorted = remember(chans, liveLogins) { chans.sortedByDescending { it.login in liveLogins } }
                ContentRow("Deine Kanäle") {
                    itemsIndexed(sorted, key = { _, c -> c.id }) { i, c ->
                        RestorableFocus(memory, "start-channels", i) { m ->
                            ChannelCard(c, live = c.login in liveLogins, onClick = { memory.remember("start-channels", i); actions.channel(c.login) },
                                modifier = m.width(Tokens.Layout.cardAvatar))
                        }
                    }
                }
            }
            if (topStreams.isNotEmpty()) item("top") {
                ContentRow("Beliebt auf Twitch") {
                    itemsIndexed(topStreams, key = { _, s -> s.login }) { i, s ->
                        RestorableFocus(memory, "start-top", i) { m ->
                            StreamCard(s, onClick = { memory.remember("start-top", i); actions.play(topStreams, i) },
                                modifier = m.width(Tokens.Layout.cardWide), onFocus = { holder.target = s.hero() })
                        }
                    }
                }
            }
            val gameList = games.items()
            if (gameList.isNotEmpty()) item("games") {
                ContentRow("Top-Kategorien") {
                    itemsIndexed(gameList, key = { _, g -> g.id }) { i, g ->
                        RestorableFocus(memory, "start-games", i) { m ->
                            GameCard(g, onClick = { memory.remember("start-games", i); actions.game(g) }, modifier = m.width(Tokens.Layout.cardPoster))
                        }
                    }
                }
            }
            if (followed == Load.Loading && top == Load.Loading) item("loading") {
                LoadingState(Modifier.height(200.dp))
            }
        }
    }
}

/** Picture behind the hero text, fading into the page well before the rows start. */
@Composable
private fun HeroImage(holder: HeroHolder, vm: TwitchViewModel) {
    val hero = holder.shown
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT + 120.dp)) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().aspectRatio(16f / 9f)) {
            if (hero != null) Thumbnail(hero.image, Modifier.fillMaxSize())
            val live = hero?.liveLogin
            if (live != null && vm.app.settings.livePreview) LivePreview(live, vm)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.canvas, 0.42f to Palette.canvas, 0.75f to Palette.canvas.copy(alpha = 0.35f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Palette.canvas)))
    }
}

@Composable
private fun HeroInfo(holder: HeroHolder) {
    val hero = holder.shown
    Column(Modifier.fillMaxWidth().height(HERO_HEIGHT).padding(start = Tokens.Layout.safeHorizontal, top = Tokens.Space.lg, end = 380.dp)) {
        if (hero == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hero.avatar.isNotEmpty()) {
                Avatar(hero.avatar, 44.dp, live = hero.liveLogin != null)
                Spacer(Modifier.width(Tokens.Space.md))
            }
            if (hero.liveLogin != null) {
                LiveBadge()
                Spacer(Modifier.width(10.dp))
            }
            Text(hero.title, color = Color.White, style = Type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Tokens.Space.sm))
        Text(hero.headline, color = Palette.text, style = Type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(hero.details, color = LocalAccent.current.light, style = Type.label, maxLines = 1)
    }
}

/**
 * Muted low-res live preview of the focused stream. Uses a TextureView: a SurfaceView would punch
 * through the Compose layers and show up in the wrong places.
 */
@Composable
private fun LivePreview(login: String, vm: TwitchViewModel) {
    val context = LocalContext.current
    val player = remember { StreamPlayer(context, vm.app.settings, vm.app.gql, PlayerMode.PREVIEW) }
    val state by player.state.collectAsState()
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(login) {
        player.stop()
        delay(1_500)
        player.play(login)
    }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { player.player.setVideoTextureView(it) } },
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (state == PlaybackState.Playing) 1f else 0f },
    )
}

// ---------------------------------------------------------------------------------------------
// Grids

@Composable
fun GameStreamsScreen(vm: TwitchViewModel, game: Game, nav: Navigator) {
    val actions = remember(vm, nav) { TwitchActions(vm, nav) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = Tokens.Layout.safeHorizontal, top = Tokens.Layout.safeVertical, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(game.boxArt(90, 120), Modifier.size(45.dp, 60.dp).clip(RoundedCornerShape(Tokens.Radius.sm)))
            Spacer(Modifier.width(Tokens.Space.lg))
            Column {
                Text("TWITCH · KATEGORIE", color = LocalAccent.current.light, style = Type.overline)
                Text(game.name, color = Palette.text, style = Type.headline)
            }
        }
        StreamListPane(vm, "game-${game.id}", vm.gameStreams, actions, empty = "Niemand streamt das gerade.", autoFocus = true,
            onRetry = { vm.openGame(game) })
    }
}

@Composable
private fun StreamListPane(
    vm: TwitchViewModel,
    key: String,
    flow: StateFlow<Load<List<StreamInfo>>>,
    actions: TwitchActions,
    empty: String,
    followed: Boolean = false,
    autoFocus: Boolean = false,
    onRetry: () -> Unit,
) {
    LoadContent(flow.collectAsState().value, onRetry = onRetry, onLogin = { actions.login() }) { data ->
        if (data.isEmpty()) {
            EmptyState(empty, icon = Icons.Filled.VideocamOff)
        } else {
            StreamGrid(vm, key, data, autoFocus) { list, index -> actions.play(list, index, followed) }
        }
    }
}

@Composable
fun StreamGrid(
    vm: TwitchViewModel,
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
        contentPadding = PaddingValues(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Space.lg, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Layout.gridGap),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(streams, key = { _, s -> s.login }) { index, stream ->
            StreamCard(
                stream,
                onClick = {
                    vm.focusMemory.remember(key, index)
                    onClick(streams, index)
                },
                modifier = if (index == restoreIndex) Modifier.focusRequester(restore) else Modifier,
            )
        }
    }
}

@Composable
private fun GamesPane(vm: TwitchViewModel, actions: TwitchActions) {
    LoadContent(vm.repo.games.collectAsState().value, onRetry = { vm.repo.refreshAll() }, onLogin = { actions.login() }) { games ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Space.lg, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lg),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(games, key = { _, g -> g.id }) { _, game ->
                GameCard(game, onClick = { actions.game(game) })
            }
        }
    }
}

@Composable
private fun SearchPane(vm: TwitchViewModel, actions: TwitchActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val result by vm.search.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = Tokens.Layout.safeHorizontal, vertical = Tokens.Space.sm), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Kanalname…",
                icon = Icons.Filled.Search,
                imeAction = ImeAction.Search,
                onSubmit = { vm.search(query) },
                modifier = Modifier.width(480.dp),
            )
            Spacer(Modifier.width(Tokens.Space.lg))
            TvButton("Suchen", onClick = { vm.search(query) }, style = ButtonStyle.Primary)
        }
        when (val r = result) {
            null -> EmptyState(
                "Kanal suchen",
                body = "Ohne Login kannst du einen Kanal über seinen exakten Namen öffnen. Tipp: Mit der Handy-Fernbedienung tippst du schneller.",
                icon = Icons.Filled.Search,
            )
            else -> LoadContent(r, onRetry = { vm.search(query) }) { data ->
                if (data.isEmpty()) {
                    EmptyState("Nichts gefunden", icon = Icons.Filled.Search)
                } else if (vm.app.auth.isLoggedIn) {
                    // Search results open the channel page (live stream + VODs).
                    StreamGrid(vm, "search-$query", data) { list, index -> actions.channel(list[index].login) }
                } else {
                    StreamGrid(vm, "search-$query", data) { list, index ->
                        if (list[index].isLive) actions.play(list, index) else actions.channel(list[index].login)
                    }
                }
            }
        }
    }
}
