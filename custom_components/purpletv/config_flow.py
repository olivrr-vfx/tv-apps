"""Config flow for PurpleTV."""

from __future__ import annotations

from collections.abc import Mapping
import re
from typing import Any

import voluptuous as vol

from homeassistant.config_entries import ConfigEntry, ConfigFlow, ConfigFlowResult, OptionsFlow
from homeassistant.const import CONF_HOST, CONF_PORT
from homeassistant.core import callback
from homeassistant.helpers.aiohttp_client import async_get_clientsession

try:
    from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo
except ImportError:  # Home Assistant < 2025.1
    from homeassistant.components.zeroconf import ZeroconfServiceInfo

from .api import CannotConnect, InvalidAuth, PurpleTVClient
from .const import CONF_CHANNELS, CONF_CODE, DEFAULT_PORT, DOMAIN

CHANNELS_HELP = "papaplatte, zarbex"


def parse_channels(text: str) -> list[str]:
    """'Papaplatte, twitch.tv/zarbex' -> ['papaplatte', 'zarbex']."""
    out: list[str] = []
    for part in re.split(r"[,;\s]+", text or ""):
        login = part.strip().rstrip("/").split("/")[-1].lower()
        if re.fullmatch(r"[a-z0-9_]{2,25}", login) and login not in out:
            out.append(login)
    return out


class PurpleTVConfigFlow(ConfigFlow, domain=DOMAIN):
    VERSION = 1

    def __init__(self) -> None:
        self._host: str | None = None
        self._port: int = DEFAULT_PORT

    async def _validate(self, host: str, port: int, code: str) -> str | None:
        client = PurpleTVClient(async_get_clientsession(self.hass), host, port, code)
        try:
            await client.state()
        except InvalidAuth:
            return "invalid_auth"
        except CannotConnect:
            return "cannot_connect"
        return None

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        errors: dict[str, str] = {}
        if user_input is not None:
            host = user_input[CONF_HOST].strip()
            port = int(user_input[CONF_PORT])
            await self.async_set_unique_id(host)
            self._abort_if_unique_id_configured()
            error = await self._validate(host, port, user_input[CONF_CODE].strip())
            if error is None:
                return self._create(host, port, user_input)
            errors["base"] = error

        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Required(CONF_HOST): str,
                    vol.Required(CONF_PORT, default=DEFAULT_PORT): int,
                    vol.Required(CONF_CODE): str,
                    vol.Optional(CONF_CHANNELS, default=""): str,
                }
            ),
            errors=errors,
            description_placeholders={"example": CHANNELS_HELP},
        )

    async def async_step_zeroconf(self, discovery_info: ZeroconfServiceInfo) -> ConfigFlowResult:
        self._host = str(discovery_info.host)
        self._port = discovery_info.port or DEFAULT_PORT
        await self.async_set_unique_id(self._host)
        self._abort_if_unique_id_configured(updates={CONF_HOST: self._host, CONF_PORT: self._port})
        self.context["title_placeholders"] = {"host": self._host}
        return await self.async_step_confirm()

    async def async_step_confirm(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        """Discovered TV: only the code from the PurpleTV settings is needed."""
        assert self._host is not None
        errors: dict[str, str] = {}
        if user_input is not None:
            error = await self._validate(self._host, self._port, user_input[CONF_CODE].strip())
            if error is None:
                return self._create(self._host, self._port, user_input)
            errors["base"] = error
        return self.async_show_form(
            step_id="confirm",
            data_schema=vol.Schema(
                {
                    vol.Required(CONF_CODE): str,
                    vol.Optional(CONF_CHANNELS, default=""): str,
                }
            ),
            errors=errors,
            description_placeholders={"host": self._host, "example": CHANNELS_HELP},
        )

    async def async_step_reauth(self, entry_data: Mapping[str, Any]) -> ConfigFlowResult:
        self._host = entry_data[CONF_HOST]
        self._port = entry_data[CONF_PORT]
        return await self.async_step_reauth_confirm()

    async def async_step_reauth_confirm(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        assert self._host is not None
        errors: dict[str, str] = {}
        if user_input is not None:
            error = await self._validate(self._host, self._port, user_input[CONF_CODE].strip())
            if error is None:
                return self.async_update_reload_and_abort(
                    self._get_reauth_entry(), data_updates={CONF_CODE: user_input[CONF_CODE].strip()}
                )
            errors["base"] = error
        return self.async_show_form(
            step_id="reauth_confirm",
            data_schema=vol.Schema({vol.Required(CONF_CODE): str}),
            errors=errors,
        )

    def _create(self, host: str, port: int, user_input: dict[str, Any]) -> ConfigFlowResult:
        return self.async_create_entry(
            title=f"Couch ({host})",
            data={CONF_HOST: host, CONF_PORT: port, CONF_CODE: user_input[CONF_CODE].strip()},
            options={CONF_CHANNELS: parse_channels(user_input.get(CONF_CHANNELS, ""))},
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry: ConfigEntry) -> OptionsFlow:
        return PurpleTVOptionsFlow()


class PurpleTVOptionsFlow(OptionsFlow):
    """Pick the channels that get a live sensor."""

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        if user_input is not None:
            return self.async_create_entry(data={CONF_CHANNELS: parse_channels(user_input[CONF_CHANNELS])})
        current = ", ".join(self.config_entry.options.get(CONF_CHANNELS, []))
        return self.async_show_form(
            step_id="init",
            data_schema=vol.Schema({vol.Optional(CONF_CHANNELS, default=current): str}),
            description_placeholders={"example": CHANNELS_HELP},
        )
