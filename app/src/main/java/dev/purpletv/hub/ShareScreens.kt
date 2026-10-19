package dev.purpletv.hub

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import dev.purpletv.PurpleApp
import dev.purpletv.design.Palette
import dev.purpletv.design.PlayerMessage
import dev.purpletv.design.StatusOverlay
import dev.purpletv.design.Tokens
import dev.purpletv.design.Type
import dev.purpletv.design.VideoSurface
import dev.purpletv.player.BasePlayer
import dev.purpletv.player.PlaybackState
import dev.purpletv.player.PlayerMode
import dev.purpletv.player.loadControlFor
import dev.purpletv.remote.MediaInbox
import dev.purpletv.remote.RemoteControl
import dev.purpletv.remote.RemoteServer
import dev.purpletv.remote.ScreenShare
import kotlinx.coroutines.delay
import java.io.File

/**
 * Shows a screen or camera shared from a browser. The WebRTC part runs in a WebView on Couch's
 * own receiver page (`/receiver`), which talks to the sender through [ScreenShare].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScreenShareScreen(app: PurpleApp, id: String, onExit: () -> Unit) {
    val url = remember(id) {
        "http://127.0.0.1:${RemoteServer.PORT}/receiver?id=$id&token=${Uri.encode(app.settings.remoteCode)}"
    }
    LaunchedEffect(Unit) { RemoteControl.screen = "screen" }
    BackHandler {
        ScreenShare.end(id)
        onExit()
    }
    DisposableEffect(id) { onDispose { ScreenShare.end(id) } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    keepScreenOn = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) = request.grant(request.resources)
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Plays a local video file (photos and videos sent from a phone). */
private class LocalPlayer(context: android.content.Context) : BasePlayer(
    context,
    PlayerMode.VOD,
    DefaultMediaSourceFactory(context),
    loadControlFor(PlayerMode.VOD, lowLatency = false),
) {
    private var file: File? = null

    fun play(file: File) {
        this.file = file
        resetForNewContent()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
    }

    override fun reload() {
        file?.let(::play)
    }

    override fun handleError(error: PlaybackException) {
        _state.value = PlaybackState.Error("Video kann nicht abgespielt werden (${error.errorCodeName})")
    }
}

/** Photos and videos sent to the TV. ◀ ▶ switch between everything sent in this session. */
@Composable
fun MediaViewerScreen(startId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val items = remember { mutableStateOf(MediaInbox.all()) }
    var index by remember { mutableIntStateOf(items.value.indexOfFirst { it.id == startId }.coerceAtLeast(0)) }
    var hint by remember { mutableStateOf(true) }
    val player = remember { LocalPlayer(context) }
    val state by player.state.collectAsState()
    val focus = remember { FocusRequester() }

    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(Unit) {
        RemoteControl.screen = "media"
        runCatching { focus.requestFocus() }
    }
    // New uploads while the viewer is open jump straight to them.
    LaunchedEffect(Unit) {
        RemoteControl.commands.collect { c ->
            if (c is RemoteControl.Command.ShowMedia) {
                items.value = MediaInbox.all()
                items.value.indexOfFirst { it.id == c.id }.takeIf { it >= 0 }?.let { index = it }
            }
        }
    }
    val item = items.value.getOrNull(index)
    LaunchedEffect(item?.id) {
        hint = true
        if (item?.isVideo == true) player.play(item.file) else player.stop()
        delay(4_000)
        hint = false
    }
    BackHandler(onBack = onExit)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                hint = true
                when (ev.key) {
                    Key.DirectionLeft -> { if (index > 0) index--; true }
                    Key.DirectionRight -> { if (index < items.value.lastIndex) index++; true }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (item?.isVideo == true) { if (player.player.isPlaying) player.pause() else player.resume() }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        when {
            item == null -> PlayerMessage(Modifier.align(Alignment.Center), "Nichts zum Anzeigen", "Zurück zur Übersicht")
            item.isVideo -> {
                VideoSurface(player, Modifier.fillMaxSize())
                StatusOverlay(state, Modifier.align(Alignment.Center))
            }
            else -> AsyncImage(item.file, item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        if (hint && item != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Tokens.Layout.safeHorizontal, Tokens.Layout.safeVertical)
                    .clip(RoundedCornerShape(Tokens.Radius.lg))
                    .background(Palette.scrim)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(item.name, color = Color.White, style = Type.titleSmall)
                val help = buildList {
                    add("${index + 1} von ${items.value.size}")
                    if (items.value.size > 1) add("◀ ▶ blättern")
                    if (item.isVideo) add("OK Pause")
                }
                Text(help.joinToString("  ·  "), color = Palette.textDim, style = Type.caption)
            }
        }
    }
}
