"""PurpleTV: control the PurpleTV Twitch app on Android TV and watch Twitch live status."""

from __future__ import annotations

from dataclasses import dataclass

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import CONF_HOST, CONF_PORT, Platform
from homeassistant.core import HomeAssistant
from homeassistant.helpers.aiohttp_client import async_get_clientsession

from .api import PurpleTVClient
from .const import CONF_CODE
from .coordinator import LiveCoordinator, TVCoordinator

PLATFORMS = [Platform.MEDIA_PLAYER, Platform.BINARY_SENSOR, Platform.SENSOR]


@dataclass
class PurpleTVData:
    client: PurpleTVClient
    tv: TVCoordinator
    live: LiveCoordinator


type PurpleTVConfigEntry = ConfigEntry[PurpleTVData]


async def async_setup_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry) -> bool:
    client = PurpleTVClient(
        async_get_clientsession(hass),
        entry.data[CONF_HOST],
        entry.data[CONF_PORT],
        entry.data[CONF_CODE],
    )
    tv = TVCoordinator(hass, entry, client)
    live = LiveCoordinator(hass, entry)
    await tv.async_config_entry_first_refresh()
    await live.async_config_entry_first_refresh()
    entry.runtime_data = PurpleTVData(client, tv, live)

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    # Channel list changes need new entities.
    entry.async_on_unload(entry.add_update_listener(_async_reload))
    return True


async def _async_reload(hass: HomeAssistant, entry: PurpleTVConfigEntry) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


async def async_unload_entry(hass: HomeAssistant, entry: PurpleTVConfigEntry) -> bool:
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
