# Tessera

Free, open-source Android apps that replace the default Google apps, built to look and feel
first-party on current Android: Material 3 Expressive, dynamic color, edge-to-edge, predictive
back and adaptive layouts for phones, foldables and tablets.

A *tessera* is one tile in a mosaic. Each app is a separate tile, and the apps share one
design system.

- **No** ads, trackers, analytics, proprietary libraries or Google Play Services.
- Minimal permissions; system pickers (SAF, photo picker) instead of broad storage access.
- Licensed under the [GNU GPL v3.0](LICENSE).

## Apps

| App        | Package                 | Status        |
|------------|-------------------------|---------------|
| Calculator | `org.tessera.calculator` | Scaffold only |
| Clock      | `org.tessera.clock`      | Scaffold only |
| Files      | `org.tessera.files`      | Scaffold only |

The planned order after these is in [CLAUDE.md](CLAUDE.md#app-order).

## Building

Requirements: JDK 17+ (21 recommended), Android SDK with platform `android-37.0` and
build-tools `37.0.0`.

```sh
./gradlew assembleDebug check
```

APKs land in `apps/<app>/build/outputs/apk/debug/`.

### Nix (optional)

`flake.nix` provides a dev shell with JDK 21 and the Android SDK:

```sh
nix develop               # build tooling
nix develop .#emulator    # + emulator and API 37 AOSP system image
```

## Layout

```
core/design     TesseraTheme and shared Material 3 Expressive components
apps/<name>     one module per app; depends only on core:*
docs/           specs per app and the decision log (docs/decisions.md)
```

## Distribution

F-Droid, IzzyOnDroid and GitHub Releases. Google Play may follow later.
