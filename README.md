# PurpleTV – dein TV-Hub für Twitch und YouTube

PurpleTV ist eine eigene App für Android TV / Google TV (läuft auch auf Fire TV), in der mehrere
Streaming-Apps zusammenleben: **Twitch** und **YouTube**, weitere folgen. Ein **Dashboard** zeigt auf
einen Blick, wer live ist und was neu ist. Jede App lässt sich einzeln einschalten und bekommt eine
eigene Kachel auf dem TV-Startbildschirm. Dazu gibt es eine **Home-Assistant-Integration** und eine
**Fernbedienung fürs iPhone** (Web-App für den Home-Bildschirm).

Nativ in Kotlin mit Jetpack Compose und ExoPlayer (Media3), ohne WebView – startet schnell und spielt flüssig.

## Überblick

| Teil | Was es kann |
|---|---|
| **Dashboard** | Uhr, deine Apps mit Status („3 Kanäle live“, „5 neue Videos heute“), Live auf Twitch, Neu auf YouTube, Weiter schauen (beide Apps gemischt) |
| **Twitch** | Werbeblocker, Login per QR-Code, gefolgte Kanäle, Entdecken, Kategorien, Suche, VODs, Chat mit 7TV/BTTV, Kanal-Zapping, niedrige Latenz |
| **YouTube** | Ohne Google-Konto: Abos auf dem TV, Abo-Feed, Trends (Live, Gaming, Musik, Podcasts, Filme), Suche, Kanalseiten, Weiter schauen, Autoplay, Qualitätswahl |
| **Handy-Fernbedienung** | Steuerkreuz oder Wischfeld, Play/Pause, Lautstärke, Spulen, Twitch-Kanäle und YouTube-Videos direkt auf dem TV starten, YouTube-Abos verwalten |
| **Home Assistant** | Media-Player (mit Medien-Browser), Fernbedienung, App-Auswahl, Sensoren, Live-Sensoren pro Twitch-Kanal |
| **Design-System** | Eine Token-Quelle für TV-App, Handy und Home-Assistant-Theme ([design/README.md](design/README.md)) |

## Apps einzeln einschalten

Unter **Einstellungen → Allgemein** schaltest du Twitch und YouTube ein oder aus. Jede aktive App hat
eine eigene Kachel auf dem TV-Startbildschirm („PurpleTV Twitch“, „PurpleTV YouTube“) und startet dort
direkt – wie eine eigenständige App. Die Zurück-Taste verlässt sie dann auch direkt. Die Hauptkachel
„PurpleTV“ öffnet das Dashboard (oder, je nach Einstellung, die zuletzt genutzte App).

Links in der Leiste wechselst du zwischen Start, den Apps und den Einstellungen; innerhalb einer App
gibt es oben Reiter (bei Twitch z. B. Start, Live, Entdecken, Kategorien, Suche).

## Twitch

- **Werbeblocker**: erkennt die von Twitch eingefügte Werbung und holt sich einen werbefreien
  Ersatz-Stream. Gibt es keinen, werden die Werbe-Segmente herausgeschnitten (das Bild steht kurz),
  klappt beides nicht, wird die Werbung stumm geschaltet. Oben links steht, was gerade passiert.
- **Immer aktuelle Live-Liste**: Solange PurpleTV offen ist, wird die Liste der gefolgten Live-Kanäle
  jede Minute neu geladen, und sofort, wenn du zu PurpleTV zurückkehrst. Kanäle, die offline gegangen
  sind, verschwinden aus den Reihen und aus dem Kanal-Zapping. Läuft gerade ein Kanal, der endet,
  zeigt der Player das an und schaltet nach 10 Sekunden zum nächsten Live-Kanal (OK = bleiben).
- **Kanal-Zapping** mit ▲/▼ durch die (laufend aktualisierte) Liste, wie beim Fernsehen.
- **Chat** als Seitenleiste, Overlay oder aus; **Statistiken**; **VODs** mit Weiterschauen.

### Fernbedienung im Twitch-Player

| Taste | Aktion |
|---|---|
| ▲ / ▼ (oder CH+/CH−) | vorheriger / nächster Kanal |
| ▶ | Chat: Seitenleiste → Overlay → aus |
| ◀ | Statistiken an/aus |
| OK | Steuerung |
| Menü | Einstellungen (Qualität, Chat, Neu laden …) |

### Twitch-Login einrichten (einmalig, ca. 2 Minuten)

Für Login und gefolgte Kanäle braucht die App eine eigene Twitch Client-ID. Die ist kostenlos:

1. Auf https://dev.twitch.tv/console einloggen und **Register Your Application** öffnen.
2. Name: beliebig (z. B. `PurpleTV-<deinname>`), OAuth Redirect URL: `http://localhost`,
   Category: *Application Integration*, **Client Type: Public**.
