#!/usr/bin/env bash
# Screenshots of the Couch web app served by the emulator (port 8765 is forwarded).
CODE="$1"
OUT=diag/web
mkdir -p $OUT
CHROME=$(command -v google-chrome || command -v chromium || command -v chromium-browser)
[ -z "$CHROME" ] && { echo "no chrome" > $OUT/error.txt; exit 0; }
web() {
  "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --window-size="$2" \
    --virtual-time-budget=20000 --screenshot="$OUT/$1.png" "http://127.0.0.1:8765/?code=$CODE$3" > /dev/null 2>&1
  "$CHROME" --headless=new --no-sandbox --disable-gpu --virtual-time-budget=20000 --dump-dom \
    "http://127.0.0.1:8765/?code=$CODE$3" > "$OUT/$1.html" 2>/dev/null
  echo "web $1"
}
web desktop-start 1440,900 ""
web desktop-twitch 1440,900 "#twitch"
web desktop-youtube 1440,900 "#youtube"
web desktop-share 1440,900 "#share"
web phone-start 390,844 ""
web phone-remote 390,844 "#remote"
exit 0
