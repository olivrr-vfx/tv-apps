package dev.purpletv.design

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.purpletv.player.BasePlayer
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerStats
import kotlinx.coroutines.delay
import java.util.Locale

/** Shared player chrome. Every player screen (Twitch live, VOD, YouTube) is built from these. */

@Composable
fun VideoSurface(player: BasePlayer, modifier: Modifier = Modifier) {
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
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.94f))))
                .padding(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = 80.dp, bottom = Tokens.Layout.safeVertical)
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

/** Right-hand settings panel, the player menu. */
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
                .width(380.dp)
                .background(Brush.horizontalGradient(listOf(Palette.canvas.copy(alpha = 0.9f), Palette.canvas.copy(alpha = 0.98f))))
                .padding(horizontal = Tokens.Space.xl, vertical = 36.dp)
        ) {
            Text(title, color = Color.White, style = Type.title, modifier = Modifier.padding(start = Tokens.Space.sm, bottom = Tokens.Space.lg))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows.size) { i ->
                    PanelItem(rows[i], if (i == 0) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun PanelItem(row: PanelRow, modifier: Modifier) {
    val accent = LocalAccent.current
    Focusable(
        onClick = row.onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        focusedScale = 1.02f,
        background = Color.Transparent,
        focusedBackground = Palette.focus,
        ring = false,
    ) { focused ->
        val fg = if (focused) Palette.onFocus else Color.White
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.checked != null) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    if (row.checked) Icon(Icons.Filled.Check, null, tint = if (focused) accent.deep else accent.light, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(row.label, color = fg, style = Type.label.copy(fontSize = Type.body.fontSize), modifier = Modifier.weight(1f))
            if (row.value != null) {
                Text(row.value, color = if (focused) accent.deep else accent.light, style = Type.label)
            }
            if (row.submenu) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = fg, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun StatusOverlay(state: PlaybackState, modifier: Modifier, offlineText: String = "Dieser Kanal ist gerade offline.") {
    when (state) {
        PlaybackState.Loading, PlaybackState.Buffering ->
            CircularProgressIndicator(color = LocalAccent.current.primary, modifier = modifier.size(56.dp), strokeWidth = 4.dp)
        PlaybackState.Offline -> PlayerMessage(modifier, offlineText, "OK drücken zum erneut Prüfen")
        is PlaybackState.Error -> PlayerMessage(modifier, state.message, "OK drücken zum erneut Versuchen")
        PlaybackState.Ended -> PlayerMessage(modifier, "Video zu Ende", "Zurück zur Übersicht mit der Zurück-Taste")
        PlaybackState.Playing -> Unit
    }
}

@Composable
fun PlayerMessage(modifier: Modifier, text: String, hint: String) {
    Column(
        modifier.clip(RoundedCornerShape(Tokens.Radius.lg)).background(Palette.scrim).padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, style = Type.title)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = Palette.textDim, style = Type.bodySmall)
    }
}

@Composable
fun StatsBox(player: BasePlayer, modifier: Modifier, extra: () -> List<String> = { emptyList() }) {
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
    ) + extra()
    Column(modifier.clip(RoundedCornerShape(Tokens.Radius.md)).background(Palette.scrim).padding(14.dp)) {
        lines.forEach { Text(it, color = Color.White, style = Type.caption.copy(fontFamily = FontFamily.Monospace)) }
    }
}

/** Progress bar for videos. When focused, left/right seek; holding the key speeds up. */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    pendingMs: Long?,
    onSeekStep: (forward: Boolean, repeat: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
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
            Text(formatClock(shown / 1000), color = Color.White, style = Type.label)
            Spacer(Modifier.weight(1f))
            Text(formatClock(durationMs / 1000), color = Palette.textDim, style = Type.label)
        }
        Spacer(Modifier.height(Tokens.Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 5.dp)
                .clip(RoundedCornerShape(Tokens.Radius.pill))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent.gradient))
        }
        if (focused) {
            Spacer(Modifier.height(6.dp))
            Text("◀ ▶ spulen  ·  gedrückt halten für schneller", color = Palette.textDim, style = Type.caption)
        }
    }
}

/** Big "+30 s" bubble while seeking without the controls open. */
@Composable
fun SeekBubble(deltaMs: Long, targetMs: Long, durationMs: Long, modifier: Modifier) {
    val sign = if (deltaMs >= 0) "+" else "−"
    Column(
        modifier.clip(RoundedCornerShape(Tokens.Radius.lg)).background(Palette.scrim).padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$sign${formatClock(kotlin.math.abs(deltaMs) / 1000)}", color = Color.White, style = Type.headline)
        Text("${formatClock(targetMs / 1000)} / ${formatClock(durationMs / 1000)}", color = Palette.textDim, style = Type.bodySmall)
    }
}

/** Seek step that grows while a key is held down. */
fun seekStepMs(repeat: Int): Long = when {
    repeat < 4 -> 10_000L
    repeat < 12 -> 30_000L
    repeat < 30 -> 60_000L
    else -> 180_000L
}

fun formatClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
