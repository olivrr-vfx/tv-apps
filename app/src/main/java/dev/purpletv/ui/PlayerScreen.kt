package dev.purpletv.ui

import android.view.ViewGroup
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.purpletv.PurpleApp
import dev.purpletv.chat.ChatClient
import dev.purpletv.data.ChatMode
import dev.purpletv.player.AdStatus
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerStats
import dev.purpletv.player.Quality
import dev.purpletv.player.StreamPlayer
import dev.purpletv.twitch.StreamInfo
import dev.purpletv.twitch.formatUptime
import dev.purpletv.twitch.formatViewers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PlayerScreen(app: PurpleApp, channels: List<StreamInfo>, startIndex: Int, onExit: () -> Unit) {
    val context = LocalContext.current
    val settings = app.settings
    val player = remember { StreamPlayer(context, settings, app.gql) }
    val chat = remember { ChatClient(settings.thirdPartyEmotes) }

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))) }
    var info by remember { mutableStateOf(channels.getOrNull(index)) }
    var chatMode by remember { mutableStateOf(settings.chatMode) }
    var showStats by remember { mutableStateOf(settings.showStats) }
    var menuOpen by remember { mutableStateOf(false) }
    var qualityOpen by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var zapBanner by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val state by player.state.collectAsState()
    val adStatus by player.adEngine.status.collectAsState()
    val qualities by player.qualities.collectAsState()
    val selectedQuality by player.selectedQuality.collectAsState()
    val messages by chat.messages.collectAsState()

    val rootFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }

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
        zapBanner = System.currentTimeMillis()
        player.play(current.login)
        val id = current.userId ?: withContext(Dispatchers.IO) { runCatching { app.gql.user(current.login)?.first }.getOrNull() }
        chat.connect(current.login, id)
    }

    // Refresh title / viewer count once a minute.
    LaunchedEffect(index) {
        while (true) {
            delay(60_000)
            val login = channels.getOrNull(index)?.login ?: break
            withContext(Dispatchers.IO) { runCatching { app.helix.stream(login) }.getOrNull() }?.let { info = it }
        }
    }

    LaunchedEffect(menuOpen, qualityOpen, lastInput) {
        if (menuOpen || qualityOpen) {
            delay(8_000)
            menuOpen = false
            qualityOpen = false
        }
        if (!menuOpen && !qualityOpen) runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(menuOpen) { if (menuOpen) runCatching { menuFocus.requestFocus() } }
    LaunchedEffect(qualityOpen) { if (qualityOpen) runCatching { qualityFocus.requestFocus() } }

    BackHandler {
        when {
            qualityOpen -> qualityOpen = false
            menuOpen -> menuOpen = false
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInput = System.currentTimeMillis()
                if (menuOpen || qualityOpen) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionRight -> { cycleChat(); true }
                    Key.DirectionLeft -> { toggleStats(); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu -> {
                        if (state is PlaybackState.Error || state == PlaybackState.Offline) player.reload() else menuOpen = true
                        true
                    }
                    Key.MediaPlayPause -> {
                        if (player.player.isPlaying) player.pause() else player.resume()
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
                    modifier = Modifier.fillMaxSize(),
                )
                if (chatMode == ChatMode.OVERLAY) {
                    ChatPanel(
                        messages,
                        settings.chatFontSize,
                        overlay = true,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .width(300.dp)
                            .fillMaxHeight(0.6f)
                            .padding(end = 12.dp, bottom = 12.dp),
                    )
                }
            }
            if (chatMode == ChatMode.SIDE) {
                Box(Modifier.width(300.dp).fillMaxHeight().background(Color(0xFF18181B))) {
                    ChatPanel(messages, settings.chatFontSize, overlay = false, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        StatusOverlay(state, Modifier.align(Alignment.Center))
        AdBadge(adStatus, Modifier.align(Alignment.TopStart).padding(16.dp))

        if (showStats) {
            StatsBox(player, adStatus, Modifier.align(Alignment.TopEnd).padding(16.dp))
        }

        // Channel name banner after zapping.
        var bannerVisible by remember { mutableStateOf(true) }
        LaunchedEffect(zapBanner) {
            bannerVisible = true
            delay(3_000)
            bannerVisible = false
        }
        AnimatedVisibility(bannerVisible && !menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart)) {
            info?.let { InfoPanel(it, index, channels.size, compact = true) }
        }

        AnimatedVisibility(menuOpen, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 28.dp)
            ) {
                info?.let { InfoPanel(it, index, channels.size, compact = false) }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        "Qualität: ${selectedQuality ?: "Auto"}",
                        onClick = { qualityOpen = true; menuOpen = false },
                        modifier = Modifier.focusRequester(menuFocus),
                    )
                    PillButton("Chat: ${chatMode.label}", onClick = { cycleChat() })
                    PillButton(if (showStats) "Statistiken aus" else "Statistiken", onClick = { toggleStats() })
                    PillButton("Neu laden", onClick = { player.reload(); menuOpen = false })
                    if (channels.size > 1) {
                        PillButton("◀ Vorheriger", onClick = { zap(-1) })
                        PillButton("Nächster ▶", onClick = { zap(1) })
                    }
                }
            }
        }

        if (qualityOpen) {
            QualityMenu(
                qualities,
                selectedQuality,
                firstFocus = qualityFocus,
                onSelect = {
                    player.selectQuality(it)
                    qualityOpen = false
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
            )
        }
    }
}

