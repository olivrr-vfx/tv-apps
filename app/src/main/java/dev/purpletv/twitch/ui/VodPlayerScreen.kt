package dev.purpletv.twitch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.design.ControlsOverlay
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.PanelRow
import dev.purpletv.design.Palette
import dev.purpletv.design.RoundIconButton
import dev.purpletv.design.SeekBar
import dev.purpletv.design.SeekBubble
import dev.purpletv.design.SidePanel
import dev.purpletv.design.StatsBox
import dev.purpletv.design.StatusOverlay
import dev.purpletv.design.Tokens
import dev.purpletv.design.TopShade
import dev.purpletv.design.Type
import dev.purpletv.design.VideoSurface
import dev.purpletv.design.seekStepMs
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.remote.RemoteControl
import dev.purpletv.twitch.api.VideoInfo
import dev.purpletv.twitch.api.formatAgo
import dev.purpletv.twitch.api.formatViewers
import dev.purpletv.twitch.player.StreamPlayer
import kotlinx.coroutines.delay

@Composable
fun VodPlayerScreen(app: PurpleApp, video: VideoInfo, onExit: () -> Unit, onChannel: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember { StreamPlayer(context, app.settings, app.gql, PlayerMode.VOD) }
    val state by player.state.collectAsState()
    val qualities by player.qualities.collectAsState()
    val selectedQuality by player.selectedQuality.collectAsState()

    var controls by remember { mutableStateOf(true) }
    var panelQuality by remember { mutableStateOf<Boolean?>(null) } // null = closed, false = main, true = quality
    var showStats by remember { mutableStateOf(app.settings.showStats) }
    var paused by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(video.durationSec * 1000) }
    // Accumulated seek target while the user is pressing left/right.
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var seekStart by remember { mutableLongStateOf(0L) }
    var seekStamp by remember { mutableLongStateOf(0L) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun saveProgress() {
        val d = player.durationMs.takeIf { it > 0 } ?: duration
        app.history.save(video, player.positionMs, d)
    }

    LaunchedEffect(Unit) { player.playVod(video.id, app.history.position(video.id) ?: 0L) }
    DisposableEffect(Unit) {
        onDispose {
            saveProgress()
            player.release()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveProgress()
                player.pause()
                paused = true
                controls = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Position ticker + periodic progress saving.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            position = player.positionMs
            player.durationMs.takeIf { it > 0 }?.let { duration = it }
            if (++ticks % 20 == 0 && state == PlaybackState.Playing) saveProgress()
            delay(500)
        }
    }

    // Perform the accumulated seek shortly after the last key press.
    LaunchedEffect(seekStamp) {
        val target = pendingSeek ?: return@LaunchedEffect
        delay(650)
        player.seekTo(target)
        position = target
        pendingSeek = null
    }

    fun seekStep(forward: Boolean, repeat: Int) {
        val base = pendingSeek ?: player.positionMs.also { seekStart = it }
        val step = seekStepMs(repeat)
        pendingSeek = (base + if (forward) step else -step).coerceIn(0, (duration - 1000).coerceAtLeast(0))
        seekStamp = System.nanoTime()
    }

    LaunchedEffect(controls, panelQuality, lastInput, paused) {
        if ((controls || panelQuality != null) && !paused) {
            delay(if (panelQuality != null) 12_000 else 6_000)
            controls = false
            panelQuality = null
        }
        if (!controls && panelQuality == null) runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(controls) {
        if (controls && panelQuality == null) {
            delay(50)
            runCatching { playFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            panelQuality == true -> panelQuality = false
            panelQuality == false -> panelQuality = null
            controls && !paused -> controls = false
            else -> onExit()
        }
    }

    fun togglePause() {
        if (paused) player.resume() else player.pause()
        paused = !paused
    }
    fun toggleStats() {
        showStats = !showStats
        app.settings.showStats = showStats
    }

    // Home Assistant: commands in, current state out.
    LaunchedEffect(Unit) {
        RemoteControl.commands.collect { c ->
            when (c) {
                RemoteControl.Command.Play -> if (paused) togglePause()
                RemoteControl.Command.Pause -> if (!paused) togglePause()
                RemoteControl.Command.Toggle -> togglePause()
                is RemoteControl.Command.Mute -> player.setMuted(c.mute)
                is RemoteControl.Command.Volume -> player.setVolume(c.level)
                RemoteControl.Command.Next -> player.seekTo(player.positionMs + 30_000)
                RemoteControl.Command.Previous -> player.seekTo(player.positionMs - 30_000)
                is RemoteControl.Command.Seek -> player.seekTo(c.positionSec * 1000)
                is RemoteControl.Command.SeekBy -> player.seekTo(player.positionMs + c.deltaSec * 1000)
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            RemoteControl.nowPlaying = RemoteControl.NowPlaying(
                app = AppId.TWITCH,
                kind = "vod",
                id = video.id,
                title = video.title,
                channel = video.userName,
                channelId = video.login,
                thumbnail = video.thumbnail(640, 360),
                durationSec = duration / 1000,
                positionSec = player.positionMs / 1000,
            )
            RemoteControl.paused = paused
            RemoteControl.muted = player.muted
            RemoteControl.volume = player.volume
            delay(1_000)
        }
    }
    DisposableEffect(Unit) { onDispose { RemoteControl.clearPlayer() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = System.currentTimeMillis()
                when (ev.key) {
                    Key.Menu -> {
                        panelQuality = if (panelQuality != null) null else false
                        controls = false
                        return@onPreviewKeyEvent true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePause(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { seekStep(true, 4); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { seekStep(false, 4); return@onPreviewKeyEvent true }
                    else -> Unit
                }
                if (controls || panelQuality != null) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { seekStep(false, ev.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight -> { seekStep(true, ev.nativeKeyEvent.repeatCount); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                        if (state is PlaybackState.Error) player.reload() else controls = true
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        VideoSurface(player, Modifier.fillMaxSize())
        StatusOverlay(state, Modifier.align(Alignment.Center))
        TopShade(controls)
        if (showStats) StatsBox(player, Modifier.align(Alignment.TopEnd).padding(20.dp))

        val pending = pendingSeek
        if (pending != null && !controls) {
            SeekBubble(pending - seekStart, pending, duration, Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Tokens.Layout.safeHorizontal, vertical = Tokens.Layout.safeVertical)) {
                SeekBar(position, duration, pending, onSeekStep = { _, _ -> })
            }
        }

        ControlsOverlay(controls) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.userName, color = LocalAccent.current.light, style = Type.titleSmall)
                    Text(video.title, color = Color.White, style = Type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            formatAgo(video.createdAtMillis()).ifBlank { null },
                            if (video.viewCount > 0) "${formatViewers(video.viewCount)} Aufrufe" else null,
                        ).joinToString("  ·  "),
                        color = Palette.textDim,
                        style = Type.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SeekBar(position, duration, pendingSeek, onSeekStep = { fwd, rep -> seekStep(fwd, rep) })
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RoundIconButton(Icons.Filled.Replay30, "−30 s", onClick = { player.seekTo(player.positionMs - 30_000) })
                RoundIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Abspielen" else "Pause",
                    onClick = { togglePause() },
                    modifier = Modifier.focusRequester(playFocus),
                    highlighted = true,
                )
                RoundIconButton(Icons.Filled.Forward30, "+30 s", onClick = { player.seekTo(player.positionMs + 30_000) })
                Spacer(Modifier.width(18.dp))
                RoundIconButton(Icons.Filled.VideoLibrary, "Kanal", onClick = { onChannel(video.login) })
                RoundIconButton(Icons.Filled.Tune, "Einstellungen", onClick = { controls = false; panelQuality = false })
                RoundIconButton(Icons.Filled.Info, if (showStats) "Statistiken aus" else "Statistiken", onClick = { toggleStats() })
            }
        }

        SidePanel(
            visible = panelQuality != null,
            title = if (panelQuality == true) "Qualität" else "Einstellungen",
            rows = if (panelQuality == true) {
                listOf(PanelRow("Auto", checked = selectedQuality == null) { player.selectQuality(null); panelQuality = false }) +
                    qualities.map { q -> PanelRow(q.label, checked = selectedQuality == q.label) { player.selectQuality(q); panelQuality = false } }
            } else {
                listOf(
                    PanelRow("Qualität", value = selectedQuality ?: "Auto (${player.currentQualityLabel()})", submenu = true) { panelQuality = true },
                    PanelRow("Statistiken", checked = showStats) { toggleStats() },
                    PanelRow("Von vorne abspielen") { player.seekTo(0); panelQuality = null },
                    PanelRow("Zum Kanal", submenu = true) { onChannel(video.login) },
                )
            },
        )
    }
}
