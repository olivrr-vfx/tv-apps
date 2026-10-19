package dev.purpletv.twitch.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.core.Load
import dev.purpletv.data.ChatMode
import dev.purpletv.design.Avatar
import dev.purpletv.design.ControlsOverlay
import dev.purpletv.design.LiveBadge
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.PanelRow
import dev.purpletv.design.Palette
import dev.purpletv.design.PlayerMessage
import dev.purpletv.design.RoundIconButton
import dev.purpletv.design.SidePanel
import dev.purpletv.design.StatsBox
import dev.purpletv.design.StatusOverlay
import dev.purpletv.design.Tokens
import dev.purpletv.design.TopShade
import dev.purpletv.design.Type
import dev.purpletv.design.VideoSurface
import dev.purpletv.player.PlaybackState
import dev.purpletv.remote.RemoteControl
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.formatUptime
import dev.purpletv.twitch.api.formatViewers
import dev.purpletv.twitch.chat.ChatClient
import dev.purpletv.twitch.player.AdStatus
import dev.purpletv.twitch.player.StreamPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Panel { MAIN, QUALITY }

/** Seconds before switching away from a channel that went offline (followed list only). */
private const val AUTO_NEXT_SEC = 10

@Composable
fun LivePlayerScreen(
    app: PurpleApp,
    channels: List<StreamInfo>,
    startLogin: String,
    followedSource: Boolean,
    onExit: () -> Unit,
    onChannel: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = app.settings
    val repo = app.twitch
    val player = remember { StreamPlayer(context, settings, app.gql) }
    val chat = remember { ChatClient(settings.thirdPartyEmotes) }

    val followedState by repo.followed.collectAsState()
    // What ▲▼ zaps through. Opened from the followed list, it follows the live-updating list, so
    // channels that went offline drop out and new ones show up.
    val zapList: List<StreamInfo> = if (followedSource) {
        (followedState as? Load.Ok)?.data?.takeIf { it.isNotEmpty() } ?: channels
    } else {
        channels
    }

    var login by remember { mutableStateOf(startLogin) }
    var info by remember { mutableStateOf(channels.firstOrNull { it.login == startLogin } ?: StreamInfo(null, startLogin, startLogin)) }
    /** Channels seen in the followed list while this player was open. */
    val seenLive = remember { mutableSetOf<String>() }
    var wentOffline by remember { mutableStateOf(false) }
    var stayOnOffline by remember { mutableStateOf(false) }
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

    val offline = wentOffline || state == PlaybackState.Offline
    val position = zapList.indexOfFirst { it.login == login }
    val others = zapList.filter { it.login != login && it.isLive }

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
    LaunchedEffect(login) {
        paused = false
        wentOffline = false
        stayOnOffline = false
        zapBanner = System.currentTimeMillis()
        player.play(login)
        // Opened by name (deep link / Home Assistant): fetch title, game and avatar right away.
        if (info.title.isBlank() || info.avatarUrl.isBlank()) {
            withContext(Dispatchers.IO) { runCatching { app.helix.stream(login) }.getOrNull() }?.let { info = it }
        }
        val id = info.userId ?: withContext(Dispatchers.IO) { runCatching { app.gql.user(login)?.id }.getOrNull() }
        chat.connect(login, id)
    }

    // The followed list refreshed: take over fresh title/viewers, notice when the channel ended.
    LaunchedEffect(followedState, login) {
        val list = (followedState as? Load.Ok)?.data ?: return@LaunchedEffect
        val fresh = list.firstOrNull { it.login == login }
        if (fresh != null) {
            seenLive += login
            info = fresh.copy(avatarUrl = fresh.avatarUrl.ifEmpty { info.avatarUrl })
            wentOffline = false
        } else if (login in seenLive) {
            // Only trust the list for channels it already listed: Helix needs a moment to list
            // a stream that just started.
            wentOffline = true
        }
    }

    // Outside the followed list: ask Twitch directly once a minute.
    LaunchedEffect(login) {
        while (true) {
            delay(60_000)
            if (repo.isFollowedLive(login) == true) continue
            val result = withContext(Dispatchers.IO) { runCatching { app.helix.stream(login) } }
            result.onSuccess { fresh ->
                if (fresh == null) wentOffline = true
                else info = fresh.copy(avatarUrl = fresh.avatarUrl.ifEmpty { info.avatarUrl })
            }
        }
    }

    fun zap(delta: Int) {
        val list = zapList.filter { it.isLive || it.login == login }
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.login == login }
        val next = if (idx == -1) {
            if (delta > 0) list.first() else list.last()
        } else {
            list[(idx + delta + list.size) % list.size]
        }
        if (next.login == login) return
        info = next
        login = next.login
    }
    // Long-running effects call the zap of the latest composition (the zap list changes).
    val zapRef by rememberUpdatedState(::zap)

    // Channel ended: move on to the next live channel after a countdown (followed list only).
    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(offline, stayOnOffline, others.size) {
        countdown = 0
        if (!offline || stayOnOffline || !followedSource || others.isEmpty()) return@LaunchedEffect
        for (s in AUTO_NEXT_SEC downTo 1) {
            countdown = s
            delay(1_000)
        }
        countdown = 0
        zapRef(1)
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

    // Home Assistant / phone remote: commands in, current state out.
    LaunchedEffect(Unit) {
        RemoteControl.commands.collect { c ->
            when (c) {
                RemoteControl.Command.Play -> if (paused) togglePause()
                RemoteControl.Command.Pause -> if (!paused) togglePause()
                RemoteControl.Command.Toggle -> togglePause()
                is RemoteControl.Command.Mute -> player.setMuted(c.mute)
                is RemoteControl.Command.Volume -> player.setVolume(c.level)
                RemoteControl.Command.Next -> zapRef(1)
                RemoteControl.Command.Previous -> zapRef(-1)
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val i = info
            RemoteControl.nowPlaying = RemoteControl.NowPlaying(
                app = AppId.TWITCH,
                kind = "live",
                id = i.login,
                title = i.title,
                channel = i.displayName,
                channelId = i.login,
                thumbnail = i.thumbnail(640, 360),
                avatar = i.avatarUrl,
                game = i.gameName,
                viewers = i.viewers.toLong(),
                startedAt = i.startedAt,
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
                        when {
                            countdown > 0 -> stayOnOffline = true
                            state is PlaybackState.Error || state == PlaybackState.Offline -> player.reload()
                            else -> controls = true
                        }
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
                            .width(340.dp)
                            .fillMaxHeight(0.6f)
                            .padding(end = Tokens.Space.md, bottom = Tokens.Space.md),
                    )
                }
            }
            if (chatMode == ChatMode.SIDE) {
                Box(Modifier.width(340.dp).fillMaxHeight().background(Palette.canvas)) {
                    ChatPanel(messages, chatFont, overlay = false, modifier = Modifier.padding(vertical = Tokens.Space.sm))
                }
            }
        }

        if (offline) {
            val next = others.firstOrNull()
            PlayerMessage(
                Modifier.align(Alignment.Center),
                "${info.displayName} ist offline",
                when {
                    countdown > 0 && next != null -> "Weiter zu ${next.displayName} in $countdown s  ·  OK: hier bleiben"
                    others.isNotEmpty() -> "▲▼ nächster Live-Kanal  ·  OK: erneut prüfen"
                    else -> "OK drücken zum erneut Prüfen"
                },
            )
        } else {
            StatusOverlay(state, Modifier.align(Alignment.Center))
        }
        TopShade(controls)
        AdBadge(adStatus, Modifier.align(Alignment.TopStart).padding(20.dp))
        if (showStats) {
            StatsBox(player, Modifier.align(Alignment.TopEnd).padding(20.dp)) { listOf("Werbung    ${adStatus.name.lowercase()}") }
        }

        // Channel banner after zapping.
        var bannerVisible by remember { mutableStateOf(true) }
        LaunchedEffect(zapBanner) {
            bannerVisible = true
            delay(3_500)
            bannerVisible = false
        }
        AnimatedVisibility(bannerVisible && !controls && panel == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart)) {
            ZapBanner(info, position, zapList.size)
        }

        ControlsOverlay(controls) {
            LiveHeader(info, position, zapList.size)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RoundIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Live fortsetzen" else "Pause",
                    onClick = { togglePause() },
                    modifier = Modifier.focusRequester(playFocus),
                    highlighted = true,
                )
                if (zapList.size > 1) {
                    RoundIconButton(Icons.Filled.SkipPrevious, "Vorheriger", onClick = { zap(-1) })
                    RoundIconButton(Icons.Filled.SkipNext, "Nächster", onClick = { zap(1) })
                }
                RoundIconButton(Icons.Filled.Chat, "Chat: ${chatMode.label}", onClick = { cycleChat() })
                RoundIconButton(Icons.Filled.VideoLibrary, "Kanal & VODs", onClick = { onChannel(info.login) })
                RoundIconButton(Icons.Filled.Tune, "Einstellungen", onClick = { openPanel() })
                RoundIconButton(Icons.Filled.Info, if (showStats) "Statistiken aus" else "Statistiken", onClick = { toggleStats() })
                RoundIconButton(Icons.Filled.Refresh, "Neu laden", onClick = { player.reload(); paused = false })
            }
            Text(
                "▲▼ Kanal wechseln  ·  ▶ Chat  ·  ◀ Statistiken  ·  Menü-Taste: Einstellungen",
                color = Palette.textDim,
                style = Type.caption,
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
                    PanelRow("Kanal & VODs", submenu = true) { onChannel(info.login) },
                    PanelRow("Stream neu laden") { player.reload(); panel = null },
                )
            },
        )
    }
}

