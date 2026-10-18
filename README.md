# PurpleTV – schneller Twitch-Client für Android TV

Eigene Twitch-App für Android TV / Google TV (läuft auch auf Fire TV). Nativ in Kotlin mit
ExoPlayer (Media3) und Jetpack Compose gebaut, ohne WebView. Dadurch startet sie schnell und
die Wiedergabe läuft flüssig.

## Features

- **Werbeblocker**: erkennt die von Twitch in den Stream eingefügte Werbung. Bei einer
  Werbeunterbrechung holt sich die App einen werbefreien Ersatz-Stream. Gibt es keinen,
  schneidet sie die Werbe-Segmente heraus (das Bild steht dann kurz still). Klappt beides
  nicht, wird die Werbung stumm geschaltet. Oben links zeigt ein Hinweis an, was gerade passiert.
- **Login per QR-Code**: Code auf dem Handy bestätigen, kein Passwort-Getippe mit der Fernbedienung.
- **Gefolgte Kanäle**, die gerade live sind, beliebte Streams, Kategorien und Suche.
- **Niedrige Latenz**: Twitch-Low-Latency-Modus plus Prefetch-Segmente, typisch etwa 2–4 s Verzögerung.
- **Qualität** manuell wählen oder auf Auto lassen, Standard-Qualität einstellbar.
- **Chat** mit **7TV**- und **BTTV**-Emotes (auch animierte), als Seitenleiste, Overlay oder ausgeblendet.
- **Kanal-Zapping** mit ▲/▼ durch deine Liste, wie beim Fernsehen.
- **Statistiken**: Auflösung, Bitrate, Puffer, Latenz, verworfene Frames.
- **VODs** mit Spulen, Weiterschauen und Kanalseiten.
- **Home Assistant**: Media-Player, „Schaut Twitch“-Sensor, Live-Sensoren pro Kanal und Deep-Links (siehe unten).

### Fernbedienung im Player

| Taste | Aktion |
|---|---|
| ▲ / ▼ (oder CH+/CH−) | vorheriger / nächster Kanal |
| ▶ | Chat: Seitenleiste → Overlay → aus |
| ◀ | Statistiken an/aus |
| OK | Menü (Qualität, Chat, Neu laden …) |
| Zurück | Menü schließen / zur Übersicht |

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

### Automatische Updates

PurpleTV prüft beim Start selbst, ob es eine neue Version gibt, lädt sie herunter und
installiert sie. Beim ersten Update fragt Android einmalig, ob PurpleTV Apps installieren darf
(„Erlauben“ wählen). Ab Android 12 laufen die weiteren Updates danach ohne Nachfrage. Auf
älteren Geräten musst du die Installation jeweils mit einem Klick bestätigen. Abschalten
kannst du das unter *Einstellungen → Automatische Updates*.

Alternativ per ADB: `adb connect <TV-IP>` und danach `adb install -r PurpleTV.apk`.

## Twitch-Login einrichten (einmalig, ca. 2 Minuten)

Für Login und gefolgte Kanäle braucht die App eine eigene Twitch Client-ID. Die ist kostenlos:

1. Auf https://dev.twitch.tv/console einloggen und **Register Your Application** öffnen.
2. Name: beliebig (z. B. `PurpleTV-<deinname>`), OAuth Redirect URL: `http://localhost`,
   Category: *Application Integration*, **Client Type: Public**.
3. Die angezeigte **Client-ID** kopieren.
4. Eintragen, entweder:
   - **Variante A (empfohlen):** im GitHub-Repo unter *Settings → Secrets and variables →
     Actions → Variables* eine Variable `TWITCH_CLIENT_ID` anlegen und den Build neu starten
     (*Actions → Build APK → Run workflow*). Die ID steckt dann fest in der APK.
   - **Variante B:** in der App unter *Einstellungen → Twitch Client-ID* eintippen und speichern.
5. In der App auf **Anmelden** gehen, den QR-Code scannen und den Code bestätigen.

Ohne Login kannst du über die Suche trotzdem jeden Kanal per exaktem Namen öffnen.

## Home Assistant

PurpleTV lässt sich mit Home Assistant steuern und liefert Sensoren.

