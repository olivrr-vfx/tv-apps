package dev.purpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.purpletv.twitch.ChannelInfo
import dev.purpletv.twitch.Game
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.VideoInfo
import dev.purpletv.twitch.formatAgo
import dev.purpletv.twitch.formatDuration
import dev.purpletv.twitch.formatUptime
import dev.purpletv.twitch.formatViewers

private val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))

@Composable
fun StreamCard(stream: StreamInfo, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick = onClick, modifier = modifier, background = Colors.Surface, focusedBackground = Colors.SurfaceHigh, onFocus = onFocus) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Colors.SurfaceHigh)) {
                if (stream.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(stream.thumbnail(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Avatar(stream.avatarUrl, 64.dp, Modifier.align(Alignment.Center))
                }
                Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter).background(bottomShade))
                if (stream.isLive) {
                    LiveBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
                    if (stream.viewers > 0) Badge("● ${formatViewers(stream.viewers)}", Modifier.align(Alignment.BottomStart).padding(8.dp))
                    formatUptime(stream.startedAtMillis()).takeIf { it.isNotEmpty() }?.let {
                        Badge(it, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                } else {
                    Badge("Offline", Modifier.align(Alignment.TopStart).padding(8.dp))
                }
            }
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (stream.avatarUrl.isNotEmpty() && stream.thumbnailUrl.isNotEmpty()) {
                    Avatar(stream.avatarUrl, 30.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(stream.displayName, color = Colors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stream.title.ifBlank { " " },
                        color = if (focused) Colors.Text else Colors.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stream.gameName.ifBlank { " " }, color = Colors.PurpleLight, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    video: VideoInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showChannel: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    Focusable(onClick = onClick, modifier = modifier, background = Colors.Surface, focusedBackground = Colors.SurfaceHigh, onFocus = onFocus) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Colors.SurfaceHigh)) {
                AsyncImage(video.thumbnail(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter).background(bottomShade))
                Badge(formatDuration(video.durationSec), Modifier.align(Alignment.BottomEnd).padding(8.dp))
                formatAgo(video.createdAtMillis()).takeIf { it.isNotEmpty() }?.let {
                    Badge(it, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                if (video.type == "highlight") Badge("Highlight", Modifier.align(Alignment.TopEnd).padding(8.dp))
                if (progress > 0f) {
                    Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Colors.Purple))
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    video.title.ifBlank { "Ohne Titel" },
                    color = Colors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    video.userName.takeIf { showChannel && it.isNotBlank() },
                    if (video.viewCount > 0) "${formatViewers(video.viewCount)} Aufrufe" else null,
                ).joinToString(" · ")
                Text(sub.ifBlank { " " }, color = if (focused) Colors.PurpleLight else Colors.TextDim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ChannelCard(channel: ChannelInfo, live: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), onFocus = onFocus) { focused ->
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(channel.avatarUrl, 84.dp, live = live)
            Spacer(Modifier.height(8.dp))
            Text(
                channel.displayName,
                color = if (focused) Color.White else Colors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(if (live) "LIVE" else " ", color = Colors.Live, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick = onClick, modifier = modifier, background = Colors.Surface, onFocus = onFocus) { focused ->
        Column {
            AsyncImage(
                model = game.boxArt(285, 380),
                contentDescription = game.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Colors.SurfaceHigh),
            )
            Text(
                game.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = if (focused) Color.White else Colors.TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
