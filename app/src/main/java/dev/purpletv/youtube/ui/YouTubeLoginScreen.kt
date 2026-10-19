package dev.purpletv.youtube.ui

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
import androidx.compose.foundation.layout.widthIn
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
import dev.purpletv.remote.RemoteServer
import dev.purpletv.youtube.api.YouTubeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Google login for YouTube: QR code + code on the TV, confirmation on the phone. Afterwards the
 * account's subscriptions are taken over.
 */
@Composable
fun YouTubeLoginScreen(app: PurpleApp, onDone: () -> Unit) {
    val account = app.youtube.account
    var code by remember { mutableStateOf<YouTubeAccount.DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    if (!account.isConfigured) {
        val url = RemoteServer.pairingUrl(app.settings)
        EmptyState(
            "Google-Login einrichten",
            body = "Google erlaubt den Login auf Fernsehern nur mit einer eigenen Google-Client-ID (einmalig, Anleitung in der README). " +
                "Trag sie in Couch auf Handy oder Mac unter YouTube → Konto ein: $url\n\n" +
                "Ohne Einrichtung: deine Abos aus Google Takeout (Abos.csv) dort hochladen.",
            icon = Icons.Filled.Login,
            action = "Zurück",
            onAction = onDone,
        )
        return
    }

    LaunchedEffect(attempt) {
        error = null
        code = null
        val device = withContext(Dispatchers.IO) { runCatching { account.startDeviceFlow() } }.getOrElse {
            error = "Login konnte nicht gestartet werden: ${it.message}"
            return@LaunchedEffect
        }
        code = device
        var interval = device.intervalSec.coerceAtLeast(2) * 1000L
        val deadline = System.currentTimeMillis() + device.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            when (val r = withContext(Dispatchers.IO) { account.poll(device) }) {
                YouTubeAccount.PollResult.Pending -> Unit
                YouTubeAccount.PollResult.SlowDown -> interval += 2000
                YouTubeAccount.PollResult.Success -> {
                    status = "Abos werden übernommen…"
                    app.youtube.syncAccount { onDone() }
                    return@LaunchedEffect
                }
                is YouTubeAccount.PollResult.Failed -> {
                    error = r.message
                    return@LaunchedEffect
                }
            }
        }
        error = "Code abgelaufen"
    }

    val c = code
    when {
        error != null -> EmptyState("Anmeldung fehlgeschlagen", body = error, icon = Icons.Filled.Login, action = "Nochmal versuchen", onAction = { attempt++ })
        c == null || status != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = LocalAccent.current.primary)
                status?.let {
                    Spacer(Modifier.height(Tokens.Space.lg))
                    Text(it, color = Palette.textDim, style = Type.body)
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().padding(Tokens.Space.xxxl), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                QrCode(c.verificationUrl, 240.dp)
                Column(Modifier.widthIn(max = 460.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppMark(AppId.YOUTUBE, 40.dp)
                        Spacer(Modifier.width(Tokens.Space.md))
                        Text("Mit Google anmelden", color = Palette.text, style = Type.headline)
                    }
                    Spacer(Modifier.height(Tokens.Space.lg))
                    Text("1. QR-Code scannen oder ${c.verificationUrl.removePrefix("https://")} öffnen", color = Palette.textDim, style = Type.body)
                    Text("2. Diesen Code eingeben:", color = Palette.textDim, style = Type.body)
                    Spacer(Modifier.height(Tokens.Space.md))
                    Text(
                        c.userCode,
                        modifier = Modifier.clip(RoundedCornerShape(Tokens.Radius.md)).background(Palette.surfaceRaised).padding(horizontal = 22.dp, vertical = 10.dp),
                        color = LocalAccent.current.light,
                        style = Type.display.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
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
