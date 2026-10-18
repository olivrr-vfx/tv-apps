"""Constants for the PurpleTV integration."""

DOMAIN = "purpletv"

DEFAULT_PORT = 8765

CONF_CODE = "code"
CONF_CHANNELS = "channels"

TV_SCAN_SECONDS = 5
LIVE_SCAN_SECONDS = 60

APP_NAMES = {
    "dashboard": "PurpleTV",
    "twitch": "Twitch",
    "youtube": "YouTube",
    "settings": "PurpleTV",
}

# Keys accepted by remote.send_command (PurpleTV /api/key), plus a few extras.
REMOTE_KEYS = ["up", "down", "left", "right", "ok", "back", "menu", "play_pause", "channel_up", "channel_down"]
KEY_ALIASES = {"select": "ok", "center": "ok", "enter": "ok", "return": "back"}
