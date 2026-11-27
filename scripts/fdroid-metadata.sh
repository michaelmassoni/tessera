#!/usr/bin/env bash
# Writes the fdroiddata build recipes for every app into fdroid/metadata/ (docs/fdroid.md), from
# each app's current versionName and versionCode. Copy them into a fork of
# https://gitlab.com/fdroid/fdroiddata (metadata/) to submit or update the apps.
#
# The recipes use reproducible builds: F-Droid builds each release from its tag and, when the
# result matches the APK on GitHub byte for byte, publishes that APK, signed with the maintainer's
# key. So F-Droid and GitHub (Obtainium) installs are the same and update each other.
set -euo pipefail
cd "$(dirname "$0")/.."
repo=https://github.com/michaelmassoni/tessera
# SHA-256 of the release signing certificate (public: apksigner verify --print-certs <apk>).
cert=25ccbbff35e9147b00ded2860ae9794fe403216e8f278e93ad680dc00ee78f85
category() {
  case $1 in
    calculator) echo Calculator ;; calendar) echo "Calendar & Agenda" ;; clock) echo Clock ;;
    contacts) echo Contact ;; feeds) echo News ;; files) echo "File Management" ;;
    gallery) echo Gallery ;; messages) echo "Phone & SMS" ;; music) echo "Local Media Player" ;;
    notes) echo Note ;; phone) echo "Phone & SMS" ;; podcasts) echo Podcast ;;
    recorder) echo Multimedia ;; sports) echo "Sports & Health" ;; tasks) echo Task ;;
    weather) echo Weather ;;
  esac
}
# Apps that talk to a service that isn't free software (F-Droid's NonFreeNet anti-feature).
nonfreenet() {
  case $1 in
    podcasts) echo "Show search uses the Apple Podcasts directory (fyyd, which is free, too)." ;;
    sports) echo "Scores come from ESPN's public data service." ;;
    weather) echo "Forecasts in Australia come from the Bureau of Meteorology (Open-Meteo elsewhere)." ;;
  esac
}
mkdir -p fdroid/metadata
for dir in apps/*/; do
  app=$(basename "$dir")
  id=io.github.michaelmassoni.tessera.$app
  name=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$dir/build.gradle.kts")
  code=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$dir/build.gradle.kts")
  title=$(cat "$dir/fastlane/metadata/android/en-US/title.txt")
  out=fdroid/metadata/$id.yml
  {
    echo "Categories:"
    echo "  - $(category "$app")"
    echo "License: GPL-3.0-or-later"
    echo "SourceCode: $repo"
    echo "IssueTracker: $repo/issues"
    echo "Changelog: $repo/releases"
    note=$(nonfreenet "$app")
    if [ -n "$note" ]; then
      echo
      echo "AntiFeatures:"
      echo "  NonFreeNet:"
      echo "    en-US: $note"
    fi
    echo
    echo "AutoName: $title"
    echo
    echo "RepoType: git"
    echo "Repo: $repo.git"
    echo "Binaries: $repo/releases/download/$app-v%v/tessera-$app-%v.apk"
    echo
    echo "Builds:"
    echo "  - versionName: $name"
    echo "    versionCode: $code"
    echo "    commit: $app-v$name"
    echo "    subdir: apps/$app"
    echo "    sudo:"
    echo "      - echo 'deb https://deb.debian.org/debian trixie main' > /etc/apt/sources.list.d/trixie.list"
    echo "      - apt-get update"
    echo "      - apt-get install -y -t trixie openjdk-21-jdk-headless"
    echo "      - update-java-alternatives -a"
    echo "    gradle:"
    echo "      - yes"
    echo "    prebuild: sdkmanager 'platforms;android-37.1' 'build-tools;37.0.0'"
    echo
    echo "AllowedAPKSigningKeys: $cert"
    echo
    echo "AutoUpdateMode: Version"
    echo "UpdateCheckMode: Tags ^$app-v.*\$"
    echo "CurrentVersion: $name"
    echo "CurrentVersionCode: $code"
  } > "$out"
done
ls fdroid/metadata | wc -l
