package dev.purpletv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Colors {
    val Purple = Color(0xFF9147FF)
    val PurpleLight = Color(0xFFBF94FF)
    val Background = Color(0xFF0E0E10)
    val Surface = Color(0xFF1F1F23)
    val SurfaceHigh = Color(0xFF2F2F35)
    val Text = Color(0xFFEFEFF1)
    val TextDim = Color(0xFFADADB8)
    val Live = Color(0xFFEB0400)
}

@Composable
fun PurpleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Colors.Purple,
            onPrimary = Color.White,
            background = Colors.Background,
            surface = Colors.Surface,
            onSurface = Colors.Text,
            onBackground = Colors.Text,
        ),
        content = content,
    )
}

/**
 * D-pad friendly clickable container: grows and gets a purple outline when focused.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.05f,
    background: Color = Color.Transparent,
    focusedBackground: Color = background,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "scale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                focused = it.isFocused
            }
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = if (selected) Colors.Purple else Colors.SurfaceHigh,
        focusedBackground = if (selected) Colors.PurpleLight else Color.White,
        onFocus = onFocus,
    ) { focused ->
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp).align(Alignment.Center),
            color = if (focused && !selected) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = "LIVE") {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Colors.Live)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}