@Composable
private fun InfoPanel(info: StreamInfo, index: Int, total: Int, compact: Boolean) {
    Column(
        if (compact) {
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        } else Modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.width(10.dp))
            Text(info.displayName, color = Color.White, fontSize = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Bold)
            if (total > 1) {
                Spacer(Modifier.width(10.dp))
                Text("${index + 1}/$total", color = Colors.TextDim, fontSize = 14.sp)
            }
        }
        if (info.title.isNotBlank()) {
            Text(
                info.title,
                color = Colors.Text,
                fontSize = if (compact) 14.sp else 16.sp,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (compact) 520.dp else 820.dp),
            )
        }
        val details = listOfNotNull(
            info.gameName.ifBlank { null },
            if (info.viewers > 0) "${formatViewers(info.viewers)} Zuschauer" else null,
            formatUptime(info.startedAtMillis()).ifBlank { null },
        )
        if (details.isNotEmpty()) Text(details.joinToString("  ·  "), color = Colors.PurpleLight, fontSize = 13.sp)
        if (compact && total > 1) Text("▲▼ Kanal wechseln  ·  ▶ Chat  ·  OK Menü", color = Colors.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun StatusOverlay(state: PlaybackState, modifier: Modifier) {
    when (state) {
        PlaybackState.Loading, PlaybackState.Buffering -> CircularProgressIndicator(color = Colors.Purple, modifier = modifier)
        PlaybackState.Offline -> Message(modifier, "Dieser Kanal ist gerade offline.\nOK = erneut prüfen")
        is PlaybackState.Error -> Message(modifier, "Stream konnte nicht geladen werden\n${state.message}\nOK = erneut versuchen")
        PlaybackState.Playing -> Unit
    }
}

@Composable
private fun Message(modifier: Modifier, text: String) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(20.dp),
        color = Color.White,
        fontSize = 18.sp,
    )
}

@Composable
private fun AdBadge(status: AdStatus, modifier: Modifier) {
    val text = when (status) {
        AdStatus.NONE -> return
        AdStatus.BLOCKED_BACKUP -> "Werbung geblockt – Ersatz-Stream aktiv"
        AdStatus.BLOCKED_STRIP -> "Werbung wird übersprungen…"
        AdStatus.SHOWING -> "Werbung läuft – konnte nicht geblockt werden"
    }
    val color = if (status == AdStatus.SHOWING) Color(0xFFB00020) else Colors.Purple
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatsBox(player: StreamPlayer, adStatus: AdStatus, modifier: Modifier) {
    var stats by remember { mutableStateOf(PlayerStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = player.stats()
            delay(1_000)
        }
    }
    val lines = listOf(
        "Auflösung  ${stats.resolution}",
        "Bitrate    ${stats.bitrateKbps} kbit/s",
        "Bandbreite ${String.format(Locale.US, "%.1f", stats.bandwidthMbps)} Mbit/s",
        "Puffer     ${String.format(Locale.US, "%.1f", stats.bufferSec)} s",
        "Latenz     ${stats.latencySec?.let { String.format(Locale.US, "%.1f s", it) } ?: "-"}",
        "Verworfen  ${stats.droppedFrames} Frames",
        "Werbung    ${adStatus.name.lowercase()}",
    )
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(12.dp)) {
        lines.forEach { Text(it, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun QualityMenu(
    qualities: List<Quality>,
    selected: String?,
    firstFocus: FocusRequester,
    onSelect: (Quality?) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.width(240.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xEE18181B)).padding(12.dp),
    ) {
        Text("Qualität", color = Colors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                QualityRow("Auto", selected == null, Modifier.focusRequester(firstFocus)) { onSelect(null) }
            }
            itemsIndexed(qualities) { _, q ->
                QualityRow(q.label, selected == q.label) { onSelect(q) }
            }
        }
    }
}

@Composable
private fun QualityRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f, background = Color.Transparent, focusedBackground = Colors.SurfaceHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "●" else "○", color = Colors.Purple, fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Colors.Text, fontSize = 15.sp)
        }
    }
}
