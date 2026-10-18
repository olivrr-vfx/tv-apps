package dev.purpletv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSection(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(modifier.padding(top = Tokens.Space.xl, bottom = Tokens.Space.xs)) {
        Text(title.uppercase(), color = LocalAccent.current.light, style = Type.overline)
        if (description != null) Text(description, color = Palette.textFaint, style = Type.bodySmall)
    }
}

/**
 * A settings line: label (+ description) on the left, the current value or a switch on the right.
 * [toggle] != null renders a switch instead of [value].
 */
@Composable
fun SettingRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    toggle: Boolean? = null,
    icon: ImageVector? = null,
    chevron: Boolean = false,
) {
    val accent = LocalAccent.current
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.Radius.md),
        focusedScale = Tokens.Focus.scaleRow,
        background = Palette.surface,
        focusedBackground = Palette.surfaceRaised,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (focused) Color.White else Palette.textDim, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Tokens.Space.lg))
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = Palette.text, style = Type.body)
                if (description != null) Text(description, color = Palette.textFaint, style = Type.bodySmall)
            }
            if (value != null) {
                Spacer(Modifier.width(Tokens.Space.lg))
                Text(value, color = accent.light, style = Type.label)
            }
            if (toggle != null) {
                Spacer(Modifier.width(Tokens.Space.lg))
                Switch(toggle)
            }
            if (chevron) {
                Spacer(Modifier.width(Tokens.Space.sm))
                Icon(Icons.Filled.ChevronRight, null, tint = Palette.textDim, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Display-only switch; the whole [SettingRow] is the click target. */
@Composable
fun Switch(on: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(Tokens.Radius.pill))
            .background(if (on) accent.primary else Palette.surfaceHighest)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** Read-only key/value line, e.g. "Adresse 192.168.1.20:8765". */
@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.textDim, style = Type.body, modifier = Modifier.weight(1f))
        Text(value, color = Palette.text, style = Type.label)
    }
}
