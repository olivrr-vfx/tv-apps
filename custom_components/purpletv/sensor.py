"""Sensors: what's on the TV, followed Twitch channels live, new YouTube videos."""

from __future__ import annotations

import time
from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .coordinator import TVCoordinator
from .entity import current_media, tv_device


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    host = entry.data[CONF_HOST]
    async_add_entities([
        CurrentChannelSensor(data.tv, entry.entry_id, host),
        FollowedLiveSensor(data.tv, entry.entry_id, host),
        YouTubeNewSensor(data.tv, entry.entry_id, host),
    ])


class _TVSensor(CoordinatorEntity[TVCoordinator], SensorEntity):
    _attr_has_entity_name = True

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str, key: str) -> None:
        super().__init__(coordinator)
        self._attr_translation_key = key
        self._attr_unique_id = f"{entry_id}_{key}"
        self._attr_device_info = tv_device(entry_id, host)


class CurrentChannelSensor(_TVSensor):
    """Channel of what is playing (Twitch stream/VOD or YouTube video), empty when nothing plays."""

    _attr_icon = "mdi:television-play"

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str) -> None:
        super().__init__(coordinator, entry_id, host, "current_channel")

    @property
    def native_value(self) -> str | None:
        media = current_media(self.coordinator.data)
        return media.get("channel") if media else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        media = current_media(self.coordinator.data) or {}
        return {
            "app": media.get("app"),
            "login": media.get("channel_id") if media.get("app") == "twitch" else None,
            "title": media.get("title"),
            "game": media.get("game"),
        }


class FollowedLiveSensor(_TVSensor):
    """Number of followed Twitch channels live right now (as seen by the app)."""

    _attr_icon = "mdi:account-multiple-check"
    _attr_native_unit_of_measurement = "Kanäle"

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str) -> None:
        super().__init__(coordinator, entry_id, host, "followed_live")

    @property
    def available(self) -> bool:
        return super().available and self.coordinator.data is not None

    @property
    def native_value(self) -> int | None:
        s = self.coordinator.data
        return None if s is None else len(s.get("followed_live") or [])

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        followed = (self.coordinator.data or {}).get("followed_live") or []
        return {"channels": [c["display_name"] for c in followed], "logins": [c["login"] for c in followed]}


class YouTubeNewSensor(_TVSensor):
    """YouTube videos from the subscriptions uploaded in the last 24 hours."""

    _attr_icon = "mdi:youtube-subscription"
    _attr_native_unit_of_measurement = "Videos"

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str) -> None:
        super().__init__(coordinator, entry_id, host, "youtube_new")

    def _recent(self) -> list[dict[str, Any]]:
        feed = (self.coordinator.data or {}).get("youtube_feed") or []
        cutoff = (time.time() - 24 * 3600) * 1000
        return [v for v in feed if (v.get("uploaded_ms") or 0) >= cutoff]

    @property
    def available(self) -> bool:
        s = self.coordinator.data
        return super().available and s is not None and "youtube_feed" in s

    @property
    def native_value(self) -> int | None:
        return len(self._recent()) if self.coordinator.data is not None else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        return {
            "videos": [
                {"id": v["id"], "title": v.get("title"), "channel": v.get("channel"), "thumbnail": v.get("thumbnail")}
                for v in self._recent()[:10]
            ]
        }
