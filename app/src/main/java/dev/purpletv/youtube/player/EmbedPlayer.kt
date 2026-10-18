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
import dev.purpletv.core.AppLog

/**
 * Fallback player: YouTube's own embedded player (IFrame Player API) in a WebView. Used when
 * YouTube refuses to hand out stream URLs to the extractor (bot check, changed player code). It
 * shows YouTube's ads, but the video plays. Driven through the API, so the D-pad and the phone
 * remote work the same as with the normal player.
 *
 * The page is loaded with the app id as origin: YouTube refuses embeds without an identifiable
 * embedder (error 152/153), and for apps it asks for https://<package name>.
 */
@SuppressLint("SetJavaScriptEnabled")
class EmbedPlayer(context: Context) {

    private val origin = "https://${context.packageName}"

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
        webViewClient = WebViewClient()
    }

    @Volatile var positionMs = 0L; private set
    @Volatile var durationMs = 0L; private set
    @Volatile var ended = false; private set
    @Volatile var paused = false; private set
    @Volatile var muted = false; private set
    @Volatile var volume = 1f; private set
    /** YouTube's error code when the embed refuses the video (e.g. 101/150: embedding disabled). */
    @Volatile var error: String? = null; private set
    var videoId: String? = null; private set

    fun load(id: String, startMs: Long) {
        videoId = id
        positionMs = startMs
        durationMs = 0
        ended = false
        paused = false
        error = null
        val safeId = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        view.loadDataWithBaseURL("$origin/", page(safeId, startMs / 1000), "text/html", "utf-8", null)
    }

    /** Reads the player state; call about once a second. */
    fun poll() {
        view.evaluateJavascript("typeof couchState==='function'?couchState():''") { raw ->
            val parts = raw.trim('"').split(',')
            if (parts.size == 2 && parts[0] == "err") {
                if (error == null) AppLog.e("YouTube", "Eingebetteter Player: Fehler ${parts[1]} für $videoId")
                error = parts[1]
                return@evaluateJavascript
            }
            if (parts.size < 4) return@evaluateJavascript
            positionMs = ((parts[0].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            durationMs = ((parts[1].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            ended = parts[2] == "true"
            paused = parts[3] == "true"
        }
    }

    fun toggle() = js("if(player&&ready){player.getPlayerState()===1?player.pauseVideo():player.playVideo();}").also { paused = !paused }
    fun play() = js("if(player&&ready)player.playVideo();").also { paused = false }
    fun pause() = js("if(player&&ready)player.pauseVideo();").also { paused = true }
    fun seekTo(ms: Long) = js("if(player&&ready)player.seekTo(${ms.coerceAtLeast(0) / 1000.0},true);")
    fun seekBy(ms: Long) = seekTo(positionMs + ms)

    fun setMuted(mute: Boolean) {
        muted = mute
        js(if (mute) "if(player&&ready)player.mute();" else "if(player&&ready)player.unMute();")
    }

    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        js("if(player&&ready)player.setVolume(${(volume * 100).toInt()});")
    }

    fun release() {
        view.stopLoading()
        view.loadUrl("about:blank")
        view.destroy()
    }

    private fun js(code: String) = view.evaluateJavascript("(function(){$code})()", null)

    private fun page(id: String, startSec: Long) = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%;border:0}</style>
        </head><body><div id="p"></div><script>
        var player=null, ready=false, err='';
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',host:'https://www.youtube-nocookie.com',
            playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,iv_load_policy:3,fs:0,disablekb:1,start:$startSec,origin:'$origin'},
            events:{onReady:function(e){ready=true;e.target.playVideo();},onError:function(e){err=String(e.data);}}});
        }
        function couchState(){
          if(err) return 'err,'+err;
          if(!ready) return '';
          var s=player.getPlayerState();
          return [player.getCurrentTime()||0,player.getDuration()||0,s===0,s===2].join(',');
        }
        </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
    """.trimIndent()
}

@Composable
fun EmbedSurface(player: EmbedPlayer, modifier: Modifier = Modifier) {
    AndroidView(factory = { player.view }, modifier = modifier)
}
