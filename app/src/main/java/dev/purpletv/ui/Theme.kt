package dev.purpletv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

object Colors {
    val Purple = Color(0xFF9147FF)
    val PurpleDeep = Color(0xFF5C16C5)
    val PurpleLight = Color(0xFFBF94FF)
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF18181D)
    val SurfaceHigh = Color(0xFF26262C)
    val Text = Color(0xFFEFEFF1)
    val TextDim = Color(0xFFA0A0AB)
    val Live = Color(0xFFEB0400)

    val purpleGradient = Brush.linearGradient(listOf(PurpleDeep, Purple))
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
 * D-pad friendly clickable container: grows and gets a white outline when focused.
 *
 * Kept deliberately cheap: the scale animation only touches the graphics layer (no
 * recomposition per frame) and there are no shadows, which are expensive on TV GPUs.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.06f,
    background: Color = Color.Transparent,
    focusedBackground: Color = background,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) focusedScale else 1f, label = "scale")
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
            .then(if (focused) Modifier.border(2.5.dp, Color.White, shape) else Modifier)
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
    icon: ImageVector? = null,
    onFocus: (() -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = if (selected) Colors.Purple else Colors.SurfaceHigh,
        focusedBackground = Color.White,
        onFocus = onFocus,
    ) { focused ->
        val color = if (focused) Color.Black else Color.White
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Round icon button with a label underneath that only shows on focus (player controls). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    highlighted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(
            onClick = onClick,
            modifier = Modifier.size(size).onFocusChanged { focused = it.hasFocus },
            shape = CircleShape,
            focusedScale = 1.12f,
            background = if (highlighted) Colors.Purple else Color.White.copy(alpha = 0.14f),
            focusedBackground = Color.White,
        ) { f ->
            Icon(
                icon,
                contentDescription = label,
                tint = if (f) Color.Black else Color.White,
                modifier = Modifier.size(size * 0.5f).align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (focused) Color.White else Color.Transparent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Avatar(url: String, size: Dp, modifier: Modifier = Modifier, live: Boolean = false) {
    Box(
        modifier
            .size(size)
            .then(if (live) Modifier.border(2.5.dp, Colors.Live, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(Colors.SurfaceHigh)
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
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
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Colors.purpleGradient))
        Text(text, color = Colors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
