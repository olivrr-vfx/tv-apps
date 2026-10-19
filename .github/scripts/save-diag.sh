#!/usr/bin/env bash
# Commits ./diag to runs/<sha> on the diagnostics branch.
git config --global user.name "github-actions"
git config --global user.email "github-actions@users.noreply.github.com"
for i in 1 2 3; do
  rm -rf /tmp/d
  if ! git clone -q --depth 1 --branch diagnostics "$REPO_URL" /tmp/d; then
    git init -q /tmp/d && git -C /tmp/d checkout -q --orphan diagnostics
    git -C /tmp/d remote add origin "$REPO_URL"
  fi
  dir="/tmp/d/runs/${GITHUB_SHA::7}"
  mkdir -p "$dir"
  cp -r diag/. "$dir/" || true
  git -C /tmp/d add -A
  git -C /tmp/d commit -q -m "Diagnose ${GITHUB_SHA::7} ($GITHUB_WORKFLOW)" || exit 0
  git -C /tmp/d push -q origin diagnostics && exit 0
  sleep 5
done
