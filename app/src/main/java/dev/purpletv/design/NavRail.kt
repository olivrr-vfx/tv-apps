package dev.purpletv.design

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.purpletv.core.AppId

/** One entry of the main navigation: either a Material icon or the mark of an app. */
data class RailItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val app: AppId? = null,
    val accent: Accent = Accent.Hub,
)

private val RailBackground = Color(0xFF0D0D13)

/**
 * Left navigation of the hub. Collapsed it only shows icons; it expands while it has focus.
 * The content is padded by [Tokens.Layout.railCollapsed], the expanded rail floats over it.
 */
@Composable
fun NavRail(
    items: List<RailItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: String? = null,
    itemModifier: (RailItem) -> Modifier = { Modifier },
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFocus = remember { FocusRequester() }
    val hasSelected = items.any { it.key == selected }
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(if (expanded) Tokens.Layout.railExpanded else Tokens.Layout.railCollapsed)
                .background(RailBackground)
                .onFocusChanged { expanded = it.hasFocus }
                // Entering the rail lands on the current section; only Right leaves it again
                // (Up/Down at its ends stay put instead of jumping into the page).
                .focusProperties {
                    enter = { if (hasSelected) selectedFocus else FocusRequester.Default }
                    exit = { if (it == FocusDirection.Right) FocusRequester.Default else FocusRequester.Cancel }
                }
                .focusGroup()
                .padding(vertical = Tokens.Layout.safeVertical, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
        ) {
            Row(Modifier.padding(start = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                HubMark(44.dp)
                if (expanded) {
                    Spacer(Modifier.width(Tokens.Space.md))
                    Text(dev.purpletv.core.HUB_NAME, color = Color.White, style = Type.title)
                }
            }
            items.forEach { item ->
                val isSelected = item.key == selected
                RailEntry(item, selected = isSelected, expanded = expanded, onSelect = { onSelect(item.key) },
                    modifier = itemModifier(item).then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier))
            }
            Spacer(Modifier.weight(1f))
            if (expanded && footer != null) {
                Text(footer, Modifier.padding(start = 10.dp), color = Palette.textFaint, style = Type.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Soft edge so the opened rail doesn't end in a hard line.
        if (expanded) {
            Box(Modifier.fillMaxHeight().width(56.dp).background(Brush.horizontalGradient(listOf(RailBackground, Color.Transparent))))
        }
    }
}

@Composable
private fun RailEntry(item: RailItem, selected: Boolean, expanded: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    Focusable(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(Tokens.Radius.lg),
        focusedScale = 1.03f,
        background = if (selected) item.accent.soft else Color.Transparent,
        focusedBackground = Palette.focus,
        ring = false,
    ) { focused ->
        val tint = when {
            focused -> Palette.onFocus
            selected -> item.accent.light
            else -> Palette.textDim
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                item.app != null -> AppMark(item.app, 28.dp)
                item.icon != null -> Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(28.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(Tokens.Space.lg))
                Text(item.label, color = if (focused) Palette.onFocus else Color.White, style = Type.label.copy(fontSize = Type.body.fontSize), maxLines = 1)
            }
            if (selected && !expanded) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(2.dp)).background(item.accent.primary))
            }
        }
    }
}
