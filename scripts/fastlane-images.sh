#!/usr/bin/env bash
# Store-listing images for F-Droid (docs/fdroid.md): each app's 512 px icon (from docs/icons/<app>.svg)
# and its phone screenshots (docs/screenshots/<app>-light.png and -dark.png), into
# apps/<app>/fastlane/metadata/android/en-US/images/. Re-run after changing an icon or screenshot.
#
#   nix shell nixpkgs#librsvg -c scripts/fastlane-images.sh
set -euo pipefail
cd "$(dirname "$0")/.."
for dir in apps/*/; do
  app=$(basename "$dir")
  images="$dir/fastlane/metadata/android/en-US/images"
  mkdir -p "$images/phoneScreenshots"
  rsvg-convert -w 512 -h 512 "docs/icons/$app.svg" -o "$images/icon.png"
  n=1
  for theme in light dark; do
    shot="docs/screenshots/$app-$theme.png"
    if [ -f "$shot" ]; then cp "$shot" "$images/phoneScreenshots/$n.png"; n=$((n + 1)); fi
  done
done
