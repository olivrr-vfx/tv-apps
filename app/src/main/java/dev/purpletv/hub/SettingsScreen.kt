package dev.purpletv.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.purpletv.BuildConfig
import dev.purpletv.PurpleApp
import dev.purpletv.core.AppId
import dev.purpletv.data.DefaultQuality
import dev.purpletv.design.Accent
import dev.purpletv.design.AccentScope
import dev.purpletv.design.ButtonStyle
import dev.purpletv.design.Chip
import dev.purpletv.design.InfoLine
import dev.purpletv.design.PageHeader
import dev.purpletv.design.Palette
import dev.purpletv.design.SettingRow
import dev.purpletv.design.SettingsSection
import dev.purpletv.design.Tokens
import dev.purpletv.design.TvButton
import dev.purpletv.design.TvTextField
import dev.purpletv.design.Type
import dev.purpletv.remote.RemoteServer
import dev.purpletv.update.Updater
import kotlinx.coroutines.launch

private enum class Category(val label: String, val icon: ImageVector?, val app: AppId? = null) {
    GENERAL("Allgemein", Icons.Filled.Apps),
    TWITCH("Twitch", null, AppId.TWITCH),
    YOUTUBE("YouTube", null, AppId.YOUTUBE),
    REMOTE("Fernbedienung & Home Assistant", Icons.Filled.Home),
    ABOUT("Über PurpleTV", Icons.Filled.Info),
}

private val regions = listOf("DE", "AT", "CH", "US", "GB", "FR", "NL")

@Composable
fun SettingsScreen(app: PurpleApp, nav: Navigator, onPhoneRemote: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(Category.GENERAL) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Einstellungen", overline = "PurpleTV")
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().padding(start = Tokens.Layout.safeHorizontal),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
            ) {
                Category.entries.forEach { c ->
                    Chip(c.label, selected = c == category, onClick = { category = c }, icon = c.icon, onFocus = { category = c })
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(start = Tokens.Space.xl, end = Tokens.Layout.safeHorizontal, bottom = Tokens.Space.xxxl),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
            ) {
                when (category) {
                    Category.GENERAL -> GeneralSettings(app)
                    Category.TWITCH -> AccentScope(Accent.Twitch) { TwitchSettings(app, nav) }
                    Category.YOUTUBE -> AccentScope(Accent.YouTube) { YouTubeSettings(app) }
                    Category.REMOTE -> RemoteSettings(app, onPhoneRemote)
                    Category.ABOUT -> AboutSettings(app)
                }
            }
        }
    }
}

/** Bumped on every change so the rows re-read the SharedPreferences values. */
@Composable
private fun rememberRefresh(): Pair<Int, () -> Unit> {
    var version by remember { mutableIntStateOf(0) }
    return version to { version++ }
}

private fun onOff(b: Boolean) = if (b) "An" else "Aus"

@Composable
private fun GeneralSettings(app: PurpleApp) {
    val s = app.settings
    val (version, changed) = rememberRefresh()
    @Suppress("UNUSED_VARIABLE") val observed = version
    SettingsSection("Apps", description = "Ausgeschaltete Apps verschwinden aus PurpleTV und vom TV-Startbildschirm.")
    AppId.entries.forEach { id ->
        SettingRow(id.label, toggle = s.isEnabled(id), description = "Eigene Kachel auf dem TV-Startbildschirm", onClick = {
            app.setAppEnabled(id, !s.isEnabled(id))
            changed()
        })
    }
    SettingsSection("Start")
    SettingRow("Beim Öffnen von PurpleTV zeigen", value = s.startScreen.label, onClick = { s.startScreen = s.startScreen.next(); changed() })
}

@Composable
private fun TwitchSettings(app: PurpleApp, nav: Navigator) {
    val s = app.settings
    val (version, changed) = rememberRefresh()
    @Suppress("UNUSED_VARIABLE") val observed = version
    val name by app.twitch.loggedInName.collectAsState()
    var clientId by remember { mutableStateOf(s.clientId) }

    SettingsSection("Konto")
    if (name != null) {
        SettingRow("Angemeldet als $name", value = "Abmelden", onClick = { app.twitch.logout() })
    } else {
        SettingRow("Twitch-Konto", value = "Anmelden", description = "Nötig für gefolgte Kanäle", onClick = { nav.push(Route.TwitchLogin) })
    }
    s.lastAuthError?.let { Text("Letztes Login-Problem: $it", color = Palette.textFaint, style = Type.bodySmall) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvTextField(clientId, { clientId = it }, label = "Twitch Client-ID (dev.twitch.tv)", onSubmit = { s.clientId = clientId; changed() }, modifier = Modifier.width(460.dp))
        Spacer(Modifier.width(Tokens.Space.lg))
        TvButton("Speichern", onClick = { s.clientId = clientId; changed() })
    }

    SettingsSection("Wiedergabe")
    SettingRow("Werbeblocker", toggle = s.adBlock, onClick = { s.adBlock = !s.adBlock; changed() })
    SettingRow("Ton aus, wenn Werbung nicht geblockt werden kann", toggle = s.muteAds, onClick = { s.muteAds = !s.muteAds; changed() })
    SettingRow("Niedrige Latenz", toggle = s.lowLatency, onClick = { s.lowLatency = !s.lowLatency; changed() })
    SettingRow("Standard-Qualität", value = s.defaultQuality.label, onClick = { s.defaultQuality = s.defaultQuality.next(); changed() })
    SettingRow("Statistiken im Player", toggle = s.showStats, onClick = { s.showStats = !s.showStats; changed() })
    SettingRow("Live-Vorschau auf der Startseite", toggle = s.livePreview, onClick = { s.livePreview = !s.livePreview; changed() })

    SettingsSection("Chat")
    SettingRow("Chat-Modus", value = s.chatMode.label, onClick = { s.chatMode = s.chatMode.next(); changed() })
    SettingRow("Schriftgröße", value = "${s.chatFontSize}", onClick = {
        s.chatFontSize = if (s.chatFontSize >= 19) 11 else s.chatFontSize + 2
        changed()
    })
    SettingRow("7TV- & BTTV-Emotes", toggle = s.thirdPartyEmotes, onClick = { s.thirdPartyEmotes = !s.thirdPartyEmotes; changed() })

    SettingsSection("Daten")
    SettingRow("Live-Liste jetzt aktualisieren", description = "Passiert sonst jede Minute, solange PurpleTV offen ist", onClick = { app.twitch.refreshAll() })
}

