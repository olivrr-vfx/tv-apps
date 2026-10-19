package dev.purpletv.youtube.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.purpletv.core.AppId
import dev.purpletv.core.Load
import dev.purpletv.core.items
import dev.purpletv.design.AppMark
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.ContentRow
import dev.purpletv.design.EmptyState
import dev.purpletv.design.FocusMemory
import dev.purpletv.design.LoadContent
import dev.purpletv.design.LoadingState
import dev.purpletv.design.Palette
import dev.purpletv.design.RestorableFocus
import dev.purpletv.design.SectionHeader
import dev.purpletv.design.Tokens
import dev.purpletv.design.TopTabs
import dev.purpletv.design.TvButton
import dev.purpletv.design.TvTextField
import dev.purpletv.design.Type
import dev.purpletv.hub.Navigator
import dev.purpletv.hub.Route
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.YtVideo

private val YouTubeTab.icon: ImageVector
    get() = when (this) {
        YouTubeTab.START -> Icons.Filled.Home
        YouTubeTab.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
        YouTubeTab.SEARCH -> Icons.Filled.Search
        YouTubeTab.HISTORY -> Icons.Filled.History
    }

@Composable
fun YouTubeHome(vm: YouTubeViewModel, nav: Navigator) {
    val tab by vm.tab.collectAsState()
    LaunchedEffect(Unit) { vm.refreshHistory() }
    val play: (YtVideo, List<YtVideo>) -> Unit = { v, queue -> nav.push(Route.YouTubeWatch(v, queue)) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Layout.safeVertical, bottom = Tokens.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(AppId.YOUTUBE, 36.dp)
            Spacer(Modifier.width(Tokens.Space.md))
            Text("YouTube", color = Palette.text, style = Type.title)
            Spacer(Modifier.width(Tokens.Space.xl))
            TopTabs(YouTubeTab.entries, tab, label = { it.label }, icon = { it.icon }, onSelect = { vm.tab.value = it }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Tokens.Space.md))
            val subs by vm.repo.subscriptions.collectAsState()
            Text("${subs.size} Abos", color = Palette.textFaint, style = Type.caption, maxLines = 1)
        }
        when (tab) {
            YouTubeTab.START -> StartTab(vm, nav, play)
            YouTubeTab.SUBSCRIPTIONS -> SubscriptionsTab(vm, nav, play)
            YouTubeTab.SEARCH -> SearchTab(vm, nav, play)
            YouTubeTab.HISTORY -> HistoryTab(vm, play)
        }
    }
}

