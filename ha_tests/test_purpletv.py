"""Tests for the PurpleTV Home Assistant integration (API mocked)."""

from unittest.mock import patch

from homeassistant import config_entries
from homeassistant.const import CONF_HOST, CONF_PORT
from homeassistant.core import HomeAssistant
from homeassistant.data_entry_flow import FlowResultType
from homeassistant.helpers.service_info.zeroconf import ZeroconfServiceInfo
from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.purpletv.api import GQL_URL
from custom_components.purpletv.config_flow import parse_channels
from custom_components.purpletv.const import CONF_CHANNELS, CONF_CODE, DOMAIN

HOST = "192.168.1.50"
BASE = f"http://{HOST}:8765/api"

STATE_LIVE = {
    "version": "1.0.12",
    "screen": "live",
    "playing": True,
    "paused": False,
    "muted": False,
    "volume": 0.8,
    "channel": {
        "login": "papaplatte",
        "display_name": "Papaplatte",
        "title": "CRAFTATTACK TAG 9",
        "game": "Minecraft",
        "viewers": 36000,
        "started_at": "2026-10-18T12:00:00Z",
        "thumbnail": "https://static-cdn.jtvnw.net/previews-ttv/live_user_papaplatte-640x360.jpg",
        "avatar": "",
    },
    "vod": None,
    "followed_live": [
        {"login": "papaplatte", "display_name": "Papaplatte", "title": "", "game": "", "viewers": 1, "started_at": "", "thumbnail": "", "avatar": ""},
        {"login": "zarbex", "display_name": "zarbex", "title": "", "game": "", "viewers": 1, "started_at": "", "thumbnail": "", "avatar": ""},
    ],
}

GQL_RESPONSE = {
    "data": {
        "users": [
            {
                "login": "papaplatte",
                "displayName": "Papaplatte",
                "profileImageURL": "https://example/avatar.png",
                "broadcastSettings": {"title": "CRAFTATTACK TAG 9"},
                "stream": {"id": "1", "viewersCount": 36000, "createdAt": "2026-10-18T12:00:00Z", "previewImageURL": "https://example/p.jpg", "game": {"displayName": "Minecraft"}},
            },
            {
                "login": "zarbex",
                "displayName": "zarbex",
                "profileImageURL": None,
                "broadcastSettings": {"title": "offline title"},
                "stream": None,
            },
        ]
    }
}


def test_parse_channels():
    assert parse_channels("Papaplatte, twitch.tv/zarbex;  x  , papaplatte, https://www.twitch.tv/eliasn97/") == [
        "papaplatte", "zarbex", "eliasn97",
    ]


async def test_user_flow(hass: HomeAssistant, aioclient_mock):
    aioclient_mock.get(f"{BASE}/state", json=STATE_LIVE)
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_USER})
    assert result["type"] is FlowResultType.FORM
    with patch("custom_components.purpletv.async_setup_entry", return_value=True):
        result = await hass.config_entries.flow.async_configure(
            result["flow_id"], {CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "123456", CONF_CHANNELS: "Papaplatte, zarbex"}
        )
    assert result["type"] is FlowResultType.CREATE_ENTRY
    assert result["data"] == {CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "123456"}
    assert result["options"] == {CONF_CHANNELS: ["papaplatte", "zarbex"]}


async def test_user_flow_errors(hass: HomeAssistant, aioclient_mock):
    aioclient_mock.get(f"{BASE}/state", status=401)
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_USER})
    result = await hass.config_entries.flow.async_configure(
        result["flow_id"], {CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "000000", CONF_CHANNELS: ""}
    )
    assert result["errors"] == {"base": "invalid_auth"}

    aioclient_mock.clear_requests()
    aioclient_mock.get(f"{BASE}/state", exc=TimeoutError())
    result = await hass.config_entries.flow.async_configure(
        result["flow_id"], {CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "000000", CONF_CHANNELS: ""}
    )
    assert result["errors"] == {"base": "cannot_connect"}


async def test_zeroconf_flow(hass: HomeAssistant, aioclient_mock):
    aioclient_mock.get(f"{BASE}/state", json=STATE_LIVE)
    info = ZeroconfServiceInfo(
        ip_address=__import__("ipaddress").ip_address(HOST),
        ip_addresses=[__import__("ipaddress").ip_address(HOST)],
        hostname="tv.local.",
        name="PurpleTV._purpletv._tcp.local.",
        port=8765,
        type="_purpletv._tcp.local.",
        properties={},
    )
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": config_entries.SOURCE_ZEROCONF}, data=info)
    assert result["type"] is FlowResultType.FORM
    assert result["step_id"] == "confirm"
    with patch("custom_components.purpletv.async_setup_entry", return_value=True):
        result = await hass.config_entries.flow.async_configure(result["flow_id"], {CONF_CODE: "123456", CONF_CHANNELS: ""})
    assert result["type"] is FlowResultType.CREATE_ENTRY
    assert result["data"][CONF_HOST] == HOST


