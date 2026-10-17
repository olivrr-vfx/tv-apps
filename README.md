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
