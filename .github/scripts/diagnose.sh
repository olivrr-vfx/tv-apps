#!/usr/bin/env bash
# Runs inside android-emulator-runner. Never fails: everything is collected for reading later.
OUT=diag
mkdir -p $OUT/api
PKG=dev.purpletv

shot() { sleep "${2:-10}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
open() { adb shell am start -W -a android.intent.action.VIEW -d "'$1'" $PKG > /dev/null; }
key() { for k in "$@"; do adb shell input keyevent "$k"; sleep 1.2; done; }
pick() { python3 - "$@" <<'PY'
import json, sys
path, field, cond = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    data = json.loads(open(path).read().rsplit("\n", 2)[0])
except Exception:
    sys.exit(0)
items = next((v for v in data.values() if isinstance(v, list)), [])
for it in items:
    if cond == "any" or str(it.get("live")).lower() == cond:
        print(it.get(field, "")); break
PY
}

adb install -r Couch.apk > $OUT/install.txt 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.hub.MainActivity
shot 01-dashboard 30

# Local API (pairing code comes from the app manifest the phone uses).
adb forward tcp:8765 tcp:8765
curl -s -m 10 http://127.0.0.1:8765/api/info > $OUT/api/info.json
CODE=$(curl -s -m 10 http://127.0.0.1:8765/manifest.webmanifest | grep -o 'code=[0-9]*' | head -1 | cut -d= -f2)
echo "code=$CODE" > $OUT/code.txt
api() {
  local name=$(echo "$1" | tr '/?=&' '____')
  curl -s -m 90 -w '\n%{http_code} %{time_total}s\n' -H "Authorization: Bearer $CODE" "http://127.0.0.1:8765/api/$1" > "$OUT/api/$name.txt"
  echo "api $1 -> $(tail -1 "$OUT/api/$name.txt")"
}
for p in state twitch/live twitch/top "twitch/search?q=papaplatte" youtube/feed youtube/shelves "youtube/search?q=minecraft" history; do api "$p"; done

open couch://app/twitch
shot 02-twitch-start 20
key KEYCODE_DPAD_RIGHT
shot 03-twitch-live 6
key KEYCODE_DPAD_RIGHT
shot 04-twitch-discover 8
key KEYCODE_DPAD_RIGHT
shot 05-twitch-games 8
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
shot 06-twitch-game 12

LOGIN=$(pick $OUT/api/twitch_top.txt login any)
[ -z "$LOGIN" ] && LOGIN=$(pick $OUT/api/twitch_search_q_papaplatte.txt login true)
echo "login=$LOGIN" >> $OUT/code.txt
if [ -n "$LOGIN" ]; then
  open "couch://watch/$LOGIN"
  shot 07-twitch-live-player 30
  key KEYCODE_DPAD_CENTER
  shot 08-twitch-player-controls 3
  open "couch://channel/$LOGIN"
  shot 09-twitch-channel 15
fi

open couch://app/youtube
shot 10-youtube 25
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
shot 11-youtube-scrolled 4

VID=$(pick $OUT/api/youtube_search_q_minecraft.txt id false)
echo "video=$VID" >> $OUT/code.txt
open "couch://youtube/watch/${VID:-dQw4w9WgXcQ}"
shot 12-youtube-watch 35
shot 13-youtube-watch-later 15

open "couch://youtube/search?q=lofi"
shot 14-youtube-search 20

open couch://home
shot 15-home-again 8
key KEYCODE_DPAD_LEFT
shot 16-rail 3
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
shot 17-settings 5
key KEYCODE_DPAD_DOWN
shot 18-settings-twitch 4
key KEYCODE_DPAD_DOWN
shot 19-settings-youtube 4
key KEYCODE_DPAD_DOWN
shot 20-settings-remote 4

api log
adb shell dumpsys activity activities | grep -i "mResumedActivity\|topResumedActivity" > $OUT/activity.txt
adb logcat -d -v time > $OUT/logcat-all.txt
PID=$(adb shell pidof $PKG)
echo "pid=$PID" >> $OUT/code.txt
adb logcat -d -v time --pid="$PID" > $OUT/logcat-app.txt 2>/dev/null
grep -E "AndroidRuntime|FATAL|Couch/" $OUT/logcat-all.txt > $OUT/logcat-errors.txt
bash .github/scripts/diagnose-web.sh "$CODE" || true
exit 0
