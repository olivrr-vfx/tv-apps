package dev.purpletv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.purpletv.BuildConfig

@Composable
fun SettingsScreen(vm: HomeViewModel, onLogin: () -> Unit) {
    val s = vm.app.settings
    val name by vm.loggedInName.collectAsState()
    // Bumped on every change so the rows re-read the SharedPreferences values.
    var version by remember { mutableIntStateOf(0) }
    fun changed() { version++ }
    var clientId by remember { mutableStateOf(s.clientId) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Reading the counter here makes the screen recompose (and keep focus) after a change.
        @Suppress("UNUSED_VARIABLE") val observed = version
        run {
            Section("Konto")
            if (name != null) {
                SettingRow("Angemeldet als", name!!, action = "Abmelden") { vm.logout() }
            } else {
                SettingRow("Twitch-Konto", "Nicht angemeldet", action = "Anmelden") { onLogin() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    singleLine = true,
                    label = { Text("Twitch Client-ID (dev.twitch.tv)") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { s.clientId = clientId }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Colors.Purple,
                        focusedTextColor = Colors.Text,
                        unfocusedTextColor = Colors.Text,
                    ),
                    modifier = Modifier.width(460.dp),
                )
                Spacer(Modifier.width(16.dp))
                PillButton("Speichern", onClick = { s.clientId = clientId; changed() })
            }

            Section("Wiedergabe")
            SettingRow("Werbeblocker", onOff(s.adBlock)) { s.adBlock = !s.adBlock; changed() }
            SettingRow("Ton aus, wenn Werbung nicht geblockt werden kann", onOff(s.muteAds)) { s.muteAds = !s.muteAds; changed() }
            SettingRow("Niedrige Latenz", onOff(s.lowLatency)) { s.lowLatency = !s.lowLatency; changed() }
            SettingRow("Standard-Qualität", s.defaultQuality.label) { s.defaultQuality = s.defaultQuality.next(); changed() }
            SettingRow("Statistiken im Player", onOff(s.showStats)) { s.showStats = !s.showStats; changed() }

            Section("Chat")
            SettingRow("Chat-Modus", s.chatMode.label) { s.chatMode = s.chatMode.next(); changed() }
            SettingRow("Schriftgröße", "${s.chatFontSize}") {
                s.chatFontSize = if (s.chatFontSize >= 19) 11 else s.chatFontSize + 2
                changed()
            }
            SettingRow("7TV- & BTTV-Emotes", onOff(s.thirdPartyEmotes)) { s.thirdPartyEmotes = !s.thirdPartyEmotes; changed() }

            Spacer(Modifier.height(16.dp))
            Text("PurpleTV ${BuildConfig.VERSION_NAME}", color = Colors.TextDim, fontSize = 12.sp)
            Text(
                "Fernbedienung im Player: ▲▼ Kanal wechseln · ▶ Chat umschalten · ◀ Statistiken · OK Menü",
                color = Colors.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

private fun onOff(b: Boolean) = if (b) "An" else "Aus"

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        color = Colors.PurpleLight,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingRow(label: String, value: String, action: String? = null, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusedScale = 1.01f,
        background = Colors.Surface,
        focusedBackground = Colors.SurfaceHigh,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Colors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = Colors.PurpleLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (action != null) {
                Spacer(Modifier.width(16.dp))
                Text(action, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
