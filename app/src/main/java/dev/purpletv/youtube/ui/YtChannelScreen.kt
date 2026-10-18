package dev.purpletv.youtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.purpletv.design.Avatar
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.LoadContent
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.Palette
import dev.purpletv.design.SectionHeader
import dev.purpletv.design.Thumbnail
import dev.purpletv.design.Tokens
import dev.purpletv.design.TvButton
import dev.purpletv.design.Type
import dev.purpletv.design.initialFocus
import dev.purpletv.hub.Navigator
import dev.purpletv.hub.Route
import dev.purpletv.youtube.api.YtChannelPage
import dev.purpletv.youtube.api.formatCount

@Composable
fun YtChannelScreen(vm: YouTubeViewModel, url: String, nav: Navigator) {
    LaunchedEffect(url) { vm.openChannel(url) }
    LoadContent(vm.channel.collectAsState().value, onRetry = { vm.openChannel(url, force = true) }) { page ->
        ChannelContent(vm, page, nav)
    }
}

@Composable
private fun ChannelContent(vm: YouTubeViewModel, page: YtChannelPage, nav: Navigator) {
    val ch = page.channel
    val subs by vm.repo.subscriptions.collectAsState()
    val subscribed = subs.any { it.id == ch.id }
    val all = page.live + page.videos

    Box(Modifier.fillMaxSize()) {
        if (ch.banner.isNotEmpty()) Thumbnail(ch.banner, Modifier.fillMaxWidth().height(300.dp))
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0f to Palette.canvas.copy(alpha = 0.45f), 1f to Palette.canvas)))
        VideoGrid(page.videos, vm.focusMemory, "channel-${ch.id}", play = { v, q -> nav.push(Route.YouTubeWatch(v, q)) }) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(Tokens.Space.xxl))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ch.avatar, 104.dp)
                        Spacer(Modifier.width(Tokens.Space.xl))
                        Column(Modifier.weight(1f)) {
                            Text("YOUTUBE · KANAL", color = LocalAccent.current.light, style = Type.overline)
                            Text(ch.name, color = Color.White, style = Type.display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ch.subscribers > 0) Text("${formatCount(ch.subscribers)} Abonnenten", color = Palette.textDim, style = Type.body)
                        }
                    }
                    Spacer(Modifier.height(Tokens.Space.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.md)) {
                        val live = page.live.firstOrNull()
                        if (live != null) {
                            TvButton("Live ansehen", onClick = { nav.push(Route.YouTubeWatch(live, all)) }, style = ButtonStyle.Primary, icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.initialFocus(key = ch.id))
                        }
                        TvButton(
                            if (subscribed) "Abonniert" else "Abonnieren",
                            onClick = { if (subscribed) vm.repo.unsubscribe(ch.id) else vm.repo.subscribe(ch) },
                            style = if (subscribed) ButtonStyle.Secondary else ButtonStyle.Primary,
                            icon = if (subscribed) Icons.Filled.Check else Icons.Filled.NotificationsActive,
                            modifier = if (live == null) Modifier.initialFocus(key = ch.id) else Modifier,
                        )
                        page.videos.firstOrNull()?.let { newest ->
                            TvButton("Neuestes Video", onClick = { nav.push(Route.YouTubeWatch(newest, page.videos)) }, icon = Icons.Filled.PlayArrow)
                        }
                    }
                    if (ch.description.isNotBlank()) {
                        Spacer(Modifier.height(Tokens.Space.lg))
                        Text(ch.description, color = Palette.textDim, style = Type.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(900.dp))
                    }
                    Spacer(Modifier.height(Tokens.Space.xl))
                    SectionHeader("Videos", trailing = "${page.videos.size}")
                }
            }
        }
    }
}
