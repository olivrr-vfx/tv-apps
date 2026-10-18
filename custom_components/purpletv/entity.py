"""Shared entity bits."""

from __future__ import annotations

from typing import Any

from homeassistant.helpers.device_registry import DeviceEntryType, DeviceInfo

from .const import DEFAULT_PORT, DOMAIN


def tv_device(entry_id: str, host: str) -> DeviceInfo:
    return DeviceInfo(
        identifiers={(DOMAIN, entry_id)},
        name="PurpleTV",
        manufacturer="PurpleTV",
        model="TV-Hub für Twitch und YouTube",
        configuration_url=f"http://{host}:{DEFAULT_PORT}/",
    )


def twitch_device(entry_id: str) -> DeviceInfo:
    return DeviceInfo(
        identifiers={(DOMAIN, f"{entry_id}_twitch")},
        name="Twitch-Kanäle",
        manufacturer="Twitch",
        entry_type=DeviceEntryType.SERVICE,
    )


def current_media(state: dict[str, Any] | None) -> dict[str, Any] | None:
    """What is playing, in the format of PurpleTV 2.x (also for 1.x apps that only send channel/vod)."""
    if not state:
        return None
    if state.get("media"):
        return state["media"]
    if channel := state.get("channel"):
        return {
            "app": "twitch",
            "kind": "live",
            "id": channel.get("login"),
            "title": channel.get("title"),
            "channel": channel.get("display_name"),
            "channel_id": channel.get("login"),
            "thumbnail": channel.get("thumbnail"),
            "avatar": channel.get("avatar"),
            "game": channel.get("game"),
            "viewers": channel.get("viewers"),
            "started_at": channel.get("started_at"),
            "live": True,
        }
    if vod := state.get("vod"):
        return {
            "app": "twitch",
            "kind": "vod",
            "id": vod.get("id"),
            "title": vod.get("title"),
            "channel": vod.get("display_name"),
            "channel_id": vod.get("login"),
            "thumbnail": vod.get("thumbnail"),
            "duration": vod.get("duration"),
            "position": vod.get("position"),
            "live": False,
        }
    return None
