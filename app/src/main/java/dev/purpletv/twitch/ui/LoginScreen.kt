package dev.purpletv.twitch.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.design.AppMark
import dev.purpletv.design.EmptyState
import dev.purpletv.design.LocalAccent
import dev.purpletv.design.Palette
import dev.purpletv.design.QrCode
import dev.purpletv.design.Tokens
import dev.purpletv.design.Type
import dev.purpletv.twitch.api.TwitchAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Twitch device-code login: QR code + code on the TV, confirmation on the phone. */
@Composable
fun LoginScreen(app: PurpleApp, onDone: () -> Unit) {
    var code by remember { mutableStateOf<TwitchAuth.DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        code = null
        if (!app.auth.hasClientId) {
            error = "Keine Twitch Client-ID hinterlegt. Trag sie unter Einstellungen → Twitch ein (Anleitung in der README)."
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

    val c = code
    when {
        error != null -> EmptyState(
            "Anmeldung fehlgeschlagen",
            body = error,
            icon = Icons.Filled.Login,
            action = "Nochmal versuchen",
            onAction = { attempt++ },
        )
        c == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = LocalAccent.current.primary)
        }
        else -> Box(Modifier.fillMaxSize().padding(Tokens.Space.xxxl), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                QrCode(c.verificationUri, 260.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppMark(AppId.TWITCH, 40.dp)
                        Spacer(Modifier.width(Tokens.Space.md))
                        Text("Mit Twitch anmelden", color = Palette.text, style = Type.headline)
                    }
                    Spacer(Modifier.height(Tokens.Space.lg))
                    Text("1. QR-Code mit dem Handy scannen", color = Palette.textDim, style = Type.body)
                    Text("   oder twitch.tv/activate öffnen", color = Palette.textDim, style = Type.body)
                    Text("2. Diesen Code bestätigen:", color = Palette.textDim, style = Type.body)
                    Spacer(Modifier.height(Tokens.Space.md))
                    Text(
                        c.userCode,
                        modifier = Modifier.clip(RoundedCornerShape(Tokens.Radius.md)).background(Palette.surfaceRaised).padding(horizontal = 22.dp, vertical = 10.dp),
                        color = LocalAccent.current.light,
                        style = Type.display.copy(fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                    )
                    Spacer(Modifier.height(Tokens.Space.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = LocalAccent.current.primary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Warte auf Bestätigung…", color = Palette.textDim, style = Type.bodySmall)
                    }
                }
            }
        }
    }
}
