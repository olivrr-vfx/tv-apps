package dev.purpletv.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo
import dev.purpletv.twitch.formatUptime
import dev.purpletv.twitch.formatViewers
import kotlinx.coroutines.delay

@Composable
fun ChannelScreen(vm: HomeViewModel, login: String, onPlayLive: (StreamInfo) -> Unit, onVod: (VideoInfo) -> Unit, onLogin: () -> Unit) {
    LaunchedEffect(login) { if ((vm.channelPage.value as? Load.Ok)?.data?.info?.login != login) vm.openChannel(login) }
    when (val state = vm.channelPage.collectAsState().value) {
        Load.Loading -> Centered { CircularProgressIndicator(color = Colors.Purple) }
        Load.NeedLogin -> LoginPrompt(onLogin)
        is Load.Error -> Centered { Text("Fehler: ${state.message}", color = Colors.TextDim) }
        is Load.Ok -> ChannelContent(vm, state.data, onPlayLive, onVod)
    }
}

private enum class VideoFilter(val label: String) { ARCHIVE("Übertragungen"), HIGHLIGHTS("Highlights") }

@Composable
private fun ChannelContent(vm: HomeViewModel, page: ChannelPage, onPlayLive: (StreamInfo) -> Unit, onVod: (VideoInfo) -> Unit) {
    val info = page.info
    var filter by remember { mutableStateOf(VideoFilter.ARCHIVE) }
    val videos = if (filter == VideoFilter.ARCHIVE) page.videos else page.highlights
    val firstFocus = remember { FocusRequester() }
    val history = remember(page) { vm.app.history }

    LaunchedEffect(page) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        // Banner: the stream preview when live, otherwise the channel's offline image.
        val banner = page.live?.thumbnail(1280, 720) ?: info?.offlineImageUrl.orEmpty()
        if (banner.isNotEmpty()) {
            AsyncImage(banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(360.dp))
        }
        Box(Modifier.fillMaxWidth().height(360.dp).background(Brush.verticalGradient(0f to Colors.Background.copy(alpha = 0.55f), 1f to Colors.Background)))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(info?.avatarUrl.orEmpty(), 96.dp, live = page.live != null)
                        Spacer(Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(info?.displayName ?: "", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                                if (page.live != null) {
                                    Spacer(Modifier.width(12.dp))
                                    LiveBadge()
                                }
                            }
                            val live = page.live
                            if (live != null) {
                                Text(live.title, color = Colors.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(live.gameName.ifBlank { null }, "${formatViewers(live.viewers)} Zuschauer", formatUptime(live.startedAtMillis()).ifBlank { null })
                                        .joinToString("  ·  "),
                                    color = Colors.PurpleLight,
                                    fontSize = 13.sp,
                                )
                            } else if (!info?.description.isNullOrBlank()) {
                                Text(info?.description.orEmpty(), color = Colors.TextDim, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val live = page.live
                        if (live != null) {
                            PillButton("Live ansehen", onClick = { onPlayLive(live) }, selected = true, icon = Icons.Filled.PlayArrow, modifier = Modifier.focusRequester(firstFocus))
                        }
                        VideoFilter.entries.forEach { f ->
                            PillButton(
                                "${f.label} (${if (f == VideoFilter.ARCHIVE) page.videos.size else page.highlights.size})",
                                onClick = { filter = f },
                                selected = f == filter,
                                icon = if (f == VideoFilter.ARCHIVE) Icons.Filled.VideoLibrary else null,
                                modifier = if (live == null && f == VideoFilter.ARCHIVE) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (videos.isEmpty()) {
                        Text(
                            if (filter == VideoFilter.ARCHIVE) "Keine gespeicherten Übertragungen." else "Keine Highlights.",
                            modifier = Modifier.padding(vertical = 30.dp),
                            color = Colors.TextDim,
                        )
                    }
                }
            }
            itemsIndexed(videos, key = { _, v -> v.id }) { _, video ->
                val progress = remember(video.id) {
                    history.position(video.id)?.let { pos -> (pos / 1000f / video.durationSec.coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
                }
                VideoCard(video, onClick = { onVod(video) }, progress = progress)
            }
        }
    }
}
