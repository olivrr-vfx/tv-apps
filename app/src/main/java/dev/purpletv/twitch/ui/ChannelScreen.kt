package dev.purpletv.twitch.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.purpletv.core.Load
import dev.purpletv.design.Avatar
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.Chip
import dev.purpletv.design.LiveBadge
import dev.purpletv.design.LoadContent
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.Palette
import dev.purpletv.design.Thumbnail
import dev.purpletv.design.Tokens
import dev.purpletv.design.TvButton
import dev.purpletv.design.Type
import dev.purpletv.design.initialFocus
import dev.purpletv.hub.Navigator
import dev.purpletv.hub.Route
import dev.purpletv.twitch.api.formatUptime
import dev.purpletv.twitch.api.formatViewers

private enum class VideoFilter(val label: String) { ARCHIVE("Übertragungen"), HIGHLIGHTS("Highlights") }

@Composable
fun ChannelScreen(vm: TwitchViewModel, login: String, nav: Navigator) {
    LaunchedEffect(login) { if ((vm.channelPage.value as? Load.Ok)?.data?.info?.login != login) vm.openChannel(login) }
    LoadContent(
        vm.channelPage.collectAsState().value,
        onRetry = { vm.openChannel(login) },
        onLogin = { nav.push(Route.TwitchLogin) },
    ) { page -> ChannelContent(vm, page, nav) }
}

@Composable
private fun ChannelContent(vm: TwitchViewModel, page: ChannelPage, nav: Navigator) {
    val info = page.info
    var filter by remember { mutableStateOf(VideoFilter.ARCHIVE) }
    val videos = if (filter == VideoFilter.ARCHIVE) page.videos else page.highlights
    val history = vm.app.history
    // The live flag on the page is a snapshot; the repository knows if the channel went offline since.
    val followed by vm.repo.followed.collectAsState()
    val channels by vm.repo.channels.collectAsState()
    val live = page.live?.takeIf { stream ->
        val isFollowedChannel = (channels as? Load.Ok)?.data?.any { it.login == stream.login } == true
        val stillLive = (followed as? Load.Ok)?.data?.any { it.login == stream.login }
        !isFollowedChannel || stillLive != false
    }

    Box(Modifier.fillMaxSize()) {
        // Banner: the stream preview when live, otherwise the channel's offline image.
        val banner = live?.thumbnail(1280, 720) ?: info?.offlineImageUrl.orEmpty()
        if (banner.isNotEmpty()) Thumbnail(banner, Modifier.fillMaxWidth().height(380.dp))
        Box(Modifier.fillMaxWidth().height(380.dp).background(Brush.verticalGradient(0f to Palette.canvas.copy(alpha = 0.5f), 1f to Palette.canvas)))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Layout.gridGap),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(info?.avatarUrl.orEmpty(), 104.dp, live = live != null)
                        Spacer(Modifier.width(Tokens.Space.xl))
                        Column(Modifier.weight(1f)) {
                            Text("TWITCH · KANAL", color = LocalAccent.current.light, style = Type.overline)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(info?.displayName ?: "", color = Color.White, style = Type.display)
                                if (live != null) {
                                    Spacer(Modifier.width(Tokens.Space.md))
                                    LiveBadge()
                                }
                            }
                            if (live != null) {
                                Text(live.title, color = Palette.text, style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(live.gameName.ifBlank { null }, "${formatViewers(live.viewers)} Zuschauer", formatUptime(live.startedAtMillis()).ifBlank { null })
                                        .joinToString("  ·  "),
                                    color = LocalAccent.current.light,
                                    style = Type.label,
                                )
                            } else if (!info?.description.isNullOrBlank()) {
                                Text(info?.description.orEmpty(), color = Palette.textDim, style = Type.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(Tokens.Space.xl))
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.md), verticalAlignment = Alignment.CenterVertically) {
                        if (live != null) {
                            TvButton(
                                "Live ansehen",
                                onClick = { nav.push(Route.TwitchLive(listOf(live), live.login)) },
                                style = ButtonStyle.Primary,
                                icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.initialFocus(key = page),
                            )
                        }
                        VideoFilter.entries.forEach { f ->
                            Chip(
                                "${f.label} (${if (f == VideoFilter.ARCHIVE) page.videos.size else page.highlights.size})",
                                selected = f == filter,
                                onClick = { filter = f },
                                icon = if (f == VideoFilter.ARCHIVE) Icons.Filled.VideoLibrary else null,
                                modifier = if (live == null && f == VideoFilter.ARCHIVE) Modifier.initialFocus(key = page) else Modifier,
                            )
                        }
                    }
                    if (videos.isEmpty()) {
                        Text(
                            if (filter == VideoFilter.ARCHIVE) "Keine gespeicherten Übertragungen." else "Keine Highlights.",
                            modifier = Modifier.padding(vertical = 30.dp),
                            color = Palette.textDim,
                            style = Type.body,
                        )
                    }
                }
            }
            itemsIndexed(videos, key = { _, v -> v.id }) { _, video ->
                val progress = remember(video.id) {
                    history.position(video.id)?.let { pos -> (pos / 1000f / video.durationSec.coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
                }
                VodCard(video, onClick = { nav.push(Route.TwitchVod(video)) }, progress = progress)
            }
        }
    }
}
