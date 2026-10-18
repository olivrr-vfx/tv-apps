"""Binary sensors: 'watching Twitch' on the TV and one 'is live' sensor per chosen channel."""

from __future__ import annotations

from typing import Any

from homeassistant.components.binary_sensor import BinarySensorEntity
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .coordinator import LiveCoordinator, TVCoordinator
from .entity import tv_device, twitch_device


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    entities: list[BinarySensorEntity] = [WatchingSensor(data.tv, entry.entry_id, entry.data[CONF_HOST])]
    entities += [ChannelLiveSensor(data.live, entry.entry_id, login) for login in data.live.channels]
    async_add_entities(entities)


class WatchingSensor(CoordinatorEntity[TVCoordinator], BinarySensorEntity):
    """On while a stream or video is open in PurpleTV (also when paused)."""

    _attr_has_entity_name = True
    _attr_translation_key = "watching"

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str) -> None:
        super().__init__(coordinator)
        self._attr_unique_id = f"{entry_id}_watching"
        self._attr_device_info = tv_device(entry_id, host)

    @property
    def is_on(self) -> bool:
        s = self.coordinator.data
        return bool(s) and s.get("screen") in ("live", "vod")

    @property
    def icon(self) -> str:
        return "mdi:television-play" if self.is_on else "mdi:television"

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        s = self.coordinator.data or {}
        media = s.get("channel") or s.get("vod") or {}
        return {
            "channel": media.get("display_name"),
            "title": media.get("title"),
            "paused": s.get("paused"),
            "type": "live" if s.get("channel") else ("vod" if s.get("vod") else None),
        }


class ChannelLiveSensor(CoordinatorEntity[LiveCoordinator], BinarySensorEntity):
    """Is this Twitch channel live right now? Works with the TV off."""

    _attr_has_entity_name = True

    def __init__(self, coordinator: LiveCoordinator, entry_id: str, login: str) -> None:
        super().__init__(coordinator)
        self._login = login
        self._attr_unique_id = f"{entry_id}_live_{login}"
        self._attr_device_info = twitch_device(entry_id)

    @property
    def _channel(self) -> dict[str, Any]:
        return (self.coordinator.data or {}).get(self._login) or {}

    @property
    def name(self) -> str:
        return f"{self._channel.get('display_name') or self._login} live"

    @property
    def is_on(self) -> bool:
        return bool(self._channel.get("live"))

    @property
    def icon(self) -> str:
        return "mdi:twitch"

    @property
    def entity_picture(self) -> str | None:
        return self._channel.get("avatar")

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        c = self._channel
        return {
            "login": self._login,
            "title": c.get("title"),
            "game": c.get("game"),
            "viewers": c.get("viewers"),
            "started_at": c.get("started_at"),
            "thumbnail": c.get("thumbnail"),
            "url": f"https://www.twitch.tv/{self._login}",
        }
