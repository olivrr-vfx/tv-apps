package dev.purpletv.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.purpletv.R
import dev.purpletv.core.AppId

@Composable
fun Avatar(url: String, size: Dp, modifier: Modifier = Modifier, live: Boolean = false, ringColor: Color? = null) {
    val ring = if (live) Palette.live else ringColor
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.5.dp, ring, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(Palette.surfaceHigh)
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** Picture with a placeholder surface while loading. */
@Composable
fun Thumbnail(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    Box(modifier.background(Palette.surfaceHigh)) {
        if (url.isNotEmpty()) AsyncImage(url, null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
}

/** Brand mark of an app inside PurpleTV. */
@Composable
fun AppMark(app: AppId, size: Dp, modifier: Modifier = Modifier) {
    val res = when (app) {
        AppId.TWITCH -> R.drawable.mark_twitch
        AppId.YOUTUBE -> R.drawable.mark_youtube
    }
    Image(painterResource(res), contentDescription = app.label, modifier = modifier.size(size))
}

@Composable
fun HubMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.mark_hub), contentDescription = "PurpleTV", modifier = modifier.size(size))
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = "LIVE") {
    Row(
        modifier
            .clip(RoundedCornerShape(Tokens.Radius.xs))
            .background(Palette.live)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Color.White, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, style = Type.overline)
    }
}

/** Dark translucent label on top of pictures (duration, viewers, age). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.74f)) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Radius.xs))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = Color.White,
        style = Type.caption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Small "Twitch"/"YouTube" tag so mixed rows on the dashboard stay readable. */
@Composable
fun AppTag(app: AppId, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(Tokens.Radius.xs))
            .background(Color.Black.copy(alpha = 0.74f))
            .padding(start = 4.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(app, 14.dp)
        Spacer(Modifier.width(4.dp))
        Text(app.label, color = Color.White, style = Type.caption)
    }
}
