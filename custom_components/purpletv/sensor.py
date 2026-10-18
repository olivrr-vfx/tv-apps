"""Sensors: what's on the TV and how many followed channels are live."""

from __future__ import annotations

from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .coordinator import TVCoordinator
from .entity import tv_device


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    host = entry.data[CONF_HOST]
    async_add_entities([
        CurrentChannelSensor(data.tv, entry.entry_id, host),
        FollowedLiveSensor(data.tv, entry.entry_id, host),
    ])


class _TVSensor(CoordinatorEntity[TVCoordinator], SensorEntity):
    _attr_has_entity_name = True

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str, key: str) -> None:
        super().__init__(coordinator)
        self._attr_translation_key = key
        self._attr_unique_id = f"{entry_id}_{key}"
        self._attr_device_info = tv_device(entry_id, host)


class CurrentChannelSensor(_TVSensor):
    """Name of the channel (or VOD channel) that is on, empty when nothing plays."""

    _attr_icon = "mdi:twitch"

    def __init__(self, coordinator: TVCoordinator, entry_id: str, host: str) -> None:
        super().__init__(coordinator, entry_id, host, "current_channel")

    @property
    def native_value(self) -> str | None:
        s = self.coordinator.data or {}
        media = s.get("channel") or s.get("vod")
        return media.get("display_name") if media else None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        s = self.coordinator.data or {}
        media = s.get("channel") or s.get("vod") or {}
        return {"login": media.get("login"), "title": media.get("title"), "game": media.get("game")}


class FollowedLiveSensor(_TVSensor):
    """Number of followed channels live right now (as seen by the app)."""

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
