"""PurpleTV as a remote: D-pad keys and switching apps (like a TV remote, inside PurpleTV)."""

from __future__ import annotations

import asyncio
from collections.abc import Iterable
from typing import Any

from homeassistant.components.remote import ATTR_DELAY_SECS, ATTR_NUM_REPEATS, RemoteEntity, RemoteEntityFeature
from homeassistant.const import CONF_HOST
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from . import PurpleTVConfigEntry
from .api import CannotConnect, PurpleTVClient
from .const import KEY_ALIASES, REMOTE_KEYS
from .coordinator import TVCoordinator
from .entity import tv_device
from .media_player import NOT_RUNNING

ACTIVITIES = {"Start": "dashboard", "Twitch": "twitch", "YouTube": "youtube"}


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    data = entry.runtime_data
    async_add_entities([PurpleTVRemote(data.tv, data.client, entry.entry_id, entry.data[CONF_HOST])])


class PurpleTVRemote(CoordinatorEntity[TVCoordinator], RemoteEntity):
    """send_command: up, down, left, right, ok, back, menu, play_pause, channel_up, channel_down, home.

    turn_on with activity Start / Twitch / YouTube switches the app.
    """

    _attr_has_entity_name = True
    _attr_translation_key = "remote"
    _attr_supported_features = RemoteEntityFeature.ACTIVITY

    def __init__(self, coordinator: TVCoordinator, client: PurpleTVClient, entry_id: str, host: str) -> None:
        super().__init__(coordinator)
        self._client = client
        self._attr_unique_id = f"{entry_id}_remote"
        self._attr_device_info = tv_device(entry_id, host)

    @property
    def is_on(self) -> bool:
        s = self.coordinator.data
        return s is not None and s.get("screen") != "background"

    @property
    def activity_list(self) -> list[str]:
        apps = (self.coordinator.data or {}).get("apps") or ["twitch", "youtube"]
        return [name for name, key in ACTIVITIES.items() if key == "dashboard" or key in apps]

    @property
    def current_activity(self) -> str | None:
        s = self.coordinator.data or {}
        app = ((s.get("media") or {}).get("app")) or s.get("app") or ("dashboard" if s.get("screen") == "dashboard" else None)
        return next((name for name, key in ACTIVITIES.items() if key == app), None)

    async def _call(self, endpoint: str, **params: Any) -> None:
        try:
            await self._client.command(endpoint, **params)
        except CannotConnect as err:
            raise HomeAssistantError(NOT_RUNNING) from err

    async def async_turn_on(self, activity: str | None = None, **kwargs: Any) -> None:
        target = ACTIVITIES.get(activity or "Start", (activity or "dashboard").lower())
        await self._call("app", name=target)
        await self.coordinator.async_request_refresh()

    async def async_turn_off(self, **kwargs: Any) -> None:
        # PurpleTV cannot switch the TV off; go back to the dashboard and stop playback.
        await self._call("home")
        await self.coordinator.async_request_refresh()

    async def async_send_command(self, command: Iterable[str], **kwargs: Any) -> None:
        repeats = int(kwargs.get(ATTR_NUM_REPEATS) or 1)
        delay = float(kwargs.get(ATTR_DELAY_SECS) or 0.2)
        for _ in range(repeats):
            for raw in command:
                key = KEY_ALIASES.get(raw.lower().strip(), raw.lower().strip())
                if key == "home":
                    await self._call("home")
                elif key in REMOTE_KEYS:
                    await self._call("key", code=key)
                else:
                    raise HomeAssistantError(f"Unbekannter Befehl '{raw}'. Erlaubt: {', '.join(REMOTE_KEYS + ['home'])}")
                await asyncio.sleep(delay)
        await self.coordinator.async_request_refresh()