@Composable
private fun StartTab(vm: YouTubeViewModel, nav: Navigator, play: (YtVideo, List<YtVideo>) -> Unit) {
    val feed by vm.repo.feed.collectAsState()
    val subs by vm.repo.subscriptions.collectAsState()
    val history by vm.history.collectAsState()
    val memory = vm.focusMemory

    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.fillMaxSize()) {
        if (history.isNotEmpty()) item("history") {
            ContentRow("Weiter schauen") {
                itemsIndexed(history, key = { _, e -> e.video.id }) { i, e ->
                    RestorableFocus(memory, "history", i) { m ->
                        VideoCard(e.video, onClick = { memory.remember("history", i); play(e.video, history.map { it.video }) },
                            modifier = m.width(Tokens.Layout.cardWide), progress = e.progress)
                    }
                }
            }
        }
        if (subs.isEmpty()) {
            item("onboarding") {
                EmptyState(
                    "Noch keine Abos",
                    body = "Couch braucht kein Google-Konto: Such Kanäle und abonniere sie hier auf dem TV – oder füge sie bequem mit der Handy-Fernbedienung hinzu.",
                    icon = Icons.Filled.Subscriptions,
                    action = "Kanäle suchen",
                    onAction = { vm.tab.value = YouTubeTab.SEARCH },
                    modifier = Modifier.height(340.dp),
                )
            }
        } else {
            item("feed") {
                when (val f = feed) {
                    is Load.Ok -> if (f.data.isNotEmpty()) {
                        ContentRow("Neu von deinen Abos") {
                            itemsIndexed(f.data.take(30), key = { _, v -> v.id }) { i, v ->
                                RestorableFocus(memory, "feed", i) { m ->
                                    VideoCard(v, onClick = { memory.remember("feed", i); play(v, f.data) }, modifier = m.width(Tokens.Layout.cardWide))
                                }
                            }
                        }
                    }
                    Load.Loading -> LoadingState(Modifier.height(220.dp))
                    else -> Unit
                }
            }
        }
        YouTubeClient.shelves.forEach { shelf ->
            item(shelf.id) {
                val state by vm.repo.shelves.getValue(shelf.id).collectAsState()
                val videos = state.items()
                if (videos.isNotEmpty()) {
                    ContentRow(shelf.title) {
                        itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
                            RestorableFocus(memory, shelf.id, i) { m ->
                                VideoCard(v, onClick = { memory.remember(shelf.id, i); play(v, videos) }, modifier = m.width(Tokens.Layout.cardWide))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsTab(vm: YouTubeViewModel, nav: Navigator, play: (YtVideo, List<YtVideo>) -> Unit) {
    val subs by vm.repo.subscriptions.collectAsState()
    val feed by vm.repo.feed.collectAsState()
    if (subs.isEmpty()) {
        EmptyState(
            "Noch keine Abos",
            body = "Öffne einen Kanal über die Suche und wähle „Abonnieren“.",
            icon = Icons.Filled.Subscriptions,
            action = "Kanäle suchen",
            onAction = { vm.tab.value = YouTubeTab.SEARCH },
        )
        return
    }
    LoadContent(feed, onRetry = { vm.repo.refreshFeed() }) { videos ->
        VideoGrid(videos, vm.focusMemory, "subs", play) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionHeader("Deine Kanäle", trailing = "${subs.size}")
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(vertical = Tokens.Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lg),
                    ) {
                        itemsIndexed(subs, key = { _, c -> c.id }) { _, c ->
                            YtChannelCard(c, onClick = { nav.openYouTubeChannel(c.url, c.name) }, modifier = Modifier.width(Tokens.Layout.cardAvatar))
                        }
                    }
                    SectionHeader("Neueste Videos")
                }
            }
        }
    }
}

@Composable
private fun SearchTab(vm: YouTubeViewModel, nav: Navigator, play: (YtVideo, List<YtVideo>) -> Unit) {
    val query by vm.query.collectAsState()
    val result by vm.search.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = Tokens.Layout.safeHorizontal, vertical = Tokens.Space.sm), verticalAlignment = Alignment.CenterVertically) {
            TvTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = "Videos und Kanäle suchen…",
                icon = Icons.Filled.Search,
                imeAction = ImeAction.Search,
                onSubmit = { vm.search() },
                modifier = Modifier.width(520.dp),
            )
            Spacer(Modifier.width(Tokens.Space.lg))
            TvButton("Suchen", onClick = { vm.search() }, style = ButtonStyle.Primary)
        }
        when (val r = result) {
            null -> EmptyState(
                "YouTube durchsuchen",
                body = "Tipp: Auf dem Handy tippst du schneller – öffne Couch auf dem Handy oder Mac und such dort.",
                icon = Icons.Filled.Search,
            )
            else -> LoadContent(r, onRetry = { vm.search() }) { data ->
                if (data.videos.isEmpty() && data.channels.isEmpty()) {
                    EmptyState("Nichts gefunden", icon = Icons.Filled.Search)
                } else {
                    VideoGrid(data.videos, vm.focusMemory, "search-$query", play) {
                        if (data.channels.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                SectionHeader("Kanäle")
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = PaddingValues(vertical = Tokens.Space.md),
                                    horizontalArrangement = Arrangement.spacedBy(Tokens.Space.lg),
                                ) {
                                    itemsIndexed(data.channels, key = { _, c -> c.url }) { _, c ->
                                        YtChannelCard(c, onClick = { nav.openYouTubeChannel(c.url, c.name) }, modifier = Modifier.width(Tokens.Layout.cardAvatar))
                                    }
                                }
                                SectionHeader("Videos")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(vm: YouTubeViewModel, play: (YtVideo, List<YtVideo>) -> Unit) {
    val history by vm.history.collectAsState()
    if (history.isEmpty()) {
        EmptyState("Noch nichts angeschaut", body = "Angefangene Videos erscheinen hier, damit du später weiterschauen kannst.", icon = Icons.Filled.VideoLibrary)
        return
    }
    val progress = remember(history) { history.associate { it.video.id to it.progress } }
    VideoGrid(history.map { it.video }, vm.focusMemory, "history-tab", play, progress = progress)
}

@Composable
fun VideoGrid(
    videos: List<YtVideo>,
    memory: FocusMemory,
    key: String,
    play: (YtVideo, List<YtVideo>) -> Unit,
    progress: Map<String, Float> = emptyMap(),
    header: LazyGridScope.() -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Space.lg, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Layout.gridGap),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header()
        itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
            RestorableFocus(memory, key, i) { m ->
                VideoCard(v, onClick = { memory.remember(key, i); play(v, videos) }, modifier = m, progress = progress[v.id] ?: 0f)
            }
        }
    }
}