@Composable
private fun LiveHeader(info: StreamInfo, index: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(info.avatarUrl, 64.dp, live = true)
        Spacer(Modifier.width(18.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.displayName, color = Color.White, style = Type.headline)
                Spacer(Modifier.width(Tokens.Space.md))
                LiveBadge()
                if (total > 1 && index >= 0) {
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1} / $total", color = Palette.textDim, style = Type.label)
                }
            }
            if (info.title.isNotBlank()) {
                Text(info.title, color = Palette.text, style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 600.dp))
            }
            Text(details(info), color = LocalAccent.current.light, style = Type.label)
        }
    }
}

@Composable
private fun ZapBanner(info: StreamInfo, index: Int, total: Int) {
    Row(
        Modifier
            .padding(28.dp)
            .clip(RoundedCornerShape(Tokens.Radius.lg))
            .background(Palette.scrim)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(info.avatarUrl, 48.dp, live = true)
        Spacer(Modifier.width(14.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.displayName, color = Color.White, style = Type.title)
                if (total > 1 && index >= 0) {
                    Spacer(Modifier.width(10.dp))
                    Text("${index + 1}/$total", color = Palette.textDim, style = Type.bodySmall)
                }
            }
            if (info.title.isNotBlank()) {
                Text(info.title, color = Palette.text, style = Type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(480.dp))
            }
            Text(details(info), color = LocalAccent.current.light, style = Type.caption)
            Text("OK: Steuerung  ·  ▲▼ Kanal  ·  ▶ Chat", color = Palette.textDim, style = Type.caption)
        }
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
    val color = if (status == AdStatus.SHOWING) Palette.danger else LocalAccent.current.primary
    Row(
        modifier.clip(RoundedCornerShape(Tokens.Radius.pill)).background(color.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Tokens.Space.sm))
        Text(text, color = Color.White, style = Type.label)
    }
}

private fun details(info: StreamInfo) = listOfNotNull(
    info.gameName.ifBlank { null },
    if (info.viewers > 0) "${formatViewers(info.viewers)} Zuschauer" else null,
    formatUptime(info.startedAtMillis()).ifBlank { null },
).joinToString("  ·  ")
