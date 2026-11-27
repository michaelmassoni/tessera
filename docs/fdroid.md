# F-Droid

Tessera's apps are offered in F-Droid's main repository **and** on GitHub (for Obtainium), as the
same APKs signed with the same key, so a phone can switch between the two without uninstalling.

## How: reproducible builds

F-Droid builds every app from source. Normally it then signs the APK with its own key, which can't
update (or be updated by) our GitHub APK. Instead, each recipe names our published APK
(`Binaries:`) and our certificate (`AllowedAPKSigningKeys:`). F-Droid builds the tag, compares its
unsigned APK with ours byte for byte apart from the signature, and when they match publishes **our**
signed APK. If they don't match, that version isn't published on F-Droid (GitHub is unaffected).

So every release must be reproducible: the same commit must always give the same APK, on any
machine. Checked so far (2026-11-27): Calculator, Files and Weather rebuilt from their tags on a
different machine, JDK and path matched the published APKs exactly. Music didn't, half the time:
R8 sometimes narrowed one kotlinx.coroutines method's parameter type and sometimes not.
`build-logic/reproducible-rules.pro` (applied to every app) pins it; twelve R8 runs then gave the
same output. What else keeps builds reproducible:

- No signing in Gradle without the `TESSERA_*` variables, so F-Droid gets an unsigned APK.
- `dependenciesInfo` off (F-Droid rejects Google's encrypted dependency blob).
- Pinned build-tools (37.0.0) and compileSdk (37.1); JDK 21 on both sides.
- No timestamps or machine paths in the APK; nothing generated from the build date.

The release workflow checks this on every tag: a second job rebuilds the tag on a fresh machine,
unsigned, and compares it with the published APK (`apksigcopier compare`). If that job fails, the
release still stands on GitHub, but F-Droid will reject that version: fix the cause (usually an R8
rule) and release again.

## What's in the repository

- `apps/<app>/fastlane/metadata/android/en-US/`: the store listing F-Droid shows: `title.txt`,
  `short_description.txt` (80 characters at most), `full_description.txt`, `changelogs/<versionCode>.txt`,
  and `images/` (512 px icon, phone screenshots). F-Droid reads these from the tagged commit.
- `scripts/fastlane-images.sh`: makes the icons (from `docs/icons/`) and screenshots (from
  `docs/screenshots/`) for every app. Run it after changing either.
- `scripts/fdroid-metadata.sh`: writes the fdroiddata recipes (`fdroid/metadata/<appId>.yml`) from
  each app's current version.
- Anti-features: Podcasts (Apple's podcast directory), Sports (ESPN) and Weather (Bureau of
  Meteorology) are marked NonFreeNet: they use services that aren't free software. Nothing else
  talks to anything but servers you choose (CalDAV, CardDAV, Subsonic, yarr) or free services
  (Open-Meteo, LRCLIB, fyyd).

## Releasing (each version)

1. Bump `versionCode` and `versionName` as usual, and add
   `apps/<app>/fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (a line or two; F-Droid
   shows it as "What's new").
2. Tag `<app>-v<version>` (one tag per push: GitHub ignores pushes of more than three tags). The
   release workflow publishes on GitHub, then checks reproducibility.
3. Nothing to do on F-Droid: its bot sees the new tag (`UpdateCheckMode: Tags`), adds a build to the
   recipe, builds it within a day or two and publishes it when it matches.

## Getting listed (once): what the maintainer does

1. Create an account on GitLab (gitlab.com) and fork https://gitlab.com/fdroid/fdroiddata.
2. Release every app once after this F-Droid work is merged, so the tags carry the store listings and
   the R8 fix. Then run `scripts/fdroid-metadata.sh` and copy `fdroid/metadata/*.yml` into the
   fork's `metadata/`.
3. Check locally if you like (optional): with fdroidserver installed, `fdroid lint <appId>` and
   `fdroid build -v -l <appId>` in the fork.
4. Open a merge request ("New apps: Tessera suite"), following fdroiddata's template. Sixteen apps is
   a lot for one review; submitting a few first (Calculator, Clock, Notes, Files) and the rest once
   those are in may go faster.
5. Answer the reviewers. Common requests: categories (ours are best guesses from F-Droid's list),
   and build details. The licence is GPL-3.0-or-later (the maintainer's choice, 2026-11-27).
6. Once merged, the apps appear in F-Droid after its next build cycle (usually within a few days).
   Then add an F-Droid badge and links to the README.

Until then, and afterwards, GitHub releases and `docs/obtainium.json` work exactly as before.
