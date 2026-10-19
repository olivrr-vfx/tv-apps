#!/usr/bin/env bash
# Quick network probe (no emulator): which Twitch clients allow the device code login.
OUT=diag/probe; mkdir -p $OUT
for cid in kd1unb4b3q4t58fwlpcbzcbnm76a8fp ue6666qo983tsx6so1t0vnawi233wa kimne78kx3ncx6brgo4mv6wki5h1ko; do
  curl -s -m 20 -X POST https://id.twitch.tv/oauth2/device -d "client_id=$cid" -d "scopes=user:read:follows" > "$OUT/device_$cid.json"
  echo "$cid: $(head -c 300 "$OUT/device_$cid.json")"
  # Refresh with a bogus token shows whether the client wants a secret.
  curl -s -m 20 -X POST https://id.twitch.tv/oauth2/token -d "client_id=$cid" -d "grant_type=refresh_token" -d "refresh_token=abc" > "$OUT/refresh_$cid.json"
  echo "refresh $cid: $(head -c 300 "$OUT/refresh_$cid.json")"
done
exit 0
