package dev.purpletv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.purpletv.core.AppId

/** Labels drawn on top of a [MediaCard] picture. Null = not shown. */
data class CardBadges(
    val live: Boolean = false,
    val topStart: String? = null,
    val topEnd: String? = null,
    val bottomStart: String? = null,
    val bottomEnd: String? = null,
    val app: AppId? = null,
)

/**
 * 16:9 card for anything playable: streams, VODs, videos. Same layout in every app, the accent
 * colours the meta line.
 */
@Composable
fun MediaCard(
    image: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    meta: String = "",
    avatar: String = "",
    badges: CardBadges = CardBadges(),
    progress: Float = 0f,
    titleLines: Int = 1,
    onFocus: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Focusable(
        onClick = onClick,
        modifier = modifier,
        background = Palette.surface,
        focusedBackground = Palette.surfaceRaised,
        onFocus = onFocus,
    ) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.surfaceHigh)) {
                if (image.isNotEmpty()) {
                    Thumbnail(image, Modifier.fillMaxSize())
                } else if (avatar.isNotEmpty()) {
                    Avatar(avatar, 64.dp, Modifier.align(Alignment.Center))
                }
                Box(Modifier.fillMaxWidth().height(44.dp).align(Alignment.BottomCenter).background(Palette.bottomShade))
                Row(Modifier.align(Alignment.TopStart).padding(Tokens.Space.sm), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (badges.live) LiveBadge()
                    badges.topStart?.let { Badge(it) }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(Tokens.Space.sm), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.topEnd?.let { Badge(it) }
                    badges.app?.let { AppTag(it) }
                }
                badges.bottomStart?.let { Badge(it, Modifier.align(Alignment.BottomStart).padding(Tokens.Space.sm)) }
                badges.bottomEnd?.let { Badge(it, Modifier.align(Alignment.BottomEnd).padding(Tokens.Space.sm)) }
                if (progress > 0f) {
                    Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(accent.primary))
                    }
                }
            }
            Row(Modifier.padding(Tokens.Space.md), verticalAlignment = Alignment.CenterVertically) {
                if (avatar.isNotEmpty() && image.isNotEmpty()) {
                    Avatar(avatar, 32.dp)
                    Spacer(Modifier.width(Tokens.Space.sm + 2.dp))
                }
                Column {
                    Text(
                        title.ifBlank { " " },
                        color = Palette.text,
                        style = Type.titleSmall.copy(fontSize = Type.label.fontSize),
                        maxLines = titleLines,
                        minLines = titleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle.ifBlank { " " },
                        color = if (focused) Palette.text else Palette.textDim,
                        style = Type.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotEmpty()) {
                        Text(meta, color = accent.light, style = Type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** 3:4 card for categories / games. */
@Composable
fun PosterCard(image: String, title: String, onClick: () -> Unit, modifier: Modifier = Modifier, onFocus: (() -> Unit)? = null) {
    Focusable(onClick = onClick, modifier = modifier, background = Palette.surface, onFocus = onFocus) { focused ->
        Column {
            Thumbnail(image, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            Text(
                title,
                modifier = Modifier.padding(horizontal = Tokens.Space.sm, vertical = 7.dp),
                color = if (focused) Color.White else Palette.textDim,
                style = Type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Round avatar with a name below, for channels. */
@Composable
fun AvatarCard(
    image: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    caption: String = "",
    onFocus: (() -> Unit)? = null,
) {
    Focusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(Tokens.Radius.lg), onFocus = onFocus) { focused ->
        Column(Modifier.padding(Tokens.Space.md).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(image, 84.dp, live = live)
            Spacer(Modifier.height(Tokens.Space.sm))
            Text(
                title,
                color = if (focused) Color.White else Palette.text,
                style = Type.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                if (live) "LIVE" else caption.ifEmpty { " " },
                color = if (live) Palette.live else Palette.textFaint,
                style = Type.overline,
                maxLines = 1,
            )
        }
    }
}

/**
 * Big tile on the dashboard that opens an app. Shows the app mark on its accent gradient and a
 * live status line ("3 Kanäle live").
 */
@Composable
fun AppTile(
    title: String,
    status: String,
    accent: Accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: @Composable () -> Unit,
    onFocus: (() -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Tokens.Radius.xl),
        background = Palette.surface,
        focusedBackground = Palette.surfaceRaised,
        onFocus = onFocus,
    ) { focused ->
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(accent.deep.copy(alpha = if (focused) 0.9f else 0.55f), Color.Transparent))
                )
            )
            Column(Modifier.padding(Tokens.Space.xl).align(Alignment.BottomStart)) {
                mark()
                Spacer(Modifier.height(Tokens.Space.md))
                Text(title, color = Color.White, style = Type.title)
                Text(status, color = if (focused) Color.White else accent.light, style = Type.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Simple tile with an icon, for utilities like settings or the phone remote. */
@Composable
fun UtilityTile(
    title: String,
    status: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTile(
        title = title,
        status = status,
        accent = Accent.Hub,
        onClick = onClick,
        modifier = modifier,
        mark = {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Tokens.Radius.md)).background(Palette.surfaceHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        },
    )
}
