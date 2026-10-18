"""Data coordinators: one polls the TV app, one polls Twitch for live channels."""

from __future__ import annotations

from datetime import timedelta
import logging
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import ConfigEntryAuthFailed
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

from .api import CannotConnect, InvalidAuth, PurpleTVClient, fetch_live
from .const import CONF_CHANNELS, DOMAIN, LIVE_SCAN_SECONDS, TV_SCAN_SECONDS

_LOGGER = logging.getLogger(__name__)


class TVCoordinator(DataUpdateCoordinator[dict[str, Any] | None]):
    """State of the app on the TV. ``None`` means the TV or the app is off."""

    def __init__(self, hass: HomeAssistant, entry: ConfigEntry, client: PurpleTVClient) -> None:
        super().__init__(
            hass,
            _LOGGER,
            config_entry=entry,
            name=f"{DOMAIN} tv",
            update_interval=timedelta(seconds=TV_SCAN_SECONDS),
        )
        self.client = client

    async def _async_update_data(self) -> dict[str, Any] | None:
        try:
            return await self.client.state()
        except InvalidAuth as err:
            raise ConfigEntryAuthFailed("PurpleTV code changed") from err
        except CannotConnect:
            # Not an error: the TV is simply off or PurpleTV isn't open.
            return None


class LiveCoordinator(DataUpdateCoordinator[dict[str, dict[str, Any]]]):
    """Live status of the channels chosen in the options."""

    def __init__(self, hass: HomeAssistant, entry: ConfigEntry) -> None:
        super().__init__(
            hass,
            _LOGGER,
            config_entry=entry,
            name=f"{DOMAIN} live",
            update_interval=timedelta(seconds=LIVE_SCAN_SECONDS),
        )
        self.session = async_get_clientsession(hass)

    @property
    def channels(self) -> list[str]:
        return list(self.config_entry.options.get(CONF_CHANNELS, []))

    async def _async_update_data(self) -> dict[str, dict[str, Any]]:
        try:
            return await fetch_live(self.session, self.channels)
        except CannotConnect as err:
            raise UpdateFailed(f"Twitch not reachable: {err}") from err
