#!/usr/bin/env bash
# Prints the Obtainium links used in README.md (one per app) and writes docs/obtainium.json, which
# adds every app at once from Obtainium's Import/Export screen. (Obtainium's redirect page only
# takes single-app links, and GitHub strips obtainium:// links, so "all apps" has to be a file.)
# Tessera's apps share one GitHub repository, so each entry filters releases by title
# ("Tessera Notes 0.1.2") and APKs by name ("tessera-notes-0.1.2.apk"); without the filters
# Obtainium would track whichever app released last.
# Usage: scripts/obtainium-links.sh calculator clock ...   (defaults to every app in apps/)
set -euo pipefail
cd "$(dirname "$0")/.."
repo=https://github.com/michaelmassoni/tessera
apps=("$@")
[ ${#apps[@]} -gt 0 ] || mapfile -t apps < <(ls apps)

entry() {
  local app=$1 title
  title="Tessera ${app^}"
  jq -nc --arg id "io.github.michaelmassoni.tessera.$app" --arg url "$repo" --arg name "$title" \
    --arg settings "$(jq -nc --arg t "^$title " --arg a "^tessera-$app-" --arg n "$title" \
      '{includePrereleases: false, fallbackToOlderReleases: true, filterReleaseTitlesByRegEx: $t,
        apkFilterRegEx: $a, versionDetection: true, autoApkFilterByArch: true, appName: $n}')" \
    '{id: $id, url: $url, author: "michaelmassoni", name: $name, preferredApkIndex: 0, additionalSettings: $settings}'
}

link() { echo "https://apps.obtainium.imranr.dev/redirect?r=obtainium://$1/$(jq -rn --arg s "$2" '$s|@uri')"; }

all=()
for app in "${apps[@]}"; do
  e=$(entry "$app")
  all+=("$e")
  echo "[obtainium $app]: $(link app "$e")"
done
printf '%s\n' "${all[@]}" | jq -s '{apps: .}' > docs/obtainium.json
