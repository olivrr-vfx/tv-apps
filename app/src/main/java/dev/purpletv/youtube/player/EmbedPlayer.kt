package dev.purpletv.youtube.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Fallback player: YouTube's own embedded player in a WebView. Used when YouTube refuses to hand
 * out stream URLs to the extractor (bot check, changed player code). It shows YouTube's ads, but
 * the video plays. Controlled through the page's <video> element, so the D-pad and the phone
 * remote work the same as with the normal player.
 */
@SuppressLint("SetJavaScriptEnabled")
class EmbedPlayer(context: Context) {

    val view: WebView = WebView(context).apply {
        setBackgroundColor(Color.BLACK)
        // Keys stay with Compose; the page is driven through JavaScript.
        isFocusable = false
        isFocusableInTouchMode = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = js(STYLE)
        }
    }

    @Volatile var positionMs = 0L; private set
    @Volatile var durationMs = 0L; private set
    @Volatile var ended = false; private set
    @Volatile var paused = false; private set
    @Volatile var muted = false; private set
    @Volatile var volume = 1f; private set
    var videoId: String? = null; private set

    fun load(id: String, startMs: Long) {
        videoId = id
        positionMs = startMs
        durationMs = 0
        ended = false
        paused = false
        val start = startMs / 1000
        val url = "https://www.youtube-nocookie.com/embed/$id?autoplay=1&controls=0&rel=0&playsinline=1" +
            "&iv_load_policy=3&fs=0&disablekb=1&start=$start"
        // Without a referrer the embed refuses to play ("error 153").
        view.loadUrl(url, mapOf("Referer" to "https://www.youtube.com/"))
    }

    /** Reads the page state; call about once a second. Also starts playback if autoplay was blocked. */
    fun poll() {
        view.evaluateJavascript(STATE) { raw ->
            val parts = raw.trim('"').split(',')
            if (parts.size < 4) return@evaluateJavascript
            positionMs = ((parts[0].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            durationMs = ((parts[1].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            ended = parts[2] == "true"
            paused = parts[3] == "true"
        }
    }

    fun toggle() = js("var v=document.querySelector('video');if(v){if(v.paused)v.play();else v.pause();}").also { paused = !paused }
    fun play() = js("var v=document.querySelector('video');if(v)v.play();").also { paused = false }
    fun pause() = js("var v=document.querySelector('video');if(v)v.pause();").also { paused = true }
    fun seekTo(ms: Long) = js("var v=document.querySelector('video');if(v)v.currentTime=${ms.coerceAtLeast(0) / 1000.0};")
    fun seekBy(ms: Long) = seekTo(positionMs + ms)

    fun setMuted(mute: Boolean) {
        muted = mute
        js("var v=document.querySelector('video');if(v)v.muted=$mute;")
    }

    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        js("var v=document.querySelector('video');if(v)v.volume=$volume;")
    }

    fun release() {
        view.stopLoading()
        view.loadUrl("about:blank")
        view.destroy()
    }

    private fun js(code: String) = view.evaluateJavascript("(function(){$code})()", null)

    private companion object {
        /** position,duration,ended,paused. Clicks the big play button when autoplay did not start. */
        const val STATE = "(function(){var v=document.querySelector('video');" +
            "if(!v){var b=document.querySelector('.ytp-large-play-button');if(b)b.click();return '';}" +
            "if(v.currentTime<1&&v.paused&&!v.dataset.couch){v.dataset.couch='1';v.play();}" +
            "return [v.currentTime,v.duration||0,v.ended,v.paused].join(',');})()"

        /** Hides YouTube's title bar and buttons; the TV shows its own overlay. */
        const val STYLE = "var s=document.createElement('style');" +
            "s.textContent='.ytp-chrome-top,.ytp-chrome-bottom,.ytp-pause-overlay,.ytp-watermark,.ytp-gradient-top,.ytp-gradient-bottom{display:none!important}body{background:#000}';" +
            "document.head.appendChild(s);"
    }
}

@Composable
fun EmbedSurface(player: EmbedPlayer, modifier: Modifier = Modifier) {
    AndroidView(factory = { player.view }, modifier = modifier)
}
