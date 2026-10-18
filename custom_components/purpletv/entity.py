"""Shared entity bits."""

from __future__ import annotations

from homeassistant.helpers.device_registry import DeviceEntryType, DeviceInfo

from .const import DOMAIN


def tv_device(entry_id: str, host: str) -> DeviceInfo:
    return DeviceInfo(
        identifiers={(DOMAIN, entry_id)},
        name="PurpleTV",
        manufacturer="PurpleTV",
        model="Twitch-App für Android TV",
        configuration_url=f"http://{host}:8765/api/info",
    )


def twitch_device(entry_id: str) -> DeviceInfo:
    return DeviceInfo(
        identifiers={(DOMAIN, f"{entry_id}_twitch")},
        name="Twitch-Kanäle",
        manufacturer="Twitch",
        entry_type=DeviceEntryType.SERVICE,
    )
