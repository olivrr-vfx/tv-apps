package dev.purpletv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ButtonStyle {
    /** Filled with the app accent. One per screen, for the main action. */
    Primary,
    /** Subtle filled surface. Everything else. */
    Secondary,
    /** Transparent until focused. Toolbars and inline actions. */
    Ghost,
}

/**
 * The standard button. On focus every style turns white with dark text, so the focused element is
 * always the brightest thing on screen.
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val bg = when {
        style == ButtonStyle.Primary || selected -> accent.primary
        style == ButtonStyle.Secondary -> Palette.surfaceHigh
        else -> Color.Transparent
    }
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Radius.pill),
        focusedScale = Tokens.Focus.scaleButton,
        background = bg,
        focusedBackground = Palette.focus,
        ring = false,
        onFocus = onFocus,
    ) { focused ->
        val fg = when {
            focused -> Palette.onFocus
            style == ButtonStyle.Ghost && !selected -> Palette.textDim
            else -> Palette.onAccent
        }
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 11.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Tokens.Space.sm))
            }
            Text(text, color = fg, style = Type.label)
        }
    }
}

/** Selectable chip for filters and tabs inside a page. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onFocus: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Radius.pill),
        focusedScale = Tokens.Focus.scaleButton,
        background = if (selected) accent.soft else Palette.surface,
        focusedBackground = Palette.focus,
        ring = false,
        onFocus = onFocus,
    ) { focused ->
        val fg = when {
            focused -> Palette.onFocus
            selected -> accent.light
            else -> Palette.textDim
        }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Tokens.Space.sm))
            }
            Text(text, color = fg, style = Type.label)
        }
    }
}

/** Round icon button with a label that only shows on focus (player controls). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    highlighted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            modifier = Modifier.size(size).onFocusChanged { focused = it.hasFocus },
            shape = CircleShape,
            focusedScale = 1.12f,
            background = if (highlighted) accent.primary else Color.White.copy(alpha = 0.14f),
            focusedBackground = Palette.focus,
            ring = false,
        ) { f ->
            Icon(
                icon,
                contentDescription = label,
                tint = if (f) Palette.onFocus else Color.White,
                modifier = Modifier.size(size * 0.5f).align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (focused) Color.White else Color.Transparent, style = Type.caption)
    }
}

/** Small square-ish icon button for toolbars. */
@Composable
fun IconTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(Tokens.Radius.md),
        focusedScale = Tokens.Focus.scaleButton,
        background = Palette.surfaceHigh,
        focusedBackground = Palette.focus,
        ring = false,
    ) { f ->
        Icon(icon, label, tint = if (f) Palette.onFocus else Palette.text, modifier = Modifier.size(24.dp).align(Alignment.Center))
    }
}

/** Tiny round marker in front of status text. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.size(8.dp).background(color, CircleShape))
}
