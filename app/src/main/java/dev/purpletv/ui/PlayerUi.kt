package dev.purpletv.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.purpletv.player.AdStatus
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerStats
import dev.purpletv.player.StreamPlayer
import dev.purpletv.twitch.formatDuration
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun VideoSurface(player: StreamPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                isFocusable = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player.player
            }
        },
        modifier = modifier,
    )
}

/** Bottom control area: gradient, an info header and a row of controls. */
@Composable
fun BoxScope.ControlsOverlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.92f))))
                .padding(start = 48.dp, end = 48.dp, top = 80.dp, bottom = 24.dp)
        ) { content() }
    }
}

/** Darkens the top of the picture while the controls are shown. */
@Composable
fun BoxScope.TopShade(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
    }
}

data class PanelRow(val label: String, val value: String? = null, val checked: Boolean? = null, val submenu: Boolean = false, val onClick: () -> Unit)

/** Right-hand settings panel, the "normal" player menu. */
@Composable
fun BoxScope.SidePanel(visible: Boolean, title: String, rows: List<PanelRow>) {
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(title) {
            delay(60)
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .fillMaxHeight()
                .width(360.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xE6101014), Color(0xFA101014))))
                .padding(horizontal = 20.dp, vertical = 36.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows.size) { i ->
                    val row = rows[i]
                    PanelItem(row, if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun PanelItem(row: PanelRow, modifier: Modifier) {
    Focusable(
        onClick = row.onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.02f,
        background = Color.Transparent,
        focusedBackground = Color.White,
        glow = false,
    ) { focused ->
        val fg = if (focused) Color.Black else Color.White
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.checked != null) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (row.checked) Icon(Icons.Filled.Check, null, tint = if (focused) Colors.PurpleDeep else Colors.PurpleLight, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(row.label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (row.value != null) {
                Text(row.value, color = if (focused) Colors.PurpleDeep else Colors.PurpleLight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            if (row.submenu) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = fg, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun StatusOverlay(state: PlaybackState, modifier: Modifier) {
    when (state) {
        PlaybackState.Loading, PlaybackState.Buffering -> CircularProgressIndicator(color = Colors.Purple, modifier = modifier.size(56.dp), strokeWidth = 4.dp)
        PlaybackState.Offline -> Message(modifier, "Dieser Kanal ist gerade offline.", "OK drücken zum erneut Prüfen")
        is PlaybackState.Error -> Message(modifier, state.message, "OK drücken zum erneut Versuchen")
        PlaybackState.Ended -> Message(modifier, "Video zu Ende", "Zurück zur Übersicht mit der Zurück-Taste")
        PlaybackState.Playing -> Unit
    }
}

@Composable
private fun Message(modifier: Modifier, text: String, hint: String) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = Colors.TextDim, fontSize = 13.sp)
    }
}

@Composable
fun AdBadge(status: AdStatus, modifier: Modifier) {
    val text = when (status) {
        AdStatus.NONE -> return
        AdStatus.BLOCKED_BACKUP -> "Werbung geblockt"
        AdStatus.BLOCKED_STRIP -> "Werbung wird übersprungen…"
        AdStatus.SHOWING -> "Werbung läuft – nicht blockbar"
    }
    val color = if (status == AdStatus.SHOWING) Color(0xFFB00020) else Colors.Purple
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatsBox(player: StreamPlayer, adStatus: AdStatus, modifier: Modifier) {
    var stats by remember { mutableStateOf(PlayerStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = player.stats()
            delay(1_000)
        }
    }
    val lines = listOfNotNull(
        "Auflösung  ${stats.resolution}",
        "Bitrate    ${stats.bitrateKbps} kbit/s",
        "Bandbreite ${String.format(Locale.US, "%.1f", stats.bandwidthMbps)} Mbit/s",
        "Puffer     ${String.format(Locale.US, "%.1f", stats.bufferSec)} s",
        stats.latencySec?.let { "Latenz     ${String.format(Locale.US, "%.1f s", it)}" },
        "Verworfen  ${stats.droppedFrames} Frames",
        "Werbung    ${adStatus.name.lowercase()}",
    )
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(14.dp)) {
        lines.forEach { Text(it, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    }
}

/**
 * Progress bar for VODs. When focused, left/right seek; holding the key speeds up.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    pendingMs: Long?,
    onSeekStep: (forward: Boolean, repeat: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shown = pendingMs ?: positionMs
    val fraction = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onSeekStep(false, ev.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight -> { onSeekStep(true, ev.nativeKeyEvent.repeatCount); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(shown / 1000), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs / 1000), color = Colors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Colors.purpleGradient))
        }
        if (focused) {
            Spacer(Modifier.height(6.dp))
            Text("◀ ▶ spulen  ·  gedrückt halten für schneller", color = Colors.TextDim, fontSize = 11.sp)
        }
    }
}

/** Big "+30 s" bubble while seeking without the controls open. */
@Composable
fun SeekBubble(deltaMs: Long, targetMs: Long, durationMs: Long, modifier: Modifier) {
    val sign = if (deltaMs >= 0) "+" else "−"
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$sign${formatDuration(kotlin.math.abs(deltaMs) / 1000)}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text("${formatDuration(targetMs / 1000)} / ${formatDuration(durationMs / 1000)}", color = Colors.TextDim, fontSize = 13.sp)
    }
}

/** Seek step that grows while a key is held down. */
fun seekStepMs(repeat: Int): Long = when {
    repeat < 4 -> 10_000L
    repeat < 12 -> 30_000L
    repeat < 30 -> 60_000L
    else -> 180_000L
}
