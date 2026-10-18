"""Clients for the PurpleTV app API and for Twitch live status."""

from __future__ import annotations

import asyncio
from typing import Any

import aiohttp

TIMEOUT = aiohttp.ClientTimeout(total=6)


class PurpleTVError(Exception):
    """Base error."""


class CannotConnect(PurpleTVError):
    """The TV / app is not reachable (TV off, app closed)."""


class InvalidAuth(PurpleTVError):
    """The pairing code was rejected."""


class PurpleTVClient:
    """Talks to the HTTP API inside the PurpleTV app on the TV."""

    def __init__(self, session: aiohttp.ClientSession, host: str, port: int, code: str) -> None:
        self._session = session
        self.host = host
        self.port = port
        self._code = code

    @property
    def base_url(self) -> str:
        return f"http://{self.host}:{self.port}/api"

    async def _request(self, method: str, path: str, params: dict[str, Any] | None = None, auth: bool = True) -> dict[str, Any]:
        headers = {"Authorization": f"Bearer {self._code}"} if auth else {}
        try:
            async with self._session.request(
                method, f"{self.base_url}/{path}", params=params, headers=headers, timeout=TIMEOUT
            ) as resp:
                if resp.status == 401:
                    raise InvalidAuth
                resp.raise_for_status()
                return await resp.json(content_type=None)
        except (aiohttp.ClientError, asyncio.TimeoutError, OSError) as err:
            raise CannotConnect(str(err)) from err

    async def info(self) -> dict[str, Any]:
        return await self._request("GET", "info", auth=False)

    async def state(self) -> dict[str, Any]:
        return await self._request("GET", "state")

    async def command(self, name: str, **params: Any) -> None:
        clean = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in params.items() if v is not None}
        await self._request("POST", name, params=clean)


GQL_URL = "https://gql.twitch.tv/gql"
# Public client id of the Twitch website; no login needed to read live status.
GQL_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
LIVE_QUERY = """
query PurpleTVLive($logins: [String!]) {
  users(logins: $logins) {
    login
    displayName
    profileImageURL(width: 150)
    broadcastSettings { title }
    stream {
      id
      viewersCount
      createdAt
      previewImageURL(width: 640, height: 360)
      game { displayName }
    }
  }
}
"""


async def fetch_live(session: aiohttp.ClientSession, logins: list[str]) -> dict[str, dict[str, Any]]:
    """Live status for a list of channels, keyed by lowercase login."""
    if not logins:
        return {}
    result: dict[str, dict[str, Any]] = {}
    # Twitch limits users(logins:) to 100 entries.
    for start in range(0, len(logins), 100):
        chunk = logins[start : start + 100]
        try:
            async with session.post(
                GQL_URL,
                json={"query": LIVE_QUERY, "variables": {"logins": chunk}},
                headers={"Client-ID": GQL_CLIENT_ID},
                timeout=aiohttp.ClientTimeout(total=15),
            ) as resp:
                resp.raise_for_status()
                payload = await resp.json(content_type=None)
        except (aiohttp.ClientError, asyncio.TimeoutError) as err:
            raise CannotConnect(str(err)) from err
        for user in (payload.get("data") or {}).get("users") or []:
            if not user:
                continue
            stream = user.get("stream")
            result[user["login"].lower()] = {
                "login": user["login"],
                "display_name": user.get("displayName") or user["login"],
                "avatar": user.get("profileImageURL"),
                "live": stream is not None,
                "title": (user.get("broadcastSettings") or {}).get("title"),
                "game": ((stream or {}).get("game") or {}).get("displayName"),
                "viewers": (stream or {}).get("viewersCount"),
                "started_at": (stream or {}).get("createdAt"),
                "thumbnail": (stream or {}).get("previewImageURL"),
            }
    return result