3. Die angezeigte **Client-ID** kopieren.
4. Eintragen, entweder:
   - **Variante A (empfohlen):** im GitHub-Repo unter *Settings → Secrets and variables →
     Actions → Variables* eine Variable `TWITCH_CLIENT_ID` anlegen und den Build neu starten
     (*Actions → Build APK → Run workflow*). Die ID steckt dann fest in der APK.
   - **Variante B:** in PurpleTV unter *Einstellungen → Twitch → Twitch Client-ID* eintippen und speichern.
5. In der Twitch-App auf **Anmelden** gehen, den QR-Code scannen und den Code bestätigen.

Ohne Login kannst du über die Suche trotzdem jeden Kanal per exaktem Namen öffnen.

## YouTube

YouTube läuft **ohne Google-Konto** über [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
(die Bibliothek hinter NewPipe). Deine Abos speichert PurpleTV auf dem TV; der Abo-Feed kommt aus den
öffentlichen RSS-Feeds der Kanäle.

- **Abos hinzufügen**: Kanal suchen → Kanalseite → *Abonnieren*. Schneller geht es mit der
  Handy-Fernbedienung (*YouTube → Kanäle verwalten*): Links, `@Namen` oder Kanal-IDs einfügen, eine Zeile pro Kanal.
- **Start**: Weiter schauen, Neu von deinen Abos, Trends (Live, Gaming, Musik, Podcasts, Filme & Serien).
- **Player**: ◀▶ spulen (halten = schneller), OK = Steuerung, Menü = Qualität/Autoplay/Statistiken.
  Am Ende startet nach 8 Sekunden das nächste Video (abschaltbar).
- **Einstellungen**: maximale Qualität (Standard 1080p), Shorts ausblenden, Region für Trends.

Hinweis: YouTube ändert seine Schnittstellen regelmäßig. Wenn Videos nicht mehr laden, hilft meist
ein PurpleTV-Update mit einer neueren NewPipeExtractor-Version.

## Handy-Fernbedienung (iPhone & Android)

Der TV liefert selbst eine Web-App aus, die sich wie eine echte App auf den Home-Bildschirm legen lässt.

1. Auf dem TV: Dashboard → **Handy-Fernbedienung** (oder *Einstellungen → Fernbedienung & Home Assistant*).
2. Den QR-Code mit der iPhone-Kamera scannen – die Fernbedienung öffnet sich bereits gekoppelt.
3. In Safari: **Teilen → „Zum Home-Bildschirm“**. Fertig.

Das kann sie:

- **Fernbedienung**: Steuerkreuz mit OK (oder Wischfeld: wischen = bewegen, tippen = OK), Zurück,
  Start, Menü, Play/Pause, vorheriger/nächster Kanal bzw. Video, Spulen (±10/30 s), Ton aus, Lautstärke.
- **Läuft gerade**: Vorschaubild, Titel, Kanal, Fortschritt.
- **Twitch**: deine Live-Kanäle antippen = läuft auf dem TV; beliebigen Kanal per Namen öffnen.
- **YouTube**: auf dem Handy suchen und Videos auf dem TV starten, Abo-Feed, Kanäle abonnieren und verwalten.
- **TV**: zwischen Start, Twitch und YouTube wechseln.

Das iPhone muss im selben WLAN sein, und PurpleTV muss auf dem TV laufen.

## Installation (Sideload)

1. Auf dem TV: **Einstellungen → Geräteeinstellungen → Info → Build** 7× anklicken, um die
   Entwickleroptionen freizuschalten. Danach unter **Apps → Sicherheit** „Unbekannte Quellen“
   für die Downloader-App erlauben.
2. Aus dem Play Store die App **Downloader** (von AFTVnews) installieren.
3. In Downloader diese URL eingeben:

   ```
   https://github.com/olivrr-vfx/tv-apps/releases/latest/download/PurpleTV.apk
   ```

4. Installieren und öffnen.

Wer PurpleTV schon hat, bekommt die neue Version automatisch – Einstellungen, Twitch-Login und
Home-Assistant-Code bleiben erhalten.

### Automatische Updates

PurpleTV prüft beim Start selbst, ob es eine neue Version gibt, lädt sie herunter und installiert sie.
Beim ersten Update fragt Android einmalig, ob PurpleTV Apps installieren darf („Erlauben“ wählen).
Ab Android 12 laufen die weiteren Updates danach ohne Nachfrage. Abschalten kannst du das unter
*Einstellungen → Über PurpleTV → Automatische Updates*.

Alternativ per ADB: `adb connect <TV-IP>` und danach `adb install -r PurpleTV.apk`.

## Home Assistant

**Das bekommst du:**

| Entität | Was sie macht |
|---|---|
| `media_player.purpletv` | Zeigt Twitch-Stream, VOD oder YouTube-Video mit Titel, Kanal, Bild und Fortschritt. Kann Pause/Play, Ton, Lautstärke, Spulen, Kanal/Video vor/zurück und Inhalte öffnen. Mit **Medien-Browser** (Twitch live, YouTube-Abos, Apps). Als Quelle stehen deine gefolgten Live-Kanäle zur Auswahl. |
| `remote.purpletv_fernbedienung` | Tasten wie auf der Fernbedienung: `up`, `down`, `left`, `right`, `ok`, `back`, `menu`, `play_pause`, `channel_up`, `channel_down`, `home`. Aktivitäten: Start, Twitch, YouTube. |
| `select.purpletv_app` | Welche App offen ist – umschalten zwischen Start, Twitch und YouTube |
| `binary_sensor.purpletv_schaut` | `an`, solange ein Stream oder Video läuft (Twitch oder YouTube) |
| `sensor.purpletv_aktueller_kanal` | Kanal von dem, was gerade läuft |
| `sensor.purpletv_gefolgte_kanale_live` | Anzahl deiner gefolgten Twitch-Kanäle, die live sind (mit Liste) |
| `sensor.purpletv_neue_youtube_videos` | Videos deiner YouTube-Abos aus den letzten 24 Stunden (mit Liste) |
| `binary_sensor.twitch_kanale_<kanal>_live` | Pro gewähltem Kanal (z. B. Papaplatte): live ja/nein, mit Titel, Spiel und Zuschauern. **Funktioniert auch, wenn der TV aus ist.** |

Die Entitäten-IDs hängen von der Sprache von Home Assistant ab; bestehende Installationen behalten ihre IDs.

### Installation

1. In HACS: *Benutzerdefinierte Repositories* → `https://github.com/olivrr-vfx/tv-apps`, Typ *Integration* → **PurpleTV** installieren → Home Assistant neu starten.
2. PurpleTV auf dem TV öffnen. Home Assistant findet den TV automatisch, unter *Einstellungen → Geräte & Dienste* erscheint „PurpleTV gefunden“.
3. Den **Code** aus PurpleTV (*Einstellungen → Fernbedienung & Home Assistant*) eingeben. Optional Kanäle für Twitch-Live-Sensoren angeben, z. B. `papaplatte, zarbex`. Die Liste kannst du später über *Konfigurieren* ändern.

Wird der TV nicht automatisch gefunden: *Integration hinzufügen → PurpleTV* und IP-Adresse und Code von Hand eintragen.

### Inhalte starten

```yaml
action: media_player.play_media
target:
  entity_id: media_player.purpletv
data:
  media_content_type: video
  # Twitch: papaplatte, channel:papaplatte (Kanalseite), vod:123456, twitch.tv-Links
  # YouTube: youtube:dQw4w9WgXcQ, youtube.com/youtu.be-Links, youtube_search:lofi
  # Apps: app:twitch, app:youtube, app:dashboard
  media_content_id: https://youtu.be/dQw4w9WgXcQ
```

```yaml
action: remote.send_command
target:
  entity_id: remote.purpletv_fernbedienung
data:
  command: [down, down, ok]
```

### TV einschalten und direkt Papaplatte starten

Dafür brauchst du zusätzlich die offizielle Integration **Android TV Remote**. Sie kann den TV einschalten und PurpleTV über einen Link öffnen:

```yaml
script:
  papaplatte_schauen:
    alias: Papaplatte schauen
    icon: mdi:twitch
    sequence:
      - action: remote.turn_on
        target:
          entity_id: remote.wohnzimmer_tv   # deine Android-TV-Remote-Entität
      - delay: 3
      - action: remote.turn_on
        target:
          entity_id: remote.wohnzimmer_tv
        data:
          activity: purpletv://watch/papaplatte
```

Verfügbare Links:

| Link | Öffnet |
|---|---|
| `purpletv://home` | Dashboard |
| `purpletv://twitch`, `purpletv://youtube` | eine App |
| `purpletv://watch/<kanal>` | Twitch-Live-Stream |
| `purpletv://channel/<kanal>` | Twitch-Kanalseite mit VODs |
| `purpletv://vod/<id>` | Twitch-VOD |
| `purpletv://youtube/watch/<id>` | YouTube-Video |
| `purpletv://youtube/search?q=<text>` | YouTube-Suche |
| `purpletv://youtube/channel?url=<link>` | YouTube-Kanal |

### Beispiel: Benachrichtigung, wenn Papaplatte live geht

```yaml
automation:
  - alias: Papaplatte ist live
    triggers:
      - trigger: state
        entity_id: binary_sensor.twitch_kanale_papaplatte_live
        to: "on"
    actions:
      - action: notify.mobile_app_dein_handy
        data:
          title: "Papaplatte ist live!"
          message: "{{ state_attr('binary_sensor.twitch_kanale_papaplatte_live', 'title') }}"
```

### Theme

Das Design-System gibt es auch als Home-Assistant-Theme: [homeassistant/themes/purpletv.yaml](homeassistant/themes/purpletv.yaml).

### Gut zu wissen

- Die Steuerung geht nur, solange PurpleTV auf dem TV geöffnet ist. Ist der TV aus oder läuft eine
  andere App, steht der Media-Player auf „aus“. Zum Starten nimmst du dann den Deep-Link über Android TV Remote.
- Die Schnittstelle läuft nur im Heimnetz auf Port 8765 und ist mit dem Code geschützt. Abschalten
  oder einen neuen Code erzeugen kannst du in PurpleTV unter *Einstellungen → Fernbedienung & Home Assistant*.

### API

Alle Aufrufe brauchen den Code als `Authorization: Bearer <code>` (oder `?token=<code>`).

| Methode | Pfad | Parameter |
|---|---|---|
| GET | `/api/state` | – (was läuft, Apps, Live-Liste, YouTube-Feed) |
| GET | `/api/twitch/live`, `/api/youtube/feed`, `/api/youtube/subscriptions` | – |
| GET | `/api/youtube/search` | `q` |
| POST | `/api/play`, `/api/pause`, `/api/toggle`, `/api/next`, `/api/previous`, `/api/home` | – |
| POST | `/api/mute` / `/api/volume` / `/api/seek` | `mute` / `level` (0–1) / `position` oder `delta` (s) |
| POST | `/api/key` | `code`: up, down, left, right, ok, back, menu, play_pause, channel_up, channel_down |
| POST | `/api/app` | `name`: dashboard, twitch, youtube |
| POST | `/api/open` / `/api/channel` / `/api/vod` | `login` (oder `url`) / `login` / `id` |
| POST | `/api/youtube` / `/api/youtube/channel` / `/api/youtube/search` | `id` oder `url` / `url` / `q` |
| POST | `/api/youtube/subscribe` / `/api/youtube/unsubscribe` | `url` (Link, @Name, Kanal-ID) / `id` |

## Aufbau des Codes

```
app/src/main/java/dev/purpletv/
  design/    Design-System (Tokens, Theme, Komponenten, Player-Oberfläche)
  hub/       MainActivity, Navigation, Dashboard, Einstellungen, App-Kacheln
  core/      AppId (welche Apps es gibt), Load
  player/    gemeinsame ExoPlayer-Basis
  twitch/    api, chat, player (Werbeblocker), ui, TwitchRepository
  youtube/   api (NewPipeExtractor, RSS), player, ui, YouTubeRepository
  remote/    HTTP-Server (API + Web-App), Deep-Links
app/src/main/assets/web/   Handy-Fernbedienung und /design
custom_components/purpletv Home-Assistant-Integration
design/                    Tokens + Generator
```

Eine weitere App kommt so dazu: Eintrag in `core/AppId`, Akzent in `design/tokens.json` +
`Accent`, Markenzeichen `mark_<app>.xml`, Banner, `activity-alias` im Manifest, Bildschirme aus dem Design-System.

## Selbst bauen

```
./gradlew assembleRelease
python3 design/generate.py   # nach Änderungen an design/tokens.json
```

Die APK landet unter `app/build/outputs/apk/release/`. GitHub Actions baut bei jedem Push
automatisch und veröffentlicht die APK von `main` als Release.

Die APK wird mit dem mitgelieferten Schlüssel `app/purpletv-sideload.jks` signiert, damit
Updates sich ohne Deinstallation drüber installieren lassen. Der Schlüssel ist öffentlich.
Wer einen eigenen will, legt die Secrets `SIGNING_STORE_B64` (base64 der .jks),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD` an. Achtung: Beim
Wechsel des Schlüssels muss die App einmal deinstalliert werden.

## Hinweise

- Twitch ändert regelmäßig, wie Werbung ausgeliefert wird. Der Werbeblocker kann deshalb
  zeitweise schlechter funktionieren, bis er angepasst wird.
- Werbung zu blocken und YouTube ohne die offizielle App zu nutzen verstößt gegen die
  Nutzungsbedingungen von Twitch bzw. YouTube. Die App ist nur für den privaten Gebrauch gedacht.
  Wer Creator unterstützen will, kann das über Subs, Bits oder Mitgliedschaften tun.
- Twitch: interne GraphQL-API für die Wiedergabe (wie Streamlink oder Xtra), Helix-API für Follows
  und Suche. YouTube: NewPipeExtractor (GPLv3).
