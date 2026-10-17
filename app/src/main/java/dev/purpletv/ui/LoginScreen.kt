package dev.purpletv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.purpletv.PurpleApp
import dev.purpletv.twitch.TwitchAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(app: PurpleApp, onDone: () -> Unit) {
    var code by remember { mutableStateOf<TwitchAuth.DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val retryFocus = remember { FocusRequester() }

    LaunchedEffect(attempt) {
        error = null
        code = null
        if (!app.auth.hasClientId) {
            error = "Keine Twitch Client-ID hinterlegt. Trag sie unter Einstellungen ein (Anleitung in der README)."
            return@LaunchedEffect
        }
        val device = withContext(Dispatchers.IO) { runCatching { app.auth.startDeviceFlow() } }
            .getOrElse {
                error = "Login konnte nicht gestartet werden: ${it.message}"
                return@LaunchedEffect
            }
        code = device
        var interval = device.intervalSec.coerceAtLeast(2) * 1000L
        val deadline = System.currentTimeMillis() + device.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            when (val r = withContext(Dispatchers.IO) { app.auth.poll(device) }) {
                TwitchAuth.PollResult.Pending -> Unit
                TwitchAuth.PollResult.SlowDown -> interval += 2000
                TwitchAuth.PollResult.Success -> {
                    withContext(Dispatchers.IO) { runCatching { app.helix.loadSelf() } }
                    onDone()
                    return@LaunchedEffect
                }
                is TwitchAuth.PollResult.Failed -> {
                    error = r.message
                    return@LaunchedEffect
                }
            }
        }
        error = "Code abgelaufen"
    }
    LaunchedEffect(error) {
        if (error != null) runCatching { retryFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        val c = code
        when {
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = Colors.Text, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                PillButton("Nochmal versuchen", onClick = { attempt++ }, selected = true, modifier = Modifier.focusRequester(retryFocus))
            }
            c == null -> CircularProgressIndicator(color = Colors.Purple)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                val qr = remember(c.verificationUri) { qrCode(c.verificationUri, 512) }
                Image(
                    qr,
                    contentDescription = "QR-Code",
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
                )
                Column {
                    Text("Mit Twitch anmelden", color = Colors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("1. QR-Code mit dem Handy scannen", color = Colors.TextDim, fontSize = 16.sp)
                    Text("   oder twitch.tv/activate öffnen", color = Colors.TextDim, fontSize = 16.sp)
                    Text("2. Diesen Code bestätigen:", color = Colors.TextDim, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        c.userCode,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Colors.Surface).padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Colors.PurpleLight,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Colors.Purple, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Warte auf Bestätigung…", color = Colors.TextDim, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun qrCode(text: String, size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
