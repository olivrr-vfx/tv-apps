package dev.purpletv.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Semantic layer on top of [Tokens]. UI code uses these names, never raw hex values.
 *
 * Every app inside Couch (hub, PurpleTV/Twitch, YouTube, ...) brings its own [Accent]; components pick
 * it up from [LocalAccent], so the same button or card looks right in every app.
 */
object Palette {
    val canvas = Tokens.Colors.canvas
    val surface = Tokens.Colors.surface
    val surfaceRaised = Tokens.Colors.surfaceRaised
    val surfaceHigh = Tokens.Colors.surfaceHigh
    val surfaceHighest = Tokens.Colors.surfaceHighest
    val outline = Tokens.Colors.outline
    val scrim = Tokens.Colors.scrim

    val text = Tokens.Colors.textPrimary
    val textDim = Tokens.Colors.textSecondary
    val textFaint = Tokens.Colors.textTertiary
    val onAccent = Tokens.Colors.textOnAccent
    val onFocus = Tokens.Colors.textOnFocus
    val focus = Tokens.Colors.focus

    val live = Tokens.Colors.live
    val success = Tokens.Colors.success
    val warning = Tokens.Colors.warning
    val danger = Tokens.Colors.danger
    val info = Tokens.Colors.info

    /** Fades a picture into the canvas from the bottom. */
    val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
}

@Immutable
data class Accent(
    val primary: Color,
    val light: Color,
    val deep: Color,
    val glow: Color = primary,
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(deep, primary, glow))
    val soft: Color get() = primary.copy(alpha = 0.22f)

    companion object {
        val Hub = Accent(Tokens.Colors.hub, Tokens.Colors.hubLight, Tokens.Colors.hubDeep, Tokens.Colors.hubGlow)
        val Twitch = Accent(Tokens.Colors.twitch, Tokens.Colors.twitchLight, Tokens.Colors.twitchDeep)
        val YouTube = Accent(Tokens.Colors.youtube, Tokens.Colors.youtubeLight, Tokens.Colors.youtubeDeep)
    }
}

val LocalAccent = staticCompositionLocalOf { Accent.Hub }

/** Text styles built from the type tokens. */
@Immutable
data class TypeToken(
    val size: androidx.compose.ui.unit.TextUnit,
    val line: androidx.compose.ui.unit.TextUnit,
    val weight: androidx.compose.ui.text.font.FontWeight,
    val tracking: androidx.compose.ui.unit.TextUnit,
) {
    val style: TextStyle
        get() = TextStyle(
            fontSize = size,
            lineHeight = line,
            fontWeight = weight,
            letterSpacing = tracking,
            fontFamily = FontFamily.SansSerif,
        )
}

object Type {
    val display = Tokens.Type.display.style
    val headline = Tokens.Type.headline.style
    val title = Tokens.Type.title.style
    val titleSmall = Tokens.Type.titleSmall.style
    val body = Tokens.Type.body.style
    val bodySmall = Tokens.Type.bodySmall.style
    val label = Tokens.Type.label.style
    val caption = Tokens.Type.caption.style
    val overline = Tokens.Type.overline.style
}

object Accents {
    val current: Accent
        @Composable @ReadOnlyComposable get() = LocalAccent.current
}

@Composable
fun PurpleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tokens.Colors.hub,
            onPrimary = Palette.onAccent,
            background = Palette.canvas,
            surface = Palette.surface,
            surfaceVariant = Palette.surfaceHigh,
            onSurface = Palette.text,
            onSurfaceVariant = Palette.textDim,
            onBackground = Palette.text,
            outline = Palette.outline,
            error = Palette.danger,
        ),
        content = content,
    )
}

/** Switches the accent for everything inside, e.g. the Twitch app uses Twitch purple. */
@Composable
fun AccentScope(accent: Accent, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccent provides accent, content = content)
}