@Composable
private fun YouTubeSettings(app: PurpleApp) {
    val s = app.settings
    val (version, changed) = rememberRefresh()
    @Suppress("UNUSED_VARIABLE") val observed = version
    val subs by app.youtube.subscriptions.collectAsState()

    SettingsSection("Wiedergabe")
    SettingRow("Maximale Qualität", value = s.youtubeQuality.label, onClick = {
        // "Quelle" makes little sense for YouTube; cycle through the fixed heights and Auto.
        var next = s.youtubeQuality.next()
        if (next == DefaultQuality.SOURCE) next = next.next()
        s.youtubeQuality = next
        changed()
    })
    SettingRow("Nächstes Video automatisch abspielen", toggle = s.youtubeAutoplay, onClick = { s.youtubeAutoplay = !s.youtubeAutoplay; changed() })
    SettingRow("Shorts ausblenden", toggle = s.youtubeHideShorts, onClick = {
        s.youtubeHideShorts = !s.youtubeHideShorts
        changed()
        app.youtube.refreshFeed()
        app.youtube.refreshShelves()
    })
    SettingRow("Region für Trends", value = s.youtubeRegion, onClick = {
        s.youtubeRegion = regions[(regions.indexOf(s.youtubeRegion) + 1) % regions.size]
        changed()
        app.youtube.refreshShelves()
    })

    SettingsSection("Abos", description = "Ohne Google-Konto: Abos werden nur auf diesem TV gespeichert.")
    InfoLine("Abonnierte Kanäle", "${subs.size}")
    SettingRow("Abo-Feed jetzt aktualisieren", onClick = { app.youtube.refreshFeed() })
    subs.forEach { ch ->
        SettingRow(ch.name, value = "Entfernen", onClick = { app.youtube.unsubscribe(ch.id) })
    }
}

@Composable
private fun RemoteSettings(app: PurpleApp, onPhoneRemote: () -> Unit) {
    val s = app.settings
    val (version, changed) = rememberRefresh()
    @Suppress("UNUSED_VARIABLE") val observed = version
    SettingsSection("Steuerung im Heimnetz", description = "Für Home Assistant und die Handy-Fernbedienung.")
    SettingRow("Steuerung im Heimnetz", toggle = s.remoteEnabled, onClick = {
        s.remoteEnabled = !s.remoteEnabled
        if (s.remoteEnabled) app.startRemote() else app.stopRemote()
        changed()
    })
    if (s.remoteEnabled) {
        val ip = remember { RemoteServer.localAddresses().firstOrNull() ?: "?" }
        InfoLine("Adresse", "$ip:${RemoteServer.PORT}")
        SettingRow("Code", value = s.remoteCode, description = "Neu erzeugen meldet alle Geräte ab", onClick = { s.newRemoteCode(); changed() })
        SettingRow("Handy-Fernbedienung einrichten", icon = Icons.Filled.PhoneIphone, chevron = true, onClick = onPhoneRemote)
        Text(
            "Home Assistant findet den TV automatisch (Integration „PurpleTV“ über HACS). Den Code gibst du beim Einrichten ein.",
            color = Palette.textFaint,
            style = Type.bodySmall,
        )
    }
}

@Composable
private fun AboutSettings(app: PurpleApp) {
    val s = app.settings
    val (version, changed) = rememberRefresh()
    @Suppress("UNUSED_VARIABLE") val observed = version
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    SettingsSection("Updates")
    SettingRow("Automatische Updates", toggle = s.autoUpdate, onClick = { s.autoUpdate = !s.autoUpdate; changed() })
    SettingRow("Version ${BuildConfig.VERSION_NAME}", value = "Nach Updates suchen", icon = Icons.Filled.SystemUpdate, onClick = {
        scope.launch { Updater.checkAndInstall(context, manual = true) }
    })
    SettingsSection("Tasten")
    InfoLine("Twitch live", "▲▼ Kanal · ▶ Chat · ◀ Statistiken · OK Steuerung")
    InfoLine("Videos", "◀▶ spulen (halten = schneller) · OK Steuerung")
    InfoLine("Überall", "Menü-Taste: Player-Einstellungen")
    SettingsSection("Design-System")
    val ip = remember { RemoteServer.localAddresses().firstOrNull() ?: "<TV-IP>" }
    InfoLine("Im Browser ansehen", "http://$ip:${RemoteServer.PORT}/design")
}
