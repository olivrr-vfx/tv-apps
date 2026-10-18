package dev.purpletv.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.purpletv.PurpleApp
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.CornerToast
import dev.purpletv.design.DialogButtons
import dev.purpletv.design.Palette
import dev.purpletv.design.QrCode
import dev.purpletv.design.Tokens
import dev.purpletv.design.TvButton
import dev.purpletv.design.TvDialog
import dev.purpletv.design.Type
import dev.purpletv.design.initialFocus
import dev.purpletv.remote.RemoteServer
import dev.purpletv.update.UpdateState
import dev.purpletv.update.Updater
import kotlinx.coroutines.delay

/** QR code that opens the phone remote already paired, plus how to install it on an iPhone. */
@Composable
fun PhoneRemoteDialog(app: PurpleApp, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(app.settings.remoteEnabled) }
    TvDialog("Handy als Fernbedienung", onDismiss = onDismiss, width = 860.dp) {
        if (!enabled) {
            Text("Dafür muss die Steuerung im Heimnetz an sein.", color = Palette.textDim, style = Type.body)
            Spacer(Modifier.height(Tokens.Space.xl))
            DialogButtons {
                TvButton("Einschalten", style = ButtonStyle.Primary, modifier = Modifier.initialFocus(), onClick = {
                    app.settings.remoteEnabled = true
                    app.startRemote()
                    enabled = true
                })
                TvButton("Abbrechen", onClick = onDismiss)
            }
            return@TvDialog
        }
        val url = remember { RemoteServer.pairingUrl(app.settings) }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.xxl), verticalAlignment = Alignment.CenterVertically) {
            QrCode(url, 240.dp)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.sm)) {
                Text("1. QR-Code mit der iPhone-Kamera scannen (gleiches WLAN wie der TV).", color = Palette.text, style = Type.body)
                Text("2. In Safari: Teilen → „Zum Home-Bildschirm“.", color = Palette.text, style = Type.body)
                Text("3. Fertig – PurpleTV ist jetzt eine App auf deinem iPhone.", color = Palette.text, style = Type.body)
                Spacer(Modifier.height(Tokens.Space.sm))
                Text("Funktioniert auch mit Android und am PC. Adresse:", color = Palette.textDim, style = Type.bodySmall)
                Text(url, color = Palette.textDim, style = Type.label)
            }
        }
        Spacer(Modifier.height(Tokens.Space.xl))
        TvButton("Fertig", onClick = onDismiss, style = ButtonStyle.Primary, modifier = Modifier.initialFocus())
    }
}

/** Shows update progress in the corner, and asks once for the install permission. */
@Composable
fun UpdateOverlay() {
    val state by Updater.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        is UpdateState.Downloading -> CornerToast {
            Text("Update ${s.version} wird geladen…", color = Color.White, style = Type.bodySmall)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { s.progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.width(220.dp),
            )
        }
        is UpdateState.Installing -> CornerToast { Text("Update ${s.version} wird installiert…", color = Color.White, style = Type.bodySmall) }
        UpdateState.UpToDate -> AutoDismiss { CornerToast { Text("PurpleTV ist aktuell", color = Color.White, style = Type.bodySmall) } }
        is UpdateState.Failed -> AutoDismiss { CornerToast { Text(s.message, color = Color.White, style = Type.bodySmall) } }
        UpdateState.NeedsPermission -> {
            var noSettings by remember { mutableStateOf(false) }
            TvDialog(
                "Neues Update verfügbar",
                onDismiss = { Updater.dismiss() },
                body = if (noSettings) {
                    "Öffne auf dem TV Einstellungen → Apps → Sicherheit & Einschränkungen → Unbekannte Quellen und erlaube PurpleTV. Danach die App neu starten."
                } else {
                    "Damit sich PurpleTV selbst aktualisieren kann, musst du einmalig erlauben, dass es Apps installieren darf."
                },
            ) {
                DialogButtons {
                    TvButton(
                        "Erlauben",
                        style = ButtonStyle.Primary,
                        modifier = Modifier.initialFocus(),
                        onClick = { if (!Updater.openPermissionSettings(context)) noSettings = true },
                    )
                    TvButton("Später", onClick = { Updater.dismiss() })
                }
            }
        }
    }
}

@Composable
private fun AutoDismiss(content: @Composable () -> Unit) {
    LaunchedEffect(Unit) {
        delay(5_000)
        Updater.dismiss()
    }
    content()
}
