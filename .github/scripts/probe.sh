#!/usr/bin/env bash
OUT=diag/probe; mkdir -p $OUT
TV=ue6666qo983tsx6so1t0vnawi233wa
q() {
  python3 -c 'import json,sys; print(json.dumps({"query": sys.argv[1], "variables": {"after": None}}))' "$2" > /tmp/body.json
  curl -s -m 30 https://gql.twitch.tv/gql -H "Client-ID: $TV" -H "Content-Type: application/json" --data @/tmp/body.json > "$OUT/$1.json"
  echo "$1: $(head -c 400 "$OUT/$1.json")"
}
S='id title viewersCount createdAt previewImageURL(width: 440, height: 248) broadcaster { id login displayName profileImageURL(width: 150) } game { id name displayName }'
q live 'query($after: Cursor) { currentUser { followedLiveUsers(first: 30, after: $after) { edges { cursor node { id login displayName profileImageURL(width: 150) stream { '"$S"' } } } pageInfo { hasNextPage } } } }'
q follows 'query($after: Cursor) { currentUser { follows(first: 30, after: $after) { edges { cursor node { id login displayName profileImageURL(width: 150) } } pageInfo { hasNextPage } } } }'
q self 'query { currentUser { id login displayName profileImageURL(width: 150) } }'
q wrongfield 'query { currentUser { doesNotExist } }'
exit 0
