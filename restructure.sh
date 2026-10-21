#!/usr/bin/env bash
# Rebuilds the real folder structure from flat file names.
# A file named  app__src__main__res__values__strings.xml
# is moved to   app/src/main/res/values/strings.xml
# Files without "__" in their name are left where they are.
set -euo pipefail
cd "$(dirname "$0")"
for f in *__*; do
  [ -f "$f" ] || continue
  dest="${f//__//}"
  mkdir -p "$(dirname "$dest")"
  mv -f "$f" "$dest"
  echo "$f -> $dest"
done
