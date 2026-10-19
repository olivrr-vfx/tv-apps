package dev.purpletv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.core.Load
import dev.purpletv.core.items
import dev.purpletv.design.Accent
import dev.purpletv.design.AccentScope
import dev.purpletv.design.AppMark
import dev.purpletv.design.AppTile
import dev.purpletv.design.ContentRow
import dev.purpletv.design.EmptyState
import dev.purpletv.design.FocusMemory
import dev.purpletv.design.Palette
import dev.purpletv.design.RestorableFocus
import dev.purpletv.design.Tokens
import dev.purpletv.design.Type
import dev.purpletv.design.UtilityTile
import dev.purpletv.design.initialFocus
import dev.purpletv.twitch.ui.StreamCard
import dev.purpletv.twitch.ui.VodCard
import dev.purpletv.youtube.ui.VideoCard
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dashboardMemory = FocusMemory()

/** One item of the merged "Weiter schauen" row. */
private sealed interface ContinueItem {
    val updatedAt: Long
    data class Twitch(val entry: dev.purpletv.data.WatchHistory.Entry) : ContinueItem { override val updatedAt get() = entry.updatedAt }
    data class YouTube(val entry: dev.purpletv.youtube.YouTubeHistory.Entry) : ContinueItem { override val updatedAt get() = entry.updatedAt }
}

