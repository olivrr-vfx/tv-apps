"""Which app is open on Couch (Start, PurpleTV, YouTube)."""

from __future__ import annotations

from homeassistant.components.select import SelectEntity
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .api import CannotConnect, PurpleTVClient
from .coordinator import TVCoordinator
from .entity import tv_device
from .media_player import NOT_RUNNING
from .remote import ACTIVITIES


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    async_add_entities([AppSelect(data.tv, data.client, entry.entry_id, entry.data[CONF_HOST])])


class AppSelect(CoordinatorEntity[TVCoordinator], SelectEntity):
    _attr_has_entity_name = True
    _attr_translation_key = "app"
    _attr_icon = "mdi:apps"

    def __init__(self, coordinator: TVCoordinator, client: PurpleTVClient, entry_id: str, host: str) -> None:
        super().__init__(coordinator)
        self._client = client
        self._attr_unique_id = f"{entry_id}_app"
        self._attr_device_info = tv_device(entry_id, host)

    @property
    def available(self) -> bool:
        return super().available and self.coordinator.data is not None

    @property
    def options(self) -> list[str]:
        apps = (self.coordinator.data or {}).get("apps") or ["twitch", "youtube"]
        return [name for name, key in ACTIVITIES.items() if key == "dashboard" or key in apps]

    @property
    def current_option(self) -> str | None:
        s = self.coordinator.data or {}
        app = ((s.get("media") or {}).get("app")) or s.get("app")
        if app is None and s.get("screen") in ("dashboard", "settings"):
            app = "dashboard"
        return next((name for name, key in ACTIVITIES.items() if key == app), None)

    async def async_select_option(self, option: str) -> None:
        try:
            await self._client.command("app", name=ACTIVITIES.get(option, option.lower()))
        except CannotConnect as err:
            raise HomeAssistantError(NOT_RUNNING) from err
        await self.coordinator.async_request_refresh()
