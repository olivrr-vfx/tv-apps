package dev.purpletv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.purpletv.update.UpdateState
import dev.purpletv.update.Updater
import kotlinx.coroutines.delay

/** Shows update progress in the corner, and asks once for the install permission. */
@Composable
fun UpdateOverlay() {
    val state by Updater.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> Unit
        is UpdateState.Downloading -> Corner {
            Text("Update ${s.version} wird geladen…", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { s.progress },
                color = Colors.PurpleLight,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.width(220.dp),
            )
        }
        is UpdateState.Installing -> Corner {
            Text("Update ${s.version} wird installiert…", color = Color.White, fontSize = 13.sp)
        }
        UpdateState.UpToDate -> AutoDismiss { Corner { Text("PurpleTV ist aktuell", color = Color.White, fontSize = 13.sp) } }
        is UpdateState.Failed -> AutoDismiss { Corner { Text(s.message, color = Color.White, fontSize = 13.sp) } }
        UpdateState.NeedsPermission -> {
            val focus = remember { FocusRequester() }
            var noSettings by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.width(520.dp).clip(RoundedCornerShape(14.dp)).background(Colors.Surface).padding(28.dp),
                ) {
                    Text("Neues Update verfügbar", color = Colors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (noSettings) {
                            "Öffne auf dem TV Einstellungen → Apps → Sicherheit & Einschränkungen → Unbekannte Quellen und erlaube PurpleTV. Danach die App neu starten."
                        } else {
                            "Damit sich PurpleTV selbst aktualisieren kann, musst du einmalig erlauben, dass es Apps installieren darf."
                        },
                        color = Colors.TextDim,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            "Erlauben",
                            selected = true,
                            modifier = Modifier.focusRequester(focus),
                            onClick = { if (!Updater.openPermissionSettings(context)) noSettings = true },
                        )
                        PillButton("Später", onClick = { Updater.dismiss() })
                    }
                }
            }
        }
    }
}

@Composable
private fun Corner(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Colors.Purple.copy(alpha = 0.95f)).padding(horizontal = 16.dp, vertical = 10.dp)
        ) { content() }
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