@Composable
fun Dashboard(app: PurpleApp, nav: Navigator, onPhoneRemote: () -> Unit) {
    val enabled by app.enabledAppsState.collectAsState()
    val twitchOn = AppId.TWITCH in enabled
    val youtubeOn = AppId.YOUTUBE in enabled
    LaunchedEffect(twitchOn, youtubeOn) {
        if (twitchOn) app.twitch.start()
        if (youtubeOn) app.youtube.start()
    }

    val followed by app.twitch.followed.collectAsState()
    val top by app.twitch.top.collectAsState()
    // Fixed set of shelves, so collecting them in a loop keeps a stable call order.
    val shelves = app.youtube.shelves.mapValues { it.value.collectAsState().value }
    val feed by app.youtube.feed.collectAsState()
    val subs by app.youtube.subscriptions.collectAsState()
    val twitchUser by app.twitch.loggedInName.collectAsState()
    val continueItems = remember {
        (app.history.continueWatching(10).map { ContinueItem.Twitch(it) } + app.youtube.history.continueWatching(10).map { ContinueItem.YouTube(it) })
            .sortedByDescending { it.updatedAt }
    }
    val memory = dashboardMemory

    Box(Modifier.fillMaxSize()) {
        // Ambient glow in the hub colours behind the header.
        Box(
            // Full height so the glow fades out instead of ending in a hard edge.
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Accent.Hub.deep.copy(alpha = 0.35f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(220f, 0f),
                    radius = 1100f,
                )
            )
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
            item("header") { Header() }

            item("apps") {
                ContentRow("Deine Apps") {
                    var first = true
                    enabled.forEach { appId ->
                        val isFirst = first
                        first = false
                        item(appId.key) {
                            when (appId) {
                                AppId.TWITCH -> AccentScope(Accent.Twitch) {
                                    AppTile(
                                        title = AppId.TWITCH.label,
                                        status = when (val f = followed) {
                                            is Load.Ok -> if (f.data.isEmpty()) "Niemand live" else "${f.data.size} Kanäle live"
                                            Load.NeedLogin -> "Nicht angemeldet"
                                            is Load.Error -> "Nicht erreichbar"
                                            Load.Loading -> "Lädt…"
                                        },
                                        accent = Accent.Twitch,
                                        onClick = { nav.showSection(Route.App(AppId.TWITCH)) },
                                        modifier = Modifier.width(Tokens.Layout.appTile).initialFocus(isFirst && memory.isEmpty),
                                        mark = { AppMark(AppId.TWITCH, 44.dp) },
                                    )
                                }
                                AppId.YOUTUBE -> AccentScope(Accent.YouTube) {
                                    val today = feed.items().count { System.currentTimeMillis() - it.uploadedMillis < 24 * 3600_000L }
                                    AppTile(
                                        title = "YouTube",
                                        status = when {
                                            subs.isEmpty() -> "Ohne Konto · Abos hinzufügen"
                                            today > 0 -> "$today neue Videos heute"
                                            else -> "${subs.size} Abos"
                                        },
                                        accent = Accent.YouTube,
                                        onClick = { nav.showSection(Route.App(AppId.YOUTUBE)) },
                                        modifier = Modifier.width(Tokens.Layout.appTile).initialFocus(isFirst && memory.isEmpty),
                                        mark = { AppMark(AppId.YOUTUBE, 44.dp) },
                                    )
                                }
                            }
                        }
                    }
                    item("phone") {
                        UtilityTile("Handy & Mac", "Steuern, teilen, Bildschirm zeigen", Icons.Filled.PhoneIphone, onClick = onPhoneRemote,
                            modifier = Modifier.width(Tokens.Layout.appTile))
                    }
                    item("settings") {
                        UtilityTile("Einstellungen", "Apps, Wiedergabe, Home Assistant", Icons.Filled.Settings, onClick = { nav.showSection(Route.Settings) },
                            modifier = Modifier.width(Tokens.Layout.appTile))
                    }
                }
            }

            if (enabled.isEmpty()) item("none") {
                EmptyState(
                    "Keine App aktiv",
                    body = "Schalte Twitch oder YouTube in den Einstellungen ein.",
                    icon = Icons.Filled.Apps,
                    modifier = Modifier.height(300.dp),
                )
            }

            val live = followed.items()
            if (twitchOn && live.isNotEmpty()) item("twitch-live") {
                AccentScope(Accent.Twitch) {
                    ContentRow("Live auf Twitch", trailing = twitchUser?.let { "als $it" }) {
                        itemsIndexed(live, key = { _, s -> s.login }) { i, s ->
                            RestorableFocus(memory, "live", i) { m ->
                                StreamCard(s, onClick = {
                                    memory.remember("live", i)
                                    nav.push(Route.TwitchLive(live, s.login, followed = true))
                                }, modifier = m.width(Tokens.Layout.cardWide), showApp = true)
                            }
                        }
                    }
                }
            }

            // Nobody followed is live (or no login): popular streams instead of an empty page.
            val popular = top.items().take(20)
            if (twitchOn && live.isEmpty() && popular.isNotEmpty()) item("twitch-top") {
                AccentScope(Accent.Twitch) {
                    ContentRow("Beliebt auf Twitch") {
                        itemsIndexed(popular, key = { _, s -> s.login }) { i, s ->
                            RestorableFocus(memory, "top", i) { m ->
                                StreamCard(s, onClick = {
                                    memory.remember("top", i)
                                    nav.push(Route.TwitchLive(popular, s.login))
                                }, modifier = m.width(Tokens.Layout.cardWide), showApp = true)
                            }
                        }
                    }
                }
            }

            val videos = feed.items()
            if (youtubeOn && videos.isEmpty()) {
                // No subscriptions yet: the first trending shelf that has loaded.
                val shelf = shelves.entries.sortedBy { it.key == "live" }.firstOrNull { (it.value as? Load.Ok)?.data?.isNotEmpty() == true }
                val trending = (shelf?.value as? Load.Ok)?.data.orEmpty().take(20)
                if (trending.isNotEmpty()) item("youtube-trending") {
                    AccentScope(Accent.YouTube) {
                        val title = dev.purpletv.youtube.api.YouTubeClient.shelves.firstOrNull { it.id == shelf?.key }?.title ?: "Trends"
                        ContentRow("YouTube: $title") {
                            itemsIndexed(trending, key = { _, v -> v.id }) { i, v ->
                                RestorableFocus(memory, "yt-trend", i) { m ->
                                    VideoCard(v, onClick = {
                                        memory.remember("yt-trend", i)
                                        nav.push(Route.YouTubeWatch(v, trending))
                                    }, modifier = m.width(Tokens.Layout.cardWide), showApp = true)
                                }
                            }
                        }
                    }
                }
            }
            if (youtubeOn && videos.isNotEmpty()) item("youtube-feed") {
                AccentScope(Accent.YouTube) {
                    ContentRow("Neu auf YouTube") {
                        itemsIndexed(videos.take(24), key = { _, v -> v.id }) { i, v ->
                            RestorableFocus(memory, "yt", i) { m ->
                                VideoCard(v, onClick = {
                                    memory.remember("yt", i)
                                    nav.push(Route.YouTubeWatch(v, videos))
                                }, modifier = m.width(Tokens.Layout.cardWide), showApp = true)
                            }
                        }
                    }
                }
            }

            val visibleContinue = continueItems.filter {
                (it is ContinueItem.Twitch && twitchOn) || (it is ContinueItem.YouTube && youtubeOn)
            }
            if (visibleContinue.isNotEmpty()) item("continue") {
                ContentRow("Weiter schauen") {
                    itemsIndexed(visibleContinue, key = { _, c -> c.key() }) { i, c ->
                        RestorableFocus(memory, "continue", i) { m ->
                            when (c) {
                                is ContinueItem.Twitch -> AccentScope(Accent.Twitch) {
                                    VodCard(c.entry.video, onClick = { memory.remember("continue", i); nav.push(Route.TwitchVod(c.entry.video)) },
                                        modifier = m.width(Tokens.Layout.cardWide), progress = c.entry.progress, showChannel = true, showApp = true)
                                }
                                is ContinueItem.YouTube -> AccentScope(Accent.YouTube) {
                                    VideoCard(c.entry.video, onClick = { memory.remember("continue", i); nav.push(Route.YouTubeWatch(c.entry.video)) },
                                        modifier = m.width(Tokens.Layout.cardWide), progress = c.entry.progress, showApp = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ContinueItem.key() = when (this) {
    is ContinueItem.Twitch -> "tw-${entry.video.id}"
    is ContinueItem.YouTube -> "yt-${entry.video.id}"
}

@Composable
private fun Header() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val hour = remember(now) { Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Hallo"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Layout.safeVertical + 8.dp, bottom = Tokens.Space.lg),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(greeting.uppercase(), color = Accent.Hub.light, style = Type.overline)
            Text(SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(now)), color = Palette.text, style = Type.display.copy(fontSize = Type.display.fontSize * 1.4f, lineHeight = Type.display.lineHeight * 1.3f))
            Text(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(Date(now)), color = Palette.textDim, style = Type.body)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dev.purpletv.core.HUB_NAME, color = Palette.textFaint, style = Type.label)
        }
    }
}
