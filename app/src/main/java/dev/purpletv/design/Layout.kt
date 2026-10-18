package dev.purpletv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.purpletv.core.Load
import kotlinx.coroutines.delay

val PagePadding = PaddingValues(horizontal = Tokens.Layout.safeHorizontal)

/** Big page title with optional overline (app name) and trailing content. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Row(
        modifier.fillMaxWidth().padding(start = Tokens.Layout.safeHorizontal, end = Tokens.Layout.safeHorizontal, top = Tokens.Layout.safeVertical, bottom = Tokens.Space.md),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline.uppercase(), color = accent.light, style = Type.overline)
            Text(title, color = Palette.text, style = Type.headline, maxLines = 1)
            if (subtitle != null) Text(subtitle, color = Palette.textDim, style = Type.body, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** Accent bar + title above a row or grid. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val accent = LocalAccent.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(accent.gradient))
        Text(text, color = Palette.text, style = Type.titleSmall)
        if (trailing != null) Text(trailing, color = Palette.textFaint, style = Type.bodySmall)
    }
}

/** Titled horizontal row of cards, the main building block of every start page. */
@Composable
fun ContentRow(title: String, trailing: String? = null, content: LazyListScope.() -> Unit) {
    Column {
        SectionHeader(title, Modifier.padding(start = Tokens.Layout.safeHorizontal, top = Tokens.Space.sm), trailing)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Tokens.Layout.safeHorizontal, vertical = Tokens.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Layout.gridGap),
            content = content,
        )
    }
}

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(Tokens.Space.xxxl), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Centered(modifier) { CircularProgressIndicator(color = LocalAccent.current.primary) }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Centered(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(Tokens.Radius.xl)).background(LocalAccent.current.soft),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = LocalAccent.current.light, modifier = Modifier.size(36.dp)) }
                Spacer(Modifier.height(Tokens.Space.lg))
            }
            Text(title, color = Palette.text, style = Type.title, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(Tokens.Space.sm))
                Text(body, color = Palette.textDim, style = Type.body, textAlign = TextAlign.Center, modifier = Modifier.width(560.dp))
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.height(Tokens.Space.xl))
                TvButton(action, onClick = onAction, style = ButtonStyle.Primary, modifier = Modifier.initialFocus())
            }
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        title = "Das hat nicht geklappt",
        body = message,
        icon = Icons.Filled.ErrorOutline,
        action = if (onRetry != null) "Nochmal versuchen" else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Renders the standard loading / login / error states and hands [Load.Ok] data to [content]. */
@Composable
fun <T> LoadContent(
    state: Load<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onLogin: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        Load.Loading -> LoadingState(modifier)
        Load.NeedLogin -> EmptyState(
            "Anmeldung nötig",
            modifier,
            body = "Melde dich an, um diese Inhalte zu sehen.",
            action = if (onLogin != null) "Anmelden" else null,
            onAction = onLogin,
        )
        is Load.Error -> ErrorState(state.message, modifier, onRetry)
        is Load.Ok -> content(state.data)
    }
}

/** Retry icon used in headers. */
@Composable
fun RefreshButton(onClick: () -> Unit) = IconTile(Icons.Filled.Refresh, "Aktualisieren", onClick)

/**
 * Remembers which item of a list was clicked last and puts focus back on it when the screen is
 * shown again (after returning from a player).
 */
class FocusMemory {
    private val indices = mutableMapOf<String, Int>()
    operator fun get(key: String) = indices[key]
    fun remember(key: String, index: Int) { indices[key] = index }
    val isEmpty get() = indices.isEmpty()
}

@Composable
fun RestorableFocus(memory: FocusMemory, key: String, index: Int, content: @Composable (Modifier) -> Unit) {
    if (memory[key] == index) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { requester.requestFocus() }
        }
        content(Modifier.focusRequester(requester))
    } else {
        content(Modifier)
    }
}

/**
 * Tabs of an app (Start, Live, Suche, ...). Like on most TV platforms the tab switches when the
 * focus rests on it, so the content below follows the D-pad.
 */
@Composable
fun <T> TopTabs(
    tabs: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector?)? = null,
) {
    var pending by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        delay(280)
        onSelect(target)
    }
    Row(
        modifier.padding(horizontal = Tokens.Layout.safeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            Chip(
                label(tab),
                selected = tab == selected,
                onClick = { pending = null; onSelect(tab) },
                icon = icon?.invoke(tab),
                onFocus = { if (tab != selected) pending = tab },
            )
        }
    }
}