async def _setup(hass, aioclient_mock, state):
    aioclient_mock.get(f"{BASE}/state", json=state)
    aioclient_mock.post(GQL_URL, json=GQL_RESPONSE)
    entry = MockConfigEntry(
        domain=DOMAIN,
        unique_id=HOST,
        data={CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "123456"},
        options={CONF_CHANNELS: ["papaplatte", "zarbex"]},
    )
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    return entry


def _find(hass, domain, suffix):
    matches = [s for s in hass.states.async_all(domain) if s.entity_id.endswith(suffix)]
    assert matches, f"no {domain} entity ending in {suffix}: {[s.entity_id for s in hass.states.async_all(domain)]}"
    return matches[0]


async def test_entities_live(hass: HomeAssistant, aioclient_mock):
    await _setup(hass, aioclient_mock, STATE_LIVE)

    player = hass.states.get("media_player.purpletv")
    assert player.state == "playing"
    assert player.attributes["media_title"] == "CRAFTATTACK TAG 9"
    assert player.attributes["media_artist"] == "Papaplatte"
    assert player.attributes["volume_level"] == 0.8
    assert player.attributes["source_list"] == ["Papaplatte", "zarbex"]

    assert hass.states.get("binary_sensor.purpletv_watching_twitch").state == "on"
    assert hass.states.get("sensor.purpletv_current_channel").state == "Papaplatte"
    assert hass.states.get("sensor.purpletv_followed_channels_live").state == "2"

    papa = _find(hass, "binary_sensor", "papaplatte_live")
    assert papa.state == "on"
    assert papa.attributes["game"] == "Minecraft"
    assert _find(hass, "binary_sensor", "zarbex_live").state == "off"


async def test_tv_off(hass: HomeAssistant, aioclient_mock):
    aioclient_mock.get(f"{BASE}/state", exc=TimeoutError())
    aioclient_mock.post(GQL_URL, json=GQL_RESPONSE)
    entry = MockConfigEntry(domain=DOMAIN, unique_id=HOST, data={CONF_HOST: HOST, CONF_PORT: 8765, CONF_CODE: "1"}, options={CONF_CHANNELS: ["papaplatte"]})
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    assert hass.states.get("media_player.purpletv").state == "off"
    assert hass.states.get("binary_sensor.purpletv_watching_twitch").state == "off"
    # Live sensors keep working with the TV off.
    assert _find(hass, "binary_sensor", "papaplatte_live").state == "on"


async def test_commands(hass: HomeAssistant, aioclient_mock):
    await _setup(hass, aioclient_mock, STATE_LIVE)
    for path in ["pause", "play", "mute", "volume", "open", "vod", "channel", "home"]:
        aioclient_mock.post(f"{BASE}/{path}", json={"ok": True})

    async def call(service, **data):
        await hass.services.async_call("media_player", service, {"entity_id": "media_player.purpletv", **data}, blocking=True)

    await call("media_pause")
    await call("volume_mute", is_volume_muted=True)
    await call("volume_set", volume_level=0.5)
    await call("play_media", media_content_type="channel", media_content_id="Papaplatte")
    await call("play_media", media_content_type="video", media_content_id="https://www.twitch.tv/videos/123456")
    await call("play_media", media_content_type="channel", media_content_id="channel:zarbex")
    await call("select_source", source="zarbex")
    await call("media_stop")

    posts = [(str(url), dict(params) if params else {}) for method, url, params, _ in [
        (c[0], c[1], c[1].query, None) for c in aioclient_mock.mock_calls
    ] if method == "POST" and "gql" not in str(url)]
    paths = [u.split("/api/")[1].split("?")[0] for u, _ in posts]
    assert paths == ["pause", "mute", "volume", "open", "vod", "channel", "open", "home"]
    assert posts[1][1] == {"mute": "true"}
    assert posts[3][1] == {"login": "papaplatte"}
    assert posts[4][1] == {"id": "123456"}
    assert posts[6][1] == {"login": "zarbex"}
