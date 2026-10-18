package dev.purpletv.twitch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.purpletv.core.AppId
import dev.purpletv.design.AvatarCard
import dev.purpletv.design.CardBadges
import dev.purpletv.design.MediaCard
import dev.purpletv.design.PosterCard
import dev.purpletv.twitch.api.ChannelInfo
import dev.purpletv.twitch.api.Game
import dev.purpletv.twitch.api.StreamInfo
import dev.purpletv.twitch.api.VideoInfo
import dev.purpletv.twitch.api.formatAgo
import dev.purpletv.twitch.api.formatDuration
import dev.purpletv.twitch.api.formatUptime
import dev.purpletv.twitch.api.formatViewers

/** Twitch data mapped onto the design system cards. */

@Composable
fun StreamCard(
    stream: StreamInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showApp: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    MediaCard(
        image = stream.thumbnail(),
        title = stream.displayName,
        subtitle = stream.title,
        meta = stream.gameName,
        avatar = stream.avatarUrl,
        badges = CardBadges(
            live = stream.isLive,
            topStart = if (stream.isLive) null else "Offline",
            bottomStart = if (stream.isLive && stream.viewers > 0) "● ${formatViewers(stream.viewers)}" else null,
            bottomEnd = if (stream.isLive) formatUptime(stream.startedAtMillis()).ifEmpty { null } else null,
            app = if (showApp) AppId.TWITCH else null,
        ),
        onClick = onClick,
        modifier = modifier,
        onFocus = onFocus,
    )
}

@Composable
fun VodCard(
    video: VideoInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    showChannel: Boolean = false,
    showApp: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    MediaCard(
        image = video.thumbnail(),
        title = video.title.ifBlank { "Ohne Titel" },
        subtitle = listOfNotNull(
            video.userName.takeIf { showChannel && it.isNotBlank() },
            if (video.viewCount > 0) "${formatViewers(video.viewCount)} Aufrufe" else null,
        ).joinToString(" · "),
        titleLines = 2,
        badges = CardBadges(
            topStart = formatAgo(video.createdAtMillis()).ifEmpty { null },
            topEnd = if (video.type == "highlight") "Highlight" else null,
            bottomEnd = formatDuration(video.durationSec),
            app = if (showApp) AppId.TWITCH else null,
        ),
        progress = progress,
        onClick = onClick,
        modifier = modifier,
        onFocus = onFocus,
    )
}

@Composable
fun ChannelCard(channel: ChannelInfo, live: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AvatarCard(channel.avatarUrl, channel.displayName, onClick, modifier, live = live)
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PosterCard(game.boxArt(285, 380), game.name, onClick, modifier)
}
