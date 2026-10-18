"""PurpleTV as a media player: what's on, play/pause, mute, volume, open channels."""

from __future__ import annotations

from typing import Any

from homeassistant.components.media_player import (
    MediaPlayerEntity,
    MediaPlayerEntityFeature,
    MediaPlayerState,
    MediaType,
)
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .api import CannotConnect, PurpleTVClient
from .coordinator import TVCoordinator
from .entity import tv_device

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
)

NOT_RUNNING = (
    "PurpleTV ist nicht erreichbar (TV aus oder App geschlossen). "
    "Zum Starten den Deep-Link purpletv://watch/<kanal> über die Android-TV-Remote-Integration nutzen."
)


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

    # --- state -------------------------------------------------------------------------

    @property
    def _state(self) -> dict[str, Any] | None:
        return self.coordinator.data

    @property
    def _media(self) -> dict[str, Any] | None:
        s = self._state or {}
        return s.get("channel") or s.get("vod")

    @property
    def state(self) -> MediaPlayerState:
        s = self._state
        if s is None or s.get("screen") == "background":
            return MediaPlayerState.OFF
        if s.get("screen") in ("live", "vod"):
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
        s = self._state or {}
        if s.get("channel"):
            return MediaType.CHANNEL
        if s.get("vod"):
            return MediaType.VIDEO
        return None

    @property
    def media_content_id(self) -> str | None:
        s = self._state or {}
        if s.get("channel"):
            return s["channel"]["login"]
        if s.get("vod"):
            return f"vod:{s['vod']['id']}"
        return None

    @property
    def media_title(self) -> str | None:
        m = self._media
        return m.get("title") if m else None

    @property
    def media_artist(self) -> str | None:
        m = self._media
        return m.get("display_name") if m else None

    @property
    def media_channel(self) -> str | None:
        return self.media_artist

    @property
    def media_image_url(self) -> str | None:
        m = self._media
        return m.get("thumbnail") if m else None

    @property
    def media_duration(self) -> int | None:
        vod = (self._state or {}).get("vod")
        return vod.get("duration") if vod else None

    @property
    def media_position(self) -> int | None:
        vod = (self._state or {}).get("vod")
        return vod.get("position") if vod else None

    @property
    def app_name(self) -> str | None:
        return "Twitch" if self._state else None

    @property
    def source(self) -> str | None:
        channel = (self._state or {}).get("channel")
        return channel.get("display_name") if channel else None

    @property
    def source_list(self) -> list[str] | None:
        followed = (self._state or {}).get("followed_live") or []
        return [c["display_name"] for c in followed] or None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        channel = (self._state or {}).get("channel") or {}
        return {
            "screen": (self._state or {}).get("screen", "off"),
            "game": channel.get("game"),
            "viewers": channel.get("viewers"),
            "login": channel.get("login"),
        }

    # --- commands ----------------------------------------------------------------------

    async def _send(self, name: str, **params: Any) -> None:
        try:
            await self._client.command(name, **params)
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

    async def async_select_source(self, source: str) -> None:
        followed = (self._state or {}).get("followed_live") or []
        login = next((c["login"] for c in followed if c["display_name"] == source), source)
        await self._send("open", login=login.lower())

    async def async_play_media(self, media_type: MediaType | str, media_id: str, **kwargs: Any) -> None:
        """media_id: channel name ('papaplatte'), 'channel:<name>' for the channel page,
        'vod:<id>', or a twitch.tv link."""
        target = media_id.strip()
        if "twitch.tv/" in target:
            path = target.split("twitch.tv/", 1)[1].strip("/").split("/")
            target = f"vod:{path[1]}" if len(path) >= 2 and path[0] == "videos" else path[0]
        if target.startswith("vod:") or media_type == MediaType.VIDEO:
            await self._send("vod", id=target.removeprefix("vod:"))
        elif target.startswith("channel:"):
            await self._send("channel", login=target.removeprefix("channel:").lower())
        else:
            await self._send("open", login=target.lower())
