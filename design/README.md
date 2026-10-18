# PurpleTV Design-System

Ein Design-System für alles, was zu PurpleTV gehört: die TV-App (Jetpack Compose), die
Handy-Fernbedienung (Web-App) und Home Assistant (Theme).

```
design/tokens.json  ──►  design/generate.py  ──►  app/.../design/Tokens.kt        (TV-App)
                                             ├─►  app/src/main/assets/web/tokens.css (Handy, /design)
                                             └─►  homeassistant/themes/purpletv.yaml (Home Assistant)
```

Werte ändert man **nur** in `tokens.json` und lässt danach `python3 design/generate.py` laufen.
CI prüft mit `--check`, dass die erzeugten Dateien aktuell sind. Die Referenzseite mit allen
Farben, Schriftgrößen und Komponenten zeigt der TV unter `http://<TV-IP>:8765/design`.

## Grundsätze

1. **10-Fuß-Oberfläche.** Alles muss vom Sofa aus lesbar sein: Fließtext ≥ 15 sp, Seitenränder
   48 dp (Overscan-sicher), große Klickflächen.
2. **Fokus ist das Wichtigste.** Das fokussierte Element ist immer das hellste auf dem Bildschirm:
   Buttons werden weiß mit dunkler Schrift, Karten bekommen einen weißen Ring (3 dp) und wachsen um 6 %.
3. **Ein Hub, viele Apps.** Neutrale, dunkle Flächen; Farbe kommt vom Akzent der jeweiligen App
   (Hub-Violett, Twitch-Lila, YouTube-Rot). Komponenten lesen den Akzent aus `LocalAccent`, man
   setzt ihn mit `AccentScope(Accent.Twitch) { … }`.
4. **Billig für TV-GPUs.** Keine Schatten, keine Unschärfe; Animationen nur über `graphicsLayer`
   (Skalierung, Alpha).

## Tokens

| Gruppe | Beispiele | Verwendung |
|---|---|---|
| `color` | `canvas`, `surface`, `surfaceRaised`, `textPrimary`, `focus`, `hub`, `twitch`, `youtube`, `live` | Flächen in Stufen (canvas → surfaceHighest), Text in drei Stufen, App-Akzente, Status |
| `space` | `xs` 4 · `sm` 8 · `md` 12 · `lg` 16 · `xl` 24 · `xxl` 32 · `xxxl` 48 | Abstände |
| `radius` | `xs` 4 · `sm` 8 · `md` 12 · `lg` 16 · `xl` 24 · `pill` | Ecken: Badges `xs`, Karten `md`, Kacheln `xl`, Buttons `pill` |
| `type` | `display`, `headline`, `title`, `titleSmall`, `body`, `bodySmall`, `label`, `caption`, `overline` | Schriftgrößen inkl. Zeilenhöhe, Gewicht, Laufweite |
| `motion` | `fast` 120 · `normal` 200 · `slow` 320 ms | Animationsdauern |
| `focus` | `ringWidth`, `scaleCard`, `scaleButton`, `scaleRow` | Fokus-Verhalten |
| `layout` | `safeHorizontal`, `railCollapsed`, `cardWide`, `appTile`, … | Feste Maße der TV-Oberfläche |

## Komponenten (Compose, `dev.purpletv.design`)

| Komponente | Wofür |
|---|---|
| `Focusable` | Basis jedes klickbaren Elements (Fokus-Ring, Skalierung) |
| `TvButton` (`Primary`, `Secondary`, `Ghost`), `Chip`, `RoundIconButton`, `IconTile` | Aktionen |
| `MediaCard` (+ `CardBadges`), `PosterCard`, `AvatarCard`, `AppTile`, `UtilityTile` | Inhalte |
| `LiveBadge`, `Badge`, `AppTag`, `Avatar`, `Thumbnail`, `AppMark`, `HubMark` | Bausteine |
| `PageHeader`, `SectionHeader`, `ContentRow`, `TopTabs`, `NavRail` | Seitenaufbau und Navigation |
| `LoadContent`, `LoadingState`, `EmptyState`, `ErrorState` | Lade-, Leer- und Fehlerzustände |
| `SettingsSection`, `SettingRow` (Wert, Schalter, Pfeil), `InfoLine`, `TvTextField` | Einstellungen und Eingaben |
| `TvDialog`, `CornerToast`, `QrCode` | Overlays |
| `VideoSurface`, `ControlsOverlay`, `SidePanel`, `SeekBar`, `StatusOverlay`, `StatsBox` | Player-Oberfläche |
| `FocusMemory`, `RestorableFocus`, `Modifier.initialFocus()` | Fokus wiederherstellen |

Eine neue App in PurpleTV braucht damit nur: einen Eintrag in `AppId`, einen `Accent`, ein
Markenzeichen (`res/drawable/mark_<app>.xml`) und ihre Bildschirme aus diesen Komponenten.

## Web (Handy-Fernbedienung)

`tokens.css` stellt alle Tokens als CSS-Variablen bereit, z. B. `var(--pt-color-surface)`,
`var(--pt-space-lg)`, `var(--pt-type-title-size)`. `app.css` verwendet ausschließlich diese.

## Home Assistant

`homeassistant/themes/purpletv.yaml` nach `<config>/themes/` kopieren, in der
`configuration.yaml` `frontend: themes: !include_dir_merge_named themes` eintragen und im Profil
„PurpleTV“ wählen. Zusätzlich gibt es die Variablen `--purpletv-twitch-color`,
`--purpletv-youtube-color` und `--purpletv-live-color` für eigene Karten.
