// Generated from design/tokens.json by design/generate.py. Do not edit.
package dev.purpletv.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Raw design tokens. Prefer the semantic helpers in Theme.kt in UI code. */
object Tokens {
    object Colors {
        /** App background */
        val canvas = Color(0xFF09090E)
        /** Cards, rows, panels */
        val surface = Color(0xFF121219)
        /** Focused card, sheets */
        val surfaceRaised = Color(0xFF1A1A24)
        /** Inputs, placeholders, chips */
        val surfaceHigh = Color(0xFF242431)
        /** Pressed / selected chips */
        val surfaceHighest = Color(0xFF30303F)
        /** Hairlines, dividers */
        val outline = Color(0xFF2E2E3C)
        /** Overlay behind dialogs and video chrome */
        val scrim = Color(0xB8000000)
        /** Titles and body */
        val textPrimary = Color(0xFFF5F5FA)
        /** Supporting text */
        val textSecondary = Color(0xFFA9A9BC)
        /** Hints, disabled */
        val textTertiary = Color(0xFF6F6F84)
        /** Text on accent fills */
        val textOnAccent = Color(0xFFFFFFFF)
        /** Text on the white focus fill */
        val textOnFocus = Color(0xFF0B0B10)
        /** Focus ring and focused button fill */
        val focus = Color(0xFFFFFFFF)
        /** Couch hub accent */
        val hub = Color(0xFF8B5CF6)
        /** Hub accent on dark text */
        val hubLight = Color(0xFFC4B5FD)
        /** Hub gradient start */
        val hubDeep = Color(0xFF4338CA)
        /** Hub gradient end */
        val hubGlow = Color(0xFFEC4899)
        /** Twitch app accent */
        val twitch = Color(0xFF9147FF)
        /** Twitch accent on dark text */
        val twitchLight = Color(0xFFBF94FF)
        /** Twitch gradient start */
        val twitchDeep = Color(0xFF5C16C5)
        /** YouTube app accent */
        val youtube = Color(0xFFFF2E4D)
        /** YouTube accent on dark text */
        val youtubeLight = Color(0xFFFF8597)
        /** YouTube gradient start */
        val youtubeDeep = Color(0xFFB5001E)
        /** LIVE badges and rings */
        val live = Color(0xFFFF3040)
        /** Connected, done */
        val success = Color(0xFF34D399)
        /** Attention */
        val warning = Color(0xFFFBBF24)
        /** Errors, unblockable ads */
        val danger = Color(0xFFF87171)
        /** Neutral status */
        val info = Color(0xFF60A5FA)
    }
    object Space {
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 48.dp
        val huge = 64.dp
    }
    object Radius {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 24.dp
        val pill = 999.dp
    }
    object Layout {
        val safeHorizontal = 48.dp
        val safeVertical = 28.dp
        val railCollapsed = 88.dp
        val railExpanded = 260.dp
        val cardWide = 268.dp
        val cardPoster = 136.dp
        val cardAvatar = 120.dp
        val appTile = 300.dp
        val gridGap = 20.dp
    }
    object Type {
        val display = TypeToken(44.sp, 52.sp, FontWeight(900), -0.5.sp)
        val headline = TypeToken(30.sp, 38.sp, FontWeight(800), -0.3.sp)
        val title = TypeToken(22.sp, 28.sp, FontWeight(700), 0.sp)
        val titleSmall = TypeToken(17.sp, 22.sp, FontWeight(700), 0.sp)
        val body = TypeToken(15.sp, 21.sp, FontWeight(400), 0.sp)
        val bodySmall = TypeToken(13.sp, 18.sp, FontWeight(400), 0.sp)
        val label = TypeToken(14.sp, 18.sp, FontWeight(600), 0.1.sp)
        val caption = TypeToken(12.sp, 16.sp, FontWeight(600), 0.2.sp)
        val overline = TypeToken(11.sp, 14.sp, FontWeight(800), 1.2.sp)
    }
    object Motion {
        const val fast = 120
        const val normal = 200
        const val slow = 320
        const val heroDelay = 300
    }
    object Focus {
        val ringWidth = 3.dp
        const val scaleCard = 1.06f
        const val scaleButton = 1.04f
        const val scaleRow = 1.015f
    }
}
