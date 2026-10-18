package dev.purpletv.youtube.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
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
import dev.purpletv.design.Avatar
import dev.purpletv.design.ControlsOverlay
import dev.purpletv.design.LiveBadge
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.PanelRow
import dev.purpletv.design.Palette
import dev.purpletv.design.PlayerMessage
import dev.purpletv.design.RoundIconButton
import dev.purpletv.design.SeekBar
import dev.purpletv.design.SeekBubble
import dev.purpletv.design.SidePanel
import dev.purpletv.design.StatsBox
import dev.purpletv.design.StatusOverlay
import dev.purpletv.design.Thumbnail
import dev.purpletv.design.Tokens
import dev.purpletv.design.TopShade
import dev.purpletv.design.Type
import dev.purpletv.design.VideoSurface
import dev.purpletv.design.seekStepMs
import dev.purpletv.player.PlaybackState
import dev.purpletv.remote.RemoteControl
import dev.purpletv.youtube.api.YouTubeClient
import dev.purpletv.youtube.api.YtPlayback
import dev.purpletv.youtube.api.YtVideo
import dev.purpletv.youtube.player.YouTubePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class WatchPanel { MAIN, QUALITY }

@Composable
fun WatchScreen(app: PurpleApp, start: YtVideo, queue: List<YtVideo>, onExit: () -> Unit, onChannel: (String, String) -> Unit) {
    val context = LocalContext.current
    val settings = app.settings
    val history = app.youtube.history

    var video by remember { mutableStateOf(start) }
    var playback by remember { mutableStateOf<YtPlayback?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    val currentVideo by rememberUpdatedState(video)
    val player = remember {
        YouTubePlayer(context, settings) {
            withContext(Dispatchers.IO) { runCatching { YouTubeClient.playback(currentVideo.id) }.getOrNull() }
        }
    }
    val state by player.state.collectAsState()
    val qualities by player.qualities.collectAsState()
    val selectedQuality by player.selectedQuality.collectAsState()

    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<WatchPanel?>(null) }
    var showStats by remember { mutableStateOf(settings.showStats) }
    var paused by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(start.durationSec * 1000) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var seekStart by remember { mutableLongStateOf(0L) }
    var seekStamp by remember { mutableLongStateOf(0L) }
    var upNextIn by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    val next: YtVideo? = run {
        val i = queue.indexOfFirst { it.id == video.id }
        queue.getOrNull(i + 1).takeIf { i >= 0 } ?: playback?.related?.firstOrNull { it.id != video.id && !it.isLive }
    }

    fun saveProgress() {
        val d = player.durationMs.takeIf { it > 0 } ?: duration
        history.save(playback?.video ?: video, player.positionMs, d)
    }

    // Resolve stream URLs whenever the video changes.
    LaunchedEffect(video.id, attempt) {
        loadError = null
        playback = null
        upNextIn = 0
        position = 0
        duration = video.durationSec * 1000
        val result = withContext(Dispatchers.IO) {
            runCatching {
                YouTubeClient.init(settings.youtubeRegion)
                YouTubeClient.playback(video.id)
            }
        }
        result.onSuccess {
            playback = it
            video = it.video.copy(channelAvatar = it.video.channelAvatar.ifEmpty { video.channelAvatar })
            player.play(it, history.position(video.id) ?: 0L)
            paused = false
        }.onFailure { loadError = it.message ?: "Video konnte nicht geladen werden" }
    }

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

    fun playNext(target: YtVideo?) {
        target ?: return
        saveProgress()
        video = target
    }

    // Autoplay: count down at the end of a video, then continue with the next one.
    LaunchedEffect(state, next?.id) {
        if (state != PlaybackState.Ended || next == null || !settings.youtubeAutoplay) {
            upNextIn = 0
            return@LaunchedEffect
        }
        saveProgress()
        for (s in 8 downTo 1) {
            upNextIn = s
            delay(1_000)
        }
        upNextIn = 0
        playNext(next)
    }

    LaunchedEffect(seekStamp) {
        val target = pendingSeek ?: return@LaunchedEffect
        delay(650)
        player.seekTo(target)
        position = target
        pendingSeek = null
    }

    fun seekStep(forward: Boolean, repeat: Int) {
        if (video.isLive) return
        val base = pendingSeek ?: player.positionMs.also { seekStart = it }
        val step = seekStepMs(repeat)
        pendingSeek = (base + if (forward) step else -step).coerceIn(0, (duration - 1000).coerceAtLeast(0))
        seekStamp = System.nanoTime()
    }

    LaunchedEffect(controls, panel, lastInput, paused) {
        if ((controls || panel != null) && !paused) {
            delay(if (panel != null) 12_000 else 6_000)
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
            panel == WatchPanel.QUALITY -> panel = WatchPanel.MAIN
            panel != null -> panel = null
            controls && !paused && state != PlaybackState.Ended -> controls = false
            else -> onExit()
        }
    }

    fun togglePause() {
        if (paused) player.resume() else player.pause()
        paused = !paused
    }
    fun toggleStats() {
        showStats = !showStats
        settings.showStats = showStats
    }

    val nextRef by rememberUpdatedState(next)
    LaunchedEffect(Unit) {
        RemoteControl.commands.collect { c ->
            when (c) {
                RemoteControl.Command.Play -> if (paused) togglePause()
                RemoteControl.Command.Pause -> if (!paused) togglePause()
                RemoteControl.Command.Toggle -> togglePause()
                is RemoteControl.Command.Mute -> player.setMuted(c.mute)
                is RemoteControl.Command.Volume -> player.setVolume(c.level)
                RemoteControl.Command.Next -> playNext(nextRef)
                RemoteControl.Command.Previous -> player.seekTo(player.positionMs - 10_000)
                is RemoteControl.Command.Seek -> player.seekTo(c.positionSec * 1000)
                is RemoteControl.Command.SeekBy -> player.seekTo(player.positionMs + c.deltaSec * 1000)
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val v = currentVideo
            RemoteControl.nowPlaying = RemoteControl.NowPlaying(
                app = AppId.YOUTUBE,
                kind = if (v.isLive) "live" else "video",
                id = v.id,
                title = v.title,
                channel = v.channelName,
                channelId = v.channelUrl,
                thumbnail = v.thumbnail,
                avatar = v.channelAvatar,
                viewers = if (v.isLive) v.views.coerceAtLeast(0) else 0,
                durationSec = duration / 1000,
                positionSec = player.positionMs / 1000,
                isLive = v.isLive,
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
                        panel = if (panel != null) null else WatchPanel.MAIN
                        controls = false
                        return@onPreviewKeyEvent true
                    }
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { togglePause(); return@onPreviewKeyEvent true }
                    Key.MediaFastForward -> { seekStep(true, 4); return@onPreviewKeyEvent true }
                    Key.MediaRewind -> { seekStep(false, 4); return@onPreviewKeyEvent true }
                    Key.MediaNext -> { playNext(next); return@onPreviewKeyEvent true }
                    else -> Unit
                }
                if (controls || panel != null) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { seekStep(false, ev.nativeKeyEvent.repeatCount); true }
                    Key.DirectionRight -> { seekStep(true, ev.nativeKeyEvent.repeatCount); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown -> {
                        when {
                            upNextIn > 0 -> playNext(next)
                            loadError != null -> attempt++
                            state is PlaybackState.Error -> player.reload()
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
        VideoSurface(player, Modifier.fillMaxSize())
        if (playback == null && loadError == null) {
            // Poster while the stream URLs are resolved.
            Thumbnail(video.thumbnail, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Palette.scrim))
        }
        when {
            loadError != null -> PlayerMessage(Modifier.align(Alignment.Center), loadError!!, "OK: erneut versuchen  ·  Zurück: Übersicht")
            upNextIn > 0 && next != null -> UpNext(next, upNextIn, Modifier.align(Alignment.Center))
            else -> StatusOverlay(state, Modifier.align(Alignment.Center), offlineText = "Video nicht verfügbar")
        }
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
                Avatar(video.channelAvatar, 52.dp)
                Spacer(Modifier.width(Tokens.Space.lg))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(video.channelName, color = LocalAccent.current.light, style = Type.titleSmall)
                        if (video.isLive) {
                            Spacer(Modifier.width(Tokens.Space.sm))
                            LiveBadge()
                        }
                    }
                    Text(video.title, color = Color.White, style = Type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(video.metaLine(), color = Palette.textDim, style = Type.bodySmall)
                }
            }
            if (!video.isLive) {
                Spacer(Modifier.height(18.dp))
                SeekBar(position, duration, pendingSeek, onSeekStep = { fwd, rep -> seekStep(fwd, rep) })
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!video.isLive) RoundIconButton(Icons.Filled.Replay10, "−10 s", onClick = { player.seekTo(player.positionMs - 10_000) })
                RoundIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Abspielen" else "Pause",
                    onClick = { togglePause() },
                    modifier = Modifier.focusRequester(playFocus),
                    highlighted = true,
                )
                if (!video.isLive) RoundIconButton(Icons.Filled.Forward10, "+10 s", onClick = { player.seekTo(player.positionMs + 10_000) })
                if (next != null) RoundIconButton(Icons.Filled.SkipNext, "Nächstes Video", onClick = { playNext(next) })
                Spacer(Modifier.width(18.dp))
                RoundIconButton(Icons.Filled.VideoLibrary, "Kanal", onClick = { onChannel(video.channelUrl, video.channelName) })
                RoundIconButton(Icons.Filled.Tune, "Einstellungen", onClick = { controls = false; panel = WatchPanel.MAIN })
                RoundIconButton(Icons.Filled.Info, if (showStats) "Statistiken aus" else "Statistiken", onClick = { toggleStats() })
            }
        }

        val subscribed = app.youtube.isSubscribed(dev.purpletv.youtube.api.channelIdFromUrl(video.channelUrl) ?: "")
        SidePanel(
            visible = panel != null,
            title = if (panel == WatchPanel.QUALITY) "Qualität" else "Einstellungen",
            rows = if (panel == WatchPanel.QUALITY) {
                listOf(PanelRow("Auto", checked = selectedQuality == null) { player.selectQuality(null); panel = WatchPanel.MAIN }) +
                    qualities.map { q -> PanelRow(q.label, checked = selectedQuality == q.label) { player.selectQuality(q); panel = WatchPanel.MAIN } }
            } else {
                listOfNotNull(
                    PanelRow("Qualität", value = selectedQuality ?: "Auto (${player.currentQualityLabel()})", submenu = true) { panel = WatchPanel.QUALITY },
                    PanelRow("Autoplay", checked = settings.youtubeAutoplay) { settings.youtubeAutoplay = !settings.youtubeAutoplay; panel = WatchPanel.MAIN },
                    PanelRow("Statistiken", checked = showStats) { toggleStats() },
                    if (!video.isLive) PanelRow("Von vorne abspielen") { player.seekTo(0); panel = null } else null,
                    PanelRow("Zum Kanal", value = if (subscribed) "abonniert" else null, submenu = true) { onChannel(video.channelUrl, video.channelName) },
                    PanelRow("Neu laden") { player.reload(); panel = null },
                )
            },
        )
    }
}

@Composable
private fun UpNext(next: YtVideo, seconds: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(Tokens.Radius.lg)).background(Palette.scrim).padding(Tokens.Space.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(next.thumbnail, Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(Tokens.Radius.md)))
        Spacer(Modifier.width(Tokens.Space.xl))
        Column(Modifier.width(420.dp)) {
            Text("ALS NÄCHSTES IN $seconds S", color = LocalAccent.current.light, style = Type.overline)
            Text(next.title, color = Color.White, style = Type.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(next.channelName, color = Palette.textDim, style = Type.body)
            Spacer(Modifier.height(Tokens.Space.sm))
            Text("OK: sofort abspielen  ·  Zurück: beenden", color = Palette.textFaint, style = Type.caption)
        }
    }
}
