package dev.purpletv.youtube.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.purpletv.core.AppId
import dev.purpletv.design.AvatarCard
import dev.purpletv.design.CardBadges
import dev.purpletv.design.MediaCard
import dev.purpletv.design.formatClock
import dev.purpletv.twitch.api.formatAgo
import dev.purpletv.youtube.api.YtChannel
import dev.purpletv.youtube.api.YtVideo
import dev.purpletv.youtube.api.formatCount

fun YtVideo.metaLine(): String = listOfNotNull(
    if (views >= 0) "${formatCount(views)} Aufrufe" else null,
    when {
        uploadedMillis > 0 -> formatAgo(uploadedMillis)
        uploadedText.isNotBlank() -> uploadedText
        else -> null
    },
).joinToString(" · ")

@Composable
fun VideoCard(
    video: YtVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showApp: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    MediaCard(
        image = video.thumbnail,
        title = video.title,
        subtitle = video.channelName,
        meta = video.metaLine(),
        titleLines = 2,
        badges = CardBadges(
            live = video.isLive,
            bottomEnd = if (!video.isLive && video.durationSec > 0) formatClock(video.durationSec) else null,
            topStart = if (video.isShort) "Short" else null,
            app = if (showApp) AppId.YOUTUBE else null,
        ),
        progress = progress,
        onClick = onClick,
        modifier = modifier,
        onFocus = onFocus,
    )
}

@Composable
fun YtChannelCard(channel: YtChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AvatarCard(
        channel.avatar,
        channel.name,
        onClick,
        modifier,
        caption = if (channel.subscribers > 0) "${formatCount(channel.subscribers)} Abos" else "",
    )
}
