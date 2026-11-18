# Tessera: conventions for contributors and AI sessions

Tessera is a suite of free (GPL-3.0) Android apps that replace Google's default apps and should
be indistinguishable from first-party apps on current Android. Read this file and
`docs/decisions.md` before changing anything. Decisions here are settled. To change one, raise it
with the maintainer first, then record the agreed change in `docs/decisions.md` with the date and
the reason.

## Stack

- Kotlin and Jetpack Compose only. No XML layouts and no View-based UI (the exceptions are
  manifest, resources and window themes).
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Every dependency and
  plugin version lives in the catalog, never inline.
- AGP 9 uses built-in Kotlin. Don't apply `org.jetbrains.kotlin.android` in modules. It's
  declared `apply false` at the root only to pin the Kotlin version.
- minSdk 31, targetSdk 37, compileSdk 37, build-tools 37.0.0 (pinned for reproducible
  F-Droid builds).
- Use the latest **stable** versions. Any pre-release needs a comment in the catalog and an entry
  in `docs/decisions.md` (the only current one is material3; see there).

## Layout

```
core/design       TesseraTheme (MaterialExpressiveTheme + dynamic color), shared components
apps/<name>       one application module per app, package org.tessera.<name>
docs/<name>.md    spec for each app, written before the implementation
docs/decisions.md dated decision log
```

- An app depends only on `core:*`, never on another app.
- Add a new `core:*` module (for example `core:data` or `core:settings`) only when a **second**
  app needs the code. Until then the code stays in the app.
- Keep logic in plain, testable Kotlin classes (no Android types where you can avoid them) and
  keep composables thin. Every non-trivial logic class gets unit tests.

## Design rules

- Wrap every screen in `TesseraTheme`. Use Material 3 / Expressive components and
  `MaterialTheme` tokens (colorScheme, typography, shapes, motionScheme).
- Don't hard-code colors in UI code. The only exception is launcher icon backgrounds; the
  monochrome layer covers themed icons.
- Don't build a custom widget that duplicates a Material component.
- Required in every app: dynamic color, dark mode, edge-to-edge (`enableEdgeToEdge()` with
  insets handled by Scaffold or `WindowInsets`), predictive back (don't intercept back without
  `PredictiveBackHandler`/`BackHandler`), and adaptive layouts for compact, medium and expanded
  widths. API 37 ignores orientation and resizability locks on large screens, so never rely on
  them.
- All user-facing strings go in `strings.xml`. Provide content descriptions for icons and
  check TalkBack order, touch target size (48dp) and font scaling up to 200%.
- `TesseraTheme(dynamicColor = false)` gives deterministic colors for previews and screenshot
  tests.

## Privacy and F-Droid rules

- No ads, trackers, analytics, crash reporters that phone home, proprietary libraries, Google
  Play Services or Firebase. Every dependency must be open source and buildable by F-Droid.
- Keep `dependenciesInfo { includeInApk = false; includeInBundle = false }` in every app.
- Request minimal permissions. Prefer SAF and the photo picker. Any broad permission (all-files
  access, exact alarms, contacts and so on) must be justified in the app's spec.
- No network access unless the app's spec justifies it.

## Workflow

- One branch per app (`calculator`, `clock`, ...) with small, focused commits.
- `./gradlew check` must pass before every commit. CI runs `./gradlew assembleDebug check`.
- Write `docs/<app>.md` (features, screens, edge cases, accessibility, permissions) before
  writing app code.
- Fossify is a checklist of behaviour users expect. Use it for ideas only: never copy its code
  or resources.
- Run on an emulator where you can, and state plainly what wasn't verified on a device.
- Never add `Co-Authored-By` trailers for Claude to commits.

## Local tooling (NixOS)

`nix develop` provides JDK 21 and the Android SDK. It also overrides aapt2 through
`GRADLE_OPTS`, because the Maven aapt2 can't run on NixOS. `nix develop .#emulator` adds the
emulator and an API 37 AOSP (no Google APIs) x86_64 image. AVDs live in `./.android`, which is
gitignored.

## App order

1. Calculator, Clock, Files
2. Contacts, Notes
3. Gallery, Calendar, Music
4. Phone, Messages (need a real device for testing)
5. Camera, Keyboard, Launcher

Out of scope: Maps, cloud Photos and Gmail-style services (no offline equivalent).
