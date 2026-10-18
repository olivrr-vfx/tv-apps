#!/usr/bin/env bash
# Quick network probe (no emulator): Twitch GQL queries without login.
OUT=diag/probe; mkdir -p $OUT
CID=kimne78kx3ncx6brgo4mv6wki5h1ko
q() {
  python3 -c 'import json,sys; print(json.dumps({"query": sys.argv[1]}))' "$2" > /tmp/body.json
  curl -s -m 30 https://gql.twitch.tv/gql -H "Client-ID: $CID" -H "Content-Type: application/json" \
    -H "Origin: https://www.twitch.tv" -H "Referer: https://www.twitch.tv/" --data @/tmp/body.json > "$OUT/$1.json"
  echo "$1: $(head -c 300 "$OUT/$1.json")"
}
S='id title viewersCount createdAt previewImageURL(width: 440, height: 248) broadcaster { id login displayName profileImageURL(width: 70) } game { id name displayName }'
q top "query { streams(first: 5, options: {sort: VIEWER_COUNT}) { edges { node { $S } } } }"
q top_de "query { streams(first: 5, options: {sort: VIEWER_COUNT, broadcasterLanguages: [DE]}) { edges { node { $S } } } }"
q games "query { games(first: 5) { edges { node { id name displayName boxArtURL(width: 285, height: 380) viewersCount } } } }"
q game_streams "query { game(name: \"Minecraft\") { id streams(first: 5, options: {sort: VIEWER_COUNT}) { edges { node { $S } } } } }"
q search_for "query { searchFor(userQuery: \"papa\", platform: \"web\") { channels { edges { item { ... on User { id login displayName profileImageURL(width: 150) stream { $S } } } } } } }"
q search_users "query { searchUsers(userQuery: \"papa\", first: 5) { edges { node { id login displayName profileImageURL(width: 150) stream { $S } } } } }"
q user "query { user(login: \"papaplatte\") { id login displayName description profileImageURL(width: 300) offlineImageURL bannerImageURL stream { $S } } }"
q videos "query { user(login: \"papaplatte\") { videos(first: 3, type: ARCHIVE, sort: TIME) { edges { node { id title lengthSeconds createdAt viewCount previewThumbnailURL(width: 320, height: 180) owner { id login displayName } game { name } } } } } }"
q highlights "query { user(login: \"papaplatte\") { videos(first: 3, type: HIGHLIGHT, sort: TIME) { edges { node { id title lengthSeconds } } } } }"
VID=$(grep -o '"id":"[0-9]\{6,\}"' $OUT/videos.json | head -1 | cut -d'"' -f4)
q video "query { video(id: \"$VID\") { id title lengthSeconds createdAt viewCount previewThumbnailURL(width: 320, height: 180) owner { id login displayName } game { name } } }"
q users_by_id "query { users(ids: [\"$(grep -o '"id":"[0-9]*"' $OUT/user.json | head -1 | cut -d'"' -f4)\"]) { id login profileImageURL(width: 70) } }"

q top60 "query { streams(first: 60, options: {sort: VIEWER_COUNT}) { edges { node { id } } } }"
q top30 "query { streams(first: 30, options: {sort: VIEWER_COUNT}) { edges { node { id } } } }"
q games60 "query { games(first: 60) { edges { node { id } } } }"
exit 0
