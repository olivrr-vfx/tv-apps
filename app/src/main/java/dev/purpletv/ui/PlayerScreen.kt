package dev.purpletv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.purpletv.PurpleApp
import dev.purpletv.chat.ChatClient
import dev.purpletv.data.ChatMode
import dev.purpletv.player.PlaybackState
import dev.purpletv.remote.RemoteControl
import dev.purpletv.player.StreamPlayer
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.formatUptime
import dev.purpletv.twitch.formatViewers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Panel { MAIN, QUALITY }

@Composable
fun PlayerScreen(
    app: PurpleApp,
    channels: List<StreamInfo>,
    startIndex: Int,
    onExit: () -> Unit,
    onChannel: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = app.settings
    val player = remember { StreamPlayer(context, settings, app.gql) }
    val chat = remember { ChatClient(settings.thirdPartyEmotes) }

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    var info by remember { mutableStateOf(channels.getOrNull(index)) }
    var chatMode by remember { mutableStateOf(settings.chatMode) }
    var chatFont by remember { mutableIntStateOf(settings.chatFontSize) }
    var showStats by remember { mutableStateOf(settings.showStats) }
    var controls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var paused by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var zapBanner by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val state by player.state.collectAsState()
    val adStatus by player.adEngine.status.collectAsState()
    val qualities by player.qualities.collectAsState()
    val selectedQuality by player.selectedQuality.collectAsState()
    val messages by chat.messages.collectAsState()

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose {
            chat.release()
            player.release()
        }
    }

    // Pause when the TV goes to the home screen, jump back to live when returning.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stopped = true
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (stopped) {
                    stopped = false
                    player.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // (Re)start playback and chat whenever the channel changes.
    LaunchedEffect(index) {
        val current = channels.getOrNull(index) ?: return@LaunchedEffect
        info = current
        paused = false
        zapBanner = System.currentTimeMillis()
        player.play(current.login)
        // Opened by name (deep link / Home Assistant): fetch title, game and avatar right away.
        if (current.title.isBlank() && app.auth.isLoggedIn) {
            withContext(Dispatchers.IO) { runCatching { app.helix.stream(current.login) }.getOrNull() }?.let { info = it }
        }
        val id = (info?.userId ?: current.userId) ?: withContext(Dispatchers.IO) { runCatching { app.gql.user(current.login)?.first }.getOrNull() }
        chat.connect(current.login, id)
    }

    // Refresh title / viewer count once a minute.
    LaunchedEffect(index) {
        while (true) {
            delay(60_000)
            val login = channels.getOrNull(index)?.login ?: break
            withContext(Dispatchers.IO) { runCatching { app.helix.stream(login) }.getOrNull() }
                ?.let { fresh -> info = fresh.copy(avatarUrl = fresh.avatarUrl.ifEmpty { info?.avatarUrl.orEmpty() }) }
        }
    }

    // Auto-hide controls and panel; give focus back to the video afterwards.
    LaunchedEffect(controls, panel, lastInput) {
        if (controls || panel != null) {
            delay(if (panel != null) 12_000 else 7_000)
            controls = false
            panel = null
        }
        if (!controls && panel == null) runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(controls) {
        if (controls && panel == null) {
            delay(50)
            runCatching { playFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            panel == Panel.QUALITY -> panel = Panel.MAIN
            panel != null -> panel = null
            controls -> controls = false
            else -> onExit()
        }
    }

    fun zap(delta: Int) {
        if (channels.size < 2) return
        index = (index + delta + channels.size) % channels.size
    }
    fun cycleChat() {
        chatMode = chatMode.next()
        settings.chatMode = chatMode
    }
    fun toggleStats() {
        showStats = !showStats
        settings.showStats = showStats
    }
    fun togglePause() {
        if (paused) player.resume() else player.pause()
        paused = !paused
    }
    fun openPanel() {
        controls = false
        panel = Panel.MAIN
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
                RemoteControl.Command.Next -> zap(1)
                RemoteControl.Command.Previous -> zap(-1)
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            RemoteControl.live = info
            RemoteControl.vod = null
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
                if (ev.key == Key.Menu) {
                    if (panel != null) panel = null else openPanel()
                    return@onPreviewKeyEvent true
                }
                if (ev.key == Key.MediaPlayPause) {
                    togglePause()
                    return@onPreviewKeyEvent true
                }
                if (controls || panel != null) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionRight -> { cycleChat(); true }
                    Key.DirectionLeft -> { toggleStats(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (state is PlaybackState.Error || state == PlaybackState.Offline) player.reload() else controls = true
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                VideoSurface(player, Modifier.fillMaxSize())
                if (chatMode == ChatMode.OVERLAY) {
                    ChatPanel(
                        messages,
                        chatFont,
                        overlay = true,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .width(320.dp)
                            .fillMaxHeight(0.6f)
                            .padding(end = 12.dp, bottom = 12.dp),
                    )
                }
            }
            if (chatMode == ChatMode.SIDE) {
                Box(Modifier.width(320.dp).fillMaxHeight().background(Color(0xFF111114))) {
                    ChatPanel(messages, chatFont, overlay = false, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        StatusOverlay(state, Modifier.align(Alignment.Center))
        TopShade(controls)
        AdBadge(adStatus, Modifier.align(Alignment.TopStart).padding(20.dp))
        if (showStats) StatsBox(player, adStatus, Modifier.align(Alignment.TopEnd).padding(20.dp))

        // Channel banner after zapping.
        var bannerVisible by remember { mutableStateOf(true) }
        LaunchedEffect(zapBanner) {
            bannerVisible = true
            delay(3_500)
            bannerVisible = false
        }
        AnimatedVisibility(bannerVisible && !controls && panel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart)) {
            info?.let { ZapBanner(it, index, channels.size) }
        }

        ControlsOverlay(controls) {
            info?.let { LiveHeader(it, index, channels.size) }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RoundIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Live fortsetzen" else "Pause",
                    onClick = { togglePause() },
                    modifier = Modifier.focusRequester(playFocus),
                    highlighted = true,
                )
                if (channels.size > 1) {
                    RoundIconButton(Icons.Filled.SkipPrevious, "Vorheriger", onClick = { zap(-1) })
                    RoundIconButton(Icons.Filled.SkipNext, "Nächster", onClick = { zap(1) })
                }
                RoundIconButton(Icons.Filled.Chat, "Chat: ${chatMode.label}", onClick = { cycleChat() })
                info?.let { i -> RoundIconButton(Icons.Filled.VideoLibrary, "Kanal & VODs", onClick = { onChannel(i.login) }) }
                RoundIconButton(Icons.Filled.Tune, "Einstellungen", onClick = { openPanel() })
                RoundIconButton(Icons.Filled.Info, if (showStats) "Statistiken aus" else "Statistiken", onClick = { toggleStats() })
                RoundIconButton(Icons.Filled.Refresh, "Neu laden", onClick = { player.reload(); paused = false })
            }
            Text(
                "▲▼ Kanal wechseln  ·  ▶ Chat  ·  ◀ Statistiken  ·  Menü-Taste: Einstellungen",
                color = Colors.TextDim,
                fontSize = 11.sp,
            )
        }

        SidePanel(
            visible = panel != null,
            title = if (panel == Panel.QUALITY) "Qualität" else "Einstellungen",
            rows = if (panel == Panel.QUALITY) {
                listOf(PanelRow("Auto", checked = selectedQuality == null) { player.selectQuality(null); panel = Panel.MAIN }) +
                    qualities.map { q -> PanelRow(q.label, checked = selectedQuality == q.label) { player.selectQuality(q); panel = Panel.MAIN } }
            } else {
                listOf(
                    PanelRow("Qualität", value = selectedQuality ?: "Auto (${player.currentQualityLabel()})", submenu = true) { panel = Panel.QUALITY },
                    PanelRow("Chat", value = chatMode.label) { cycleChat() },
                    PanelRow("Chat-Schriftgröße", value = "$chatFont") {
                        chatFont = if (chatFont >= 19) 11 else chatFont + 2
                        settings.chatFontSize = chatFont
                    },
                    PanelRow("Statistiken", checked = showStats) { toggleStats() },
                    PanelRow("Kanal & VODs", submenu = true) { info?.let { onChannel(it.login) } },
                    PanelRow("Stream neu laden") { player.reload(); panel = null },
                )
            },
        )
    }
}

@Composable
private fun LiveHeader(info: StreamInfo, index: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(info.avatarUrl, 60.dp, live = true)
        Spacer(Modifier.width(18.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.displayName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(12.dp))
                LiveBadge()
                if (total > 1) {
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1} / $total", color = Colors.TextDim, fontSize = 14.sp)
                }
            }
            if (info.title.isNotBlank()) {
                Text(info.title, color = Colors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(820.dp))
            }
            Text(details(info), color = Colors.PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ZapBanner(info: StreamInfo, index: Int, total: Int) {
    Row(
        Modifier
            .padding(28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(info.avatarUrl, 48.dp, live = true)
        Spacer(Modifier.width(14.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.displayName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (total > 1) {
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1}/$total", color = Colors.TextDim, fontSize = 13.sp)
                }
            }
            if (info.title.isNotBlank()) {
                Text(info.title, color = Colors.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(480.dp))
            }
            Text(details(info), color = Colors.PurpleLight, fontSize = 12.sp)
            Text("OK: Steuerung  ·  ▲▼ Kanal  ·  ▶ Chat", color = Colors.TextDim, fontSize = 11.sp)
        }
    }
}

private fun details(info: StreamInfo) = listOfNotNull(
    info.gameName.ifBlank { null },
    if (info.viewers > 0) "${formatViewers(info.viewers)} Zuschauer" else null,
    formatUptime(info.startedAtMillis()).ifBlank { null },
).joinToString("  ·  ")
