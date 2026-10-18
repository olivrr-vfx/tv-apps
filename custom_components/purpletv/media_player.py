"""Couch as a media player: what's on, play/pause, volume, seek, open Twitch/YouTube, browse."""

from __future__ import annotations

from datetime import datetime
import re
from typing import Any

from homeassistant.components.media_player import (
    BrowseMedia,
    MediaClass,
    MediaPlayerEntity,
    MediaPlayerEntityFeature,
    MediaPlayerState,
    MediaType,
)
from homeassistant.components.media_player.errors import BrowseError
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant, callback
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity
from homeassistant.util import dt as dt_util

from . import PurpleTVConfigEntry
from .api import CannotConnect, PurpleTVClient
from .const import APP_NAMES
from .coordinator import TVCoordinator
from .entity import current_media, tv_device

FEATURES = (
    MediaPlayerEntityFeature.PLAY
    | MediaPlayerEntityFeature.PAUSE
    | MediaPlayerEntityFeature.STOP
    | MediaPlayerEntityFeature.VOLUME_MUTE
    | MediaPlayerEntityFeature.VOLUME_SET
    | MediaPlayerEntityFeature.NEXT_TRACK
    | MediaPlayerEntityFeature.PREVIOUS_TRACK
    | MediaPlayerEntityFeature.PLAY_MEDIA
    | MediaPlayerEntityFeature.SELECT_SOURCE
    | MediaPlayerEntityFeature.SEEK
    | MediaPlayerEntityFeature.BROWSE_MEDIA
)

NOT_RUNNING = (
    "Couch ist nicht erreichbar (TV aus oder App geschlossen). "
    "Zum Starten den Deep-Link couch://home über die Android-TV-Remote-Integration nutzen."
)

YOUTUBE_ID = re.compile(r"(?:v=|youtu\.be/|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})")


def parse_media_id(media_type: str, media_id: str) -> tuple[str, dict[str, Any]]:
    """Map a media_content_id to an API command.

    Accepted: 'papaplatte' / 'twitch:papaplatte' (live), 'channel:<login>' (channel page),
    'vod:<id>', twitch.tv links, 'youtube:<id>' / 'yt:<id>', YouTube links,
    'youtube_search:<text>', 'app:<twitch|youtube|dashboard>'.
    """
    target = media_id.strip()
    lower = target.lower()
    if "youtube.com/" in lower or "youtu.be/" in lower:
        match = YOUTUBE_ID.search(target)
        if match:
            return "youtube", {"id": match.group(1)}
        return "youtube/channel", {"url": target}
    if lower.startswith(("youtube:", "yt:")):
        return "youtube", {"id": target.split(":", 1)[1]}
    if lower.startswith("youtube_search:"):
        return "youtube/search", {"q": target.split(":", 1)[1]}
    if lower.startswith("app:"):
        return "app", {"name": lower.split(":", 1)[1]}
    if "twitch.tv/" in lower:
        path = target.split("twitch.tv/", 1)[1].strip("/").split("/")
        target = f"vod:{path[1]}" if len(path) >= 2 and path[0] == "videos" else path[0]
        lower = target.lower()
    if lower.startswith("vod:") or (media_type == MediaType.VIDEO and target.isdigit()):
        return "vod", {"id": target.removeprefix("vod:")}
    if lower.startswith("channel:"):
        return "channel", {"login": lower.removeprefix("channel:")}
    return "open", {"login": lower.removeprefix("twitch:")}


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    async_add_entities([PurpleTVMediaPlayer(data.tv, data.client, entry.entry_id, entry.data[CONF_HOST])])


