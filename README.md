<p align="center"><img src="app/src/main/assets/web/icon-180.png" width="96" alt="Couch"></p>

<h1 align="center">Couch</h1>

<p align="center">Deine TV-App für Android TV und Google TV: Twitch und YouTube in einer App,<br>
mit Dashboard, Steuerung vom iPhone oder Mac und Home Assistant.</p>

---

## Was drin ist

| | |
|---|---|
| **Dashboard** | Uhr, deine Apps mit Status („3 Kanäle live“), Live auf Twitch, Neu auf YouTube, Weiter schauen |
| **PurpleTV** – die Twitch-App | Werbeblocker, Login per QR-Code, gefolgte Kanäle (immer aktuell), Kanal-Zapping, Chat mit 7TV/BTTV, VODs |
| **YouTube** | Ohne Google-Konto: Abos auf dem TV, Abo-Feed, Trends, Suche, Kanäle, Weiterschauen, Autoplay |
| **Couch auf Handy & Mac** | Fernbedienung, stöbern, im Browser mitschauen, Links senden, Fotos/Videos zeigen, Bildschirm teilen |
| **Home Assistant** | Media-Player mit Medien-Browser, Fernbedienung, App-Auswahl, Sensoren, Live-Sensoren pro Twitch-Kanal |

Jede App lässt sich einzeln ein- und ausschalten (*Einstellungen → Allgemein*) und hat dann eine
eigene Kachel auf dem TV-Startbildschirm („PurpleTV“, „Couch YouTube“). Die Kachel „Couch“ öffnet das Dashboard.

## Installieren

1. Auf dem TV unter **Einstellungen → Geräteeinstellungen → Info → Build** 7× klicken (Entwickleroptionen),
   dann unter **Apps → Sicherheit** „Unbekannte Quellen“ für die App **Downloader** erlauben.
2. In Downloader diese Adresse eingeben:

   ```
   https://github.com/olivrr-vfx/tv-apps/releases/latest/download/Couch.apk
   ```

**Updates kommen von selbst.** Couch prüft beim Öffnen und danach stündlich auf eine neue Version und
installiert sie. Beim ersten Mal fragt Android, ob Couch Apps installieren darf → „Erlauben“.
Sofort prüfen: *Einstellungen → Über Couch → Nach Updates suchen*. Wer noch „PurpleTV“ installiert hat,
bekommt Couch als normales Update – Login, Einstellungen und Home-Assistant-Code bleiben.

## PurpleTV (Twitch)

- **Werbeblocker**: holt bei Werbung einen werbefreien Ersatz-Stream, sonst werden die Werbe-Segmente
  übersprungen; klappt beides nicht, ist die Werbung stumm. Oben links steht, was passiert.
- **Live-Liste immer aktuell**: lädt jede Minute neu und sofort, wenn du zurückkommst. Kanäle, die
  offline gehen, verschwinden; läuft gerade einer, der endet, wechselt der Player nach 10 s zum nächsten
  Live-Kanal (OK = bleiben).
- **Tasten im Player**: ▲▼ Kanal wechseln · ▶ Chat · ◀ Statistiken · OK Steuerung · Menü Einstellungen.

<details>
<summary><b>Twitch-Login einrichten</b> (einmalig, ca. 2 Minuten)</summary>

Für Login und gefolgte Kanäle braucht Couch eine eigene, kostenlose Twitch Client-ID:

1. Auf https://dev.twitch.tv/console → **Register Your Application**.
2. Name beliebig, OAuth Redirect URL `http://localhost`, Category *Application Integration*, **Client Type: Public**.
3. Die **Client-ID** entweder im Repo als Actions-Variable `TWITCH_CLIENT_ID` anlegen und neu bauen,
   oder in Couch unter *Einstellungen → PurpleTV (Twitch) → Twitch Client-ID* eintragen.
4. In PurpleTV auf **Anmelden**, QR-Code scannen, Code bestätigen.

Ohne Login lassen sich Kanäle über die Suche per Namen öffnen.
</details>

## YouTube

