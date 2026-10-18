package dev.purpletv.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * The one focus behaviour of the design system: D-pad focus grows the element and draws a white
 * ring. Every interactive component is built on it.
 *
 * Deliberately cheap for TV GPUs: the scale only touches the graphics layer (no recomposition per
 * frame) and there are no shadows or blurs.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Tokens.Radius.md),
    focusedScale: Float = Tokens.Focus.scaleCard,
    background: Color = Color.Transparent,
    focusedBackground: Color = background,
    ring: Boolean = true,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        if (focused) focusedScale else 1f,
        animationSpec = tween(Tokens.Motion.fast),
        label = "focus-scale",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                focused = it.isFocused
            }
            .then(if (focused && ring) Modifier.border(Tokens.Focus.ringWidth, Palette.focus, shape) else Modifier)
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

/** Requests focus once after the first layout, e.g. for the first button of a screen. */
@Composable
fun Modifier.initialFocus(enabled: Boolean = true, key: Any? = Unit, delayMs: Long = 60): Modifier {
    if (!enabled) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        delay(delayMs)
        runCatching { requester.requestFocus() }
    }
    return this.focusRequester(requester)
}