class PurpleTVMediaPlayer(CoordinatorEntity[TVCoordinator], MediaPlayerEntity):
    _attr_has_entity_name = True
    _attr_name = None
    _attr_supported_features = FEATURES
    _attr_media_image_remotely_accessible = True

    def __init__(self, coordinator: TVCoordinator, client: PurpleTVClient, entry_id: str, host: str) -> None:
        super().__init__(coordinator)
        self._client = client
        self._attr_unique_id = f"{entry_id}_player"
        self._attr_device_info = tv_device(entry_id, host)
        self._position_updated: datetime | None = None
        self._last_position: int | None = None

    @callback
    def _handle_coordinator_update(self) -> None:
        position = (self._media or {}).get("position")
        if position != self._last_position:
            self._last_position = position
            self._position_updated = dt_util.utcnow()
        super()._handle_coordinator_update()

    # --- state -------------------------------------------------------------------------

    @property
    def _state(self) -> dict[str, Any] | None:
        return self.coordinator.data

    @property
    def _media(self) -> dict[str, Any] | None:
        return current_media(self._state)

    @property
    def state(self) -> MediaPlayerState:
        s = self._state
        if s is None or s.get("screen") == "background":
            return MediaPlayerState.OFF
        if self._media:
            return MediaPlayerState.PAUSED if s.get("paused") else MediaPlayerState.PLAYING
        return MediaPlayerState.IDLE

    @property
    def volume_level(self) -> float | None:
        return None if self._state is None else float(self._state.get("volume", 1))

    @property
    def is_volume_muted(self) -> bool | None:
        return None if self._state is None else bool(self._state.get("muted"))

    @property
    def media_content_type(self) -> MediaType | str | None:
        m = self._media
        if not m:
            return None
        return MediaType.CHANNEL if m["kind"] == "live" and m["app"] == "twitch" else MediaType.VIDEO

    @property
    def media_content_id(self) -> str | None:
        m = self._media
        if not m:
            return None
        if m["app"] == "youtube":
            return f"youtube:{m['id']}"
        return m["channel_id"] if m["kind"] == "live" else f"vod:{m['id']}"

    @property
    def media_title(self) -> str | None:
        m = self._media
        return (m.get("title") or m.get("channel")) if m else None

    @property
    def media_artist(self) -> str | None:
        m = self._media
        return m.get("channel") if m else None

    @property
    def media_channel(self) -> str | None:
        return self.media_artist

    @property
    def media_image_url(self) -> str | None:
        m = self._media
        return (m.get("thumbnail") or None) if m else None

    @property
    def media_duration(self) -> int | None:
        m = self._media
        return (m.get("duration") or None) if m and not m.get("live") else None

    @property
    def media_position(self) -> int | None:
        m = self._media
        return m.get("position") if m and not m.get("live") else None

    @property
    def media_position_updated_at(self) -> datetime | None:
        return self._position_updated if self.media_position is not None else None

    @property
    def app_id(self) -> str | None:
        s = self._state or {}
        return (self._media or {}).get("app") or s.get("app")

    @property
    def app_name(self) -> str | None:
        if self._state is None:
            return None
        return APP_NAMES.get(self.app_id or "", "PurpleTV")

    @property
    def source(self) -> str | None:
        m = self._media
        return m.get("channel") if m and m["app"] == "twitch" and m["kind"] == "live" else None

    @property
    def source_list(self) -> list[str] | None:
        followed = (self._state or {}).get("followed_live") or []
        return [c["display_name"] for c in followed] or None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        s = self._state or {}
        m = self._media or {}
        return {
            "screen": s.get("screen", "off"),
            "app": self.app_id,
            "game": m.get("game") or None,
            "viewers": m.get("viewers") or None,
            "login": m.get("channel_id") if m.get("app") == "twitch" else None,
            "video_id": m.get("id") if m.get("app") == "youtube" else None,
        }

    # --- commands ----------------------------------------------------------------------

    async def _send(self, endpoint: str, **params: Any) -> None:
        try:
            await self._client.command(endpoint, **params)
        except CannotConnect as err:
            raise HomeAssistantError(NOT_RUNNING) from err
        await self.coordinator.async_request_refresh()

    async def async_media_play(self) -> None:
        await self._send("play")

    async def async_media_pause(self) -> None:
        await self._send("pause")

    async def async_media_stop(self) -> None:
        await self._send("home")

    async def async_mute_volume(self, mute: bool) -> None:
        await self._send("mute", mute=mute)

    async def async_set_volume_level(self, volume: float) -> None:
        await self._send("volume", level=round(volume, 3))

    async def async_media_next_track(self) -> None:
        await self._send("next")

    async def async_media_previous_track(self) -> None:
        await self._send("previous")

    async def async_media_seek(self, position: float) -> None:
        await self._send("seek", position=int(position))

    async def async_select_source(self, source: str) -> None:
        followed = (self._state or {}).get("followed_live") or []
        login = next((c["login"] for c in followed if c["display_name"] == source), source)
        await self._send("open", login=login.lower())

    async def async_play_media(self, media_type: MediaType | str, media_id: str, **kwargs: Any) -> None:
        command, params = parse_media_id(str(media_type), media_id)
        await self._send(command, **params)

    # --- media browser -----------------------------------------------------------------

    async def async_browse_media(self, media_content_type: MediaType | str | None = None, media_content_id: str | None = None) -> BrowseMedia:
        s = self._state
        if s is None:
            raise BrowseError(NOT_RUNNING)
        apps = s.get("apps") or ["twitch", "youtube"]
        if media_content_id in (None, "", "root"):
            children = []
            if "twitch" in apps:
                children.append(_folder("twitch_live", f"PurpleTV – Live auf Twitch ({len(s.get('followed_live') or [])})", None))
            if "youtube" in apps:
                children.append(_folder("youtube_feed", "YouTube – Neu von deinen Abos", None))
            children.append(_folder("apps", "Apps", None))
            return BrowseMedia(
                media_class=MediaClass.DIRECTORY,
                media_content_id="root",
                media_content_type="purpletv",
                title="Couch",
                can_play=False,
                can_expand=True,
                children=children,
            )
        if media_content_id == "twitch_live":
            items = [
                _item(f"twitch:{c['login']}", MediaType.CHANNEL, c["display_name"], c.get("thumbnail"), MediaClass.CHANNEL)
                for c in s.get("followed_live") or []
            ]
            return _listing("twitch_live", "Twitch – Live", items)
        if media_content_id == "youtube_feed":
            items = [
                _item(f"youtube:{v['id']}", MediaType.VIDEO, f"{v['title']} · {v['channel']}", v.get("thumbnail"), MediaClass.VIDEO)
                for v in s.get("youtube_feed") or []
            ]
            return _listing("youtube_feed", "YouTube – Neu von deinen Abos", items)
        if media_content_id == "apps":
            items = [_item("app:dashboard", "app", "Start", None, MediaClass.APP)]
            items += [_item(f"app:{a}", "app", APP_NAMES.get(a, a), None, MediaClass.APP) for a in apps]
            return _listing("apps", "Apps", items)
        raise BrowseError(f"Unbekannter Eintrag: {media_content_id}")


def _folder(content_id: str, title: str, thumbnail: str | None) -> BrowseMedia:
    return BrowseMedia(
        media_class=MediaClass.DIRECTORY,
        media_content_id=content_id,
        media_content_type="purpletv",
        title=title,
        can_play=False,
        can_expand=True,
        thumbnail=thumbnail,
    )


def _item(content_id: str, content_type: str, title: str, thumbnail: str | None, media_class: str) -> BrowseMedia:
    return BrowseMedia(
        media_class=media_class,
        media_content_id=content_id,
        media_content_type=content_type,
        title=title,
        can_play=True,
        can_expand=False,
        thumbnail=thumbnail or None,
    )


def _listing(content_id: str, title: str, children: list[BrowseMedia]) -> BrowseMedia:
    return BrowseMedia(
        media_class=MediaClass.DIRECTORY,
        media_content_id=content_id,
        media_content_type="purpletv",
        title=title,
        can_play=False,
        can_expand=True,
        children=children,
    )
