#!/usr/bin/env bash
# Runs inside android-emulator-runner. Never fails: everything is collected for reading later.
OUT=diag
mkdir -p $OUT/api
PKG=dev.purpletv

shot() { sleep "${2:-10}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
open() { adb shell am start -W -a android.intent.action.VIEW -d "$1" $PKG > /dev/null; }
key() { adb shell input keyevent "$@"; sleep 1.5; }

adb install -r Couch.apk > $OUT/install.txt 2>&1
adb logcat -c
adb shell am start -W -n $PKG/.hub.MainActivity
shot 01-dashboard 25

# Local API (pairing code comes from the app manifest the phone uses).
adb forward tcp:8765 tcp:8765
curl -s -m 10 http://127.0.0.1:8765/api/info > $OUT/api/info.json
CODE=$(curl -s -m 10 http://127.0.0.1:8765/manifest.webmanifest | grep -o 'code=[0-9]*' | head -1 | cut -d= -f2)
echo "$CODE" > $OUT/code.txt
api() {
  local name=$(echo "$1" | tr '/?=&' '____')
  curl -s -m 90 -w '\n%{http_code} %{time_total}s\n' -H "Authorization: Bearer $CODE" "http://127.0.0.1:8765/api/$1" > "$OUT/api/$name.txt"
  echo "api $1 -> $(tail -1 "$OUT/api/$name.txt")"
}
for p in state twitch/live twitch/top "twitch/search?q=papaplatte" youtube/feed youtube/shelves youtube/subscriptions "youtube/search?q=lofi" history; do api "$p"; done

open purpletv://app/twitch
shot 02-twitch 20
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
shot 03-twitch-scrolled 4

LOGIN=$(grep -o '"login":"[^"]*"' $OUT/api/twitch_top.txt | head -1 | cut -d'"' -f4)
echo "login=$LOGIN" >> $OUT/code.txt
if [ -n "$LOGIN" ]; then
  open "purpletv://watch/$LOGIN"
  shot 04-twitch-live 25
  open "purpletv://channel/$LOGIN"
  shot 05-twitch-channel 15
fi

open purpletv://app/youtube
shot 06-youtube 25
key KEYCODE_DPAD_DOWN
shot 07-youtube-scrolled 4

VID=$(grep -o '"id":"[A-Za-z0-9_-]\{11\}"' $OUT/api/youtube_search_q_lofi.txt | head -1 | cut -d'"' -f4)
echo "video=$VID" >> $OUT/code.txt
open "purpletv://youtube/watch/${VID:-jfKfPfyJRdk}"
shot 08-youtube-watch 30

open "purpletv://youtube/search?q=minecraft"
shot 09-youtube-search 20

open purpletv://home
shot 10-home-again 8
key KEYCODE_DPAD_LEFT
shot 11-rail 3

# Settings: the rail entry is the last one; walk down to it.
key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER
shot 12-settings 5

adb shell dumpsys activity activities | grep -i "mResumedActivity\|topResumedActivity" > $OUT/activity.txt
adb logcat -d -v time > $OUT/logcat-all.txt
PID=$(adb shell pidof $PKG)
echo "pid=$PID" >> $OUT/code.txt
adb logcat -d -v time --pid="$PID" > $OUT/logcat-app.txt 2>/dev/null
grep -E "AndroidRuntime|FATAL|$PKG|purpletv|Couch|Exception|Error" $OUT/logcat-all.txt | grep -v "^--------" > $OUT/logcat-errors.txt
bash .github/scripts/diagnose-web.sh "$CODE" || true
exit 0