Läuft ohne Google-Konto über [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
Abos speichert Couch auf dem TV, der Feed kommt aus den öffentlichen Kanal-Feeds. Abos fügst du auf der
Kanalseite hinzu – oder schneller am Handy/Mac unter *YouTube → Kanäle verwalten* (Links oder `@Namen`, eine Zeile pro Kanal).

YouTube ändert seine Schnittstellen öfter. Lädt ein Video nicht, hilft meist das nächste Couch-Update.

## Couch auf Handy & Mac

Der TV liefert eine eigene Web-App aus. Auf dem TV: **Dashboard → „Handy & Mac“** zeigt QR-Code und Adressen.

- **iPhone**: QR-Code scannen, in Safari *Teilen → „Zum Home-Bildschirm“*. Fertig, Couch ist eine App.
- **Mac/PC**: `http://<TV-IP>:8765` in Chrome öffnen. Große Ansicht mit Navigation, Inhalten und Fernbedienung
  rechts; die **Pfeiltasten** steuern den TV, Enter = OK, Esc = Zurück, Leertaste = Play/Pause.

| Funktion | So geht's |
|---|---|
| Stöbern & starten | Klick auf eine Karte startet sie auf dem TV |
| **Im Browser mitschauen** | „Hier ansehen“ auf einer Karte oder „Hier mitschauen“ beim laufenden Programm. Twitch kommt über den TV (Werbung wird gefiltert), YouTube über den YouTube-Player |
| Links teilen | „Teilen“ auf Karten (iPhone: Teilen-Menü, Mac: Link kopieren) |
| **Link an den TV senden** | *Teilen → Link senden*: YouTube-/Twitch-Link oder Kanalname einfügen |
| Aus Chrome mit einem Klick | *Teilen*: Knopf „Auf Couch abspielen“ in die Lesezeichenleiste ziehen, auf youtube.com/twitch.tv klicken |
| Aus der iPhone-YouTube-App | Kurzbefehl „Auf Couch abspielen“ fürs Teilen-Menü (Anleitung unter *Teilen*) |
| **Fotos & Videos zeigen** | *Teilen → Fotos & Videos*; am TV blättert ◀ ▶ |
| **Bildschirm teilen** (Mac/PC) | *Teilen → Bildschirm teilen*. Läuft über die sichere Adresse `https://<TV-IP>:8766`; Chrome warnt beim ersten Mal, weil der TV sein eigenes Zertifikat hat → *Erweitert → Weiter*. Geht direkt im WLAN, ohne Cloud |
| Kamera teilen (iPhone/Mac) | *Teilen → Kamera teilen* (ebenfalls über die sichere Adresse) |

Den iPhone-Bildschirm selbst kann eine Web-App nicht aufnehmen – das erlaubt iOS nur AirPlay.
Handy oder Mac müssen im selben WLAN sein und Couch muss auf dem TV laufen.

## Home Assistant

Installation über HACS: *Benutzerdefinierte Repositories* → `https://github.com/olivrr-vfx/tv-apps`
(Typ *Integration*) → **Couch** installieren → neu starten. Der TV wird automatisch gefunden; den Code
gibt es auf dem TV unter *Einstellungen → Fernbedienung & Home Assistant*.

| Entität | Was sie macht |
|---|---|
| `media_player.couch` | Was läuft (Twitch, VOD, YouTube) mit Bild und Fortschritt; Play/Pause, Lautstärke, Spulen, Inhalte öffnen, **Medien-Browser** |
| `remote.couch_fernbedienung` | Tasten `up` `down` `left` `right` `ok` `back` `menu` `play_pause` `home`; Aktivitäten Start, PurpleTV, YouTube |
| `select.couch_app` | Zwischen Start, PurpleTV und YouTube wechseln |
| `binary_sensor.couch_schaut` | an, solange etwas läuft |
| `sensor.couch_aktueller_kanal`, `sensor.couch_gefolgte_kanale_live`, `sensor.couch_neue_youtube_videos` | Was läuft, wer live ist, was neu ist |
| `binary_sensor.twitch_kanale_<kanal>_live` | Live-Status gewählter Kanäle – auch wenn der TV aus ist |

Bestehende Installationen behalten ihre Entitäts-IDs (`…purpletv…`).

```yaml
# Video oder Stream starten
action: media_player.play_media
target: { entity_id: media_player.couch }
data:
  media_content_type: video
  media_content_id: https://youtu.be/dQw4w9WgXcQ   # oder papaplatte, vod:123, youtube_search:lofi, app:youtube
```

TV einschalten und direkt etwas starten geht zusammen mit der Integration **Android TV Remote**:
`remote.turn_on` mit `activity: couch://watch/papaplatte`.

<details>
<summary><b>Links</b> (<code>couch://</code>, <code>purpletv://</code> funktioniert weiterhin)</summary>

| Link | Öffnet |
|---|---|
| `couch://home` | Dashboard |
| `couch://twitch`, `couch://youtube` | eine App |
| `couch://watch/<kanal>` · `couch://channel/<kanal>` · `couch://vod/<id>` | Twitch live · Kanalseite · VOD |
| `couch://youtube/watch/<id>` · `couch://youtube/search?q=<text>` | YouTube-Video · Suche |
</details>

<details>
<summary><b>API</b> (für eigene Automationen)</summary>

Alle Aufrufe auf `http://<TV-IP>:8765` brauchen den Code als `Authorization: Bearer <code>` oder `?token=<code>`.

| Methode | Pfad | Parameter |
|---|---|---|
| GET | `/api/state` | – (was läuft, Apps, Live-Liste, YouTube-Feed) |
| GET | `/api/history`, `/api/twitch/live`, `/api/twitch/top`, `/api/youtube/feed`, `/api/youtube/shelves`, `/api/youtube/subscriptions` | – |
| GET | `/api/twitch/search`, `/api/youtube/search` | `q` |
| POST | `/api/play` `/api/pause` `/api/toggle` `/api/next` `/api/previous` `/api/home` | – |
| POST | `/api/mute` · `/api/volume` · `/api/seek` | `mute` · `level` (0–1) · `position` oder `delta` (s) |
| POST | `/api/key` | `code`: up, down, left, right, ok, back, menu, play_pause, channel_up, channel_down |
| POST | `/api/app` | `name`: dashboard, twitch, youtube |
| POST | `/api/open` | `url` (YouTube/Twitch-Link) oder `login` |
| POST | `/api/channel` · `/api/vod` · `/api/youtube` · `/api/youtube/search` | `login` · `id` · `id`/`url` · `q` |
| POST | `/api/youtube/subscribe` · `/api/youtube/unsubscribe` | `url` (Link, @Name, Kanal-ID) · `id` |
| POST | `/api/media` | Multipart-Feld `file` (Foto/Video) |
</details>

## Für Entwickler

```
app/src/main/java/dev/purpletv/
  hub/       Couch: MainActivity, Navigation, Dashboard, Einstellungen, Teilen-Bildschirme
  design/    Design-System (Tokens, Theme, Komponenten, Player-Oberfläche)
  twitch/    PurpleTV: api, chat, player (Werbeblocker), ui
  youtube/   api (NewPipeExtractor, RSS), player, ui
  remote/    Web-Server (API, Web-App, Browser-Wiedergabe, Bildschirm teilen), Deep-Links
app/src/main/assets/web/   Web-App für Handy und Mac, /design
custom_components/purpletv Home-Assistant-Integration
design/                    Tokens + Generatoren (siehe design/README.md)
```

```
./gradlew assembleRelease     # APK
python3 design/generate.py    # nach Änderungen an design/tokens.json
python3 design/brand.py       # nach Änderungen an den Logos
```

GitHub Actions baut jeden Push; `main` wird automatisch als Release veröffentlicht. Die APK ist mit
dem mitgelieferten, öffentlichen Schlüssel `app/purpletv-sideload.jks` signiert, damit Updates
drüber installieren. Eigener Schlüssel: Secrets `SIGNING_STORE_B64`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (Wechsel erfordert einmal Deinstallieren).

## Hinweise

- Werbung blocken und YouTube ohne offizielle App zu nutzen verstößt gegen die Nutzungsbedingungen
  von Twitch bzw. YouTube. Couch ist für den privaten Gebrauch. Creator unterstützt man mit Subs, Bits oder Mitgliedschaften.
- Twitch: interne GraphQL-API für die Wiedergabe, Helix für Follows/Suche. YouTube: NewPipeExtractor (GPLv3).
  Browser-Player: [hls.js](https://github.com/video-dev/hls.js) (Apache 2.0).
