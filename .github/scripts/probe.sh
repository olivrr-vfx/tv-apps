#!/usr/bin/env bash
OUT=diag/probe; mkdir -p $OUT
TV=ue6666qo983tsx6so1t0vnawi233wa
q() {
  python3 -c 'import json,sys; print(json.dumps({"query": sys.argv[1]}))' "$2" > /tmp/body.json
  curl -s -m 30 https://gql.twitch.tv/gql -H "Client-ID: $3" -H "Content-Type: application/json" --data @/tmp/body.json > "$OUT/$1.json"
  echo "$1: $(head -c 400 "$OUT/$1.json")"
}
q live_tv "query { currentUser { id login followedLiveUsers(first: 5) { edges { node { id login displayName profileImageURL(width: 150) stream { id title viewersCount createdAt previewImageURL(width: 440, height: 248) game { name } } } } pageInfo { hasNextPage } } } }" $TV
q follows_tv "query { currentUser { id follows(first: 5) { edges { cursor followedAt node { id login displayName profileImageURL(width: 150) } } pageInfo { hasNextPage } } } }" $TV
q live_web "query { currentUser { id followedLiveUsers(first: 5) { edges { node { login } } } } }" kimne78kx3ncx6brgo4mv6wki5h1ko
q top_tv "query { streams(first: 2) { edges { node { id } } } }" $TV
curl -s -m 20 "https://api.twitch.tv/helix/streams?first=1" -H "Client-Id: $TV" > $OUT/helix_tv.json; echo "helix: $(cat $OUT/helix_tv.json)"
curl -s -m 20 "https://api.twitch.tv/helix/streams?first=1" -H "Client-Id: $TV" -H "Authorization: Bearer abc" > $OUT/helix_tv_bad.json; echo "helix bad: $(cat $OUT/helix_tv_bad.json)"
exit 0