**Das bekommst du:**

| Entität | Was sie macht |
|---|---|
| `media_player.purpletv` | Zeigt Kanal, Titel und Vorschaubild. Kann Pause/Play, Ton aus, Lautstärke, Kanal vor/zurück und einen Kanal oder VOD öffnen. Als Quelle stehen deine gefolgten Live-Kanäle zur Auswahl. |
| `binary_sensor.purpletv_schaut_twitch` | `an`, solange ein Stream oder VOD läuft |
| `sensor.purpletv_aktueller_kanal` | Name des laufenden Kanals |
| `sensor.purpletv_gefolgte_kanale_live` | Anzahl deiner gefolgten Kanäle, die live sind (mit Liste als Attribut) |
| `binary_sensor.twitch_kanale_<kanal>_live` | Pro gewähltem Kanal (z. B. Papaplatte): live ja/nein, mit Titel, Spiel und Zuschauern. **Funktioniert auch, wenn der TV aus ist.** |

### Installation

1. In HACS: *Benutzerdefinierte Repositories* → `https://github.com/olivrr-vfx/tv-apps`, Typ *Integration* → **PurpleTV** installieren → Home Assistant neu starten.
2. PurpleTV auf dem TV öffnen. Home Assistant findet den TV automatisch, unter *Einstellungen → Geräte & Dienste* erscheint „PurpleTV gefunden“.
3. Den **Code** aus PurpleTV (*Einstellungen → Home Assistant*) eingeben. Optional Kanäle für Live-Sensoren angeben, z. B. `papaplatte, zarbex`. Die Liste kannst du später über *Konfigurieren* ändern.

Wird der TV nicht automatisch gefunden: *Integration hinzufügen → PurpleTV* und IP-Adresse und Code von Hand eintragen.

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
| `purpletv://watch/<kanal>` | Live-Stream |
| `purpletv://channel/<kanal>` | Kanalseite mit VODs |
| `purpletv://vod/<id>` | Ein VOD |
| `purpletv://home` | Startseite |

Läuft PurpleTV schon, geht es auch ohne Android TV Remote:

```yaml
action: media_player.play_media
target:
  entity_id: media_player.purpletv
data:
  media_content_type: channel
  media_content_id: papaplatte    # oder channel:papaplatte, vod:123456, twitch.tv-Link
```

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
          data:
            actions:
              - action: PAPAPLATTE_SCHAUEN
                title: Auf dem TV schauen
```

### Gut zu wissen

- Die Steuerung geht nur, solange PurpleTV auf dem TV geöffnet ist. Ist der TV aus oder läuft eine andere App, steht der Media-Player auf „aus“. Zum Starten nimmst du dann den Deep-Link über Android TV Remote.
- Die Schnittstelle läuft nur im Heimnetz auf Port 8765 und ist mit dem Code geschützt. Abschalten oder einen neuen Code erzeugen kannst du in PurpleTV unter *Einstellungen → Home Assistant*.

## Selbst bauen

```
./gradlew assembleRelease
```

Die APK landet unter `app/build/outputs/apk/release/`. GitHub Actions baut bei jedem Push
automatisch und veröffentlicht die APK als Release.

Die APK wird mit dem mitgelieferten Schlüssel `app/purpletv-sideload.jks` signiert, damit
Updates sich ohne Deinstallation drüber installieren lassen. Der Schlüssel ist öffentlich.
Wer einen eigenen will, legt die Secrets `SIGNING_STORE_B64` (base64 der .jks),
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD` an. Achtung: Beim
Wechsel des Schlüssels muss die App einmal deinstalliert werden.

## Hinweise

- Twitch ändert regelmäßig, wie Werbung ausgeliefert wird. Der Werbeblocker kann deshalb
  zeitweise schlechter funktionieren, bis er angepasst wird.
- Werbung zu blocken verstößt gegen die Nutzungsbedingungen von Twitch. Die App ist nur für
  den privaten Gebrauch gedacht. Wer Streamer unterstützen will, kann das über Subs oder Bits tun.
- Die App nutzt für die Wiedergabe die interne GraphQL-API von Twitch (wie Streamlink oder
  Xtra) und für Follows und Suche die offizielle Helix-API.
