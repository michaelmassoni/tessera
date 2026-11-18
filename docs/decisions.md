# Decision log

Newest first. Each entry records the date, the decision, the reason and its status. `Proposed`
entries wait for the maintainer's sign-off. Agreed changes to the founding decisions are recorded
here.

## 2026-11-19: How Clock delivers alarms (Accepted)

**Decision:**

- Alarms use `AlarmManager.setAlarmClock`, with `USE_EXACT_ALARM` on API 33+ and
  `SCHEDULE_EXACT_ALARM` (`maxSdkVersion` 32) below that.
- Ringing runs in a foreground service of type `systemExempted`, with a full-screen-intent
  notification.
- State is JSON (kotlinx.serialization) in **device-protected storage**, and the receivers are
  direct-boot aware.
- Timers use `setExactAndAllowWhileIdle` on the elapsed clock instead of `setAlarmClock`.

**Reason:**

- `setAlarmClock` is the only API that is both exact and exempt from Doze.
- Android's foreground-service documentation lists apps holding either exact-alarm permission as
  eligible for `systemExempted`. Its alternatives are `mediaPlayback`, which can't be started from
  boot on Android 15+, and `shortService`, which is limited to 3 minutes. Neither fits.
- Device-protected storage lets alarms be rescheduled from `LOCKED_BOOT_COMPLETED`, before the
  first unlock.
- Timers stay off `setAlarmClock` so they don't show up as the "next alarm" on the lock screen.

**Verified on emulators:** API 37 (alarm, snooze, timer in forced deep Doze, rescheduling after
a reboot before first unlock) and API 31 (alarm via `SCHEDULE_EXACT_ALARM`, the untyped
`startForeground` path, and the revoked-permission banner).

**Risk to watch:** `setExactAndAllowWhileIdle` is rate-limited while the device is idle. The
emulator Doze test covers a single timer. Several timers expiring within minutes of each other
in deep Doze haven't been tested on a real device.

**Also added:** `kotlinx-serialization-json` and the Kotlin serialization plugin (JetBrains,
Apache-2.0, F-Droid compatible). The ktlint line limit is now 140, which is common for Compose
code.

## 2026-11-19: Static analysis is Android Lint + Compose lint rules + ktlint, without detekt (Accepted)

**Decision:** Android Lint (warnings treated as errors) with Slack's `compose-lint-checks`,
plus ktlint 1.8 in the `ktlint_official` style. Detekt isn't used for now.

**Reason:** Detekt 1.23.x (the latest stable) is built against Kotlin 2.0, and this project uses
Kotlin 2.4. Detekt 2.0 is still alpha. The Compose-specific rules people usually add through
detekt are also available as Lint checks. Revisit when detekt 2.0 is stable.

Lint checks that only report newer dependency versions (`GradleDependency`,
`NewerVersionAvailable`, `AndroidGradlePluginVersion`, `OldTargetApi`) are disabled so that a
new upstream release can't break CI on its own. Dependabot opens weekly update PRs instead.

## 2026-11-19: Screenshot tests generated from @Preview with Roborazzi (Accepted)

**Decision:** Roborazzi with Robolectric (native graphics, SDK 37). Its preview scanner turns
every `@Preview` into a screenshot test, and `@TesseraPreviews` gives the standard
phone/dark/landscape/tablet set. Goldens are committed and checked in `check`.

**Reason:** Roborazzi needs no device and fits Robolectric, which the apps' logic tests already
use. Generating tests from previews means screen authors write a preview, not test code.
Paparazzi would also work, but it doesn't use Robolectric's real Android framework.

**Pins:** `espresso-core` is forced to 3.7.0 because the 3.5.1 pulled in by Compose UI test
crashes on SDK 37. `ComposablePreviewScanner` is pinned to 0.9.3 because 0.10.0 is
binary-incompatible with Roborazzi 1.79.0. Remove both pins when upstream catches up.

## 2026-11-19: Files requests all-files access by default (Accepted)

**Decision:** Files asks for `MANAGE_EXTERNAL_STORAGE` ("All files access") on first launch,
as most file managers do. This replaces the founding preference for SAF and scoped storage
**for Files only**. Every other app still prefers system pickers.

**Reason:** Since Android 11, SAF can't browse the root of shared storage or `Android/data`,
and every folder needs its own picker grant, which makes a general-purpose file manager
impractical. F-Droid allows the permission. Google Play would require a policy declaration if
the app is ever published there.

## 2026-11-19: Private GitHub repo; merge each branch when CI passes (Accepted)

**Decision:** The repository is `michaelmassoni/tessera` and stays private for now. Each task
gets its own branch and pull request, which is merged into `main` once CI passes, so later
branches build on it.

## 2026-11-19: Package prefix io.github.michaelmassoni.tessera (Accepted)

**Decision:** Apps use `io.github.michaelmassoni.tessera.<app>`, replacing `org.tessera.<app>`.

**Reason:** `tessera.org` belongs to an unrelated nonprofit, and an Android package name can
never change after publication. The `io.github.<user>` namespace is one the maintainer already
controls, at no cost. The GitHub account name "Tessera" is also taken. The project name
Tessera itself is unchanged: there are no F-Droid or Android collisions.

## 2026-11-19: material3 pinned to 1.5.0-rc01 (Accepted)

**Decision:** Override the Compose BOM (2026.09.00, which ships material3 1.4.0) with
material3 `1.5.0-rc01`. Every other Compose artifact stays on the stable BOM (Compose 1.12.1).

**Reason:** In material3 1.4.0, the newest stable release (Sep 2025), `MaterialExpressiveTheme`,
`MotionScheme.expressive()` and `ExperimentalMaterial3ExpressiveApi` are `internal`, so the
founding decision "Material 3 Expressive" can't be built on stable. 1.5.0-rc01 (published
2026-11-04) is a release candidate. Its declared dependencies are the stable Compose 1.12.0
libraries.

**Consequences:** Expressive APIs still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
Keep those opt-ins inside `core:design` wherever possible, so that API changes are absorbed in
one module. Move to 1.5.0 stable as soon as it's released.

## 2026-11-19: Optional Nix dev shell (Accepted)

**Decision:** Add `flake.nix` as an optional local toolchain. The build itself doesn't depend
on Nix: CI and F-Droid use a standard JDK and Android SDK.

**Reason:** The maintainer's machine runs NixOS, where AGP's downloaded aapt2 can't run and
the SDK can't be installed imperatively.

## 2026-11-19: Founding decisions (Accepted)

Name Tessera, GPL-3.0, a fresh Compose codebase (Fossify used as a feature checklist only),
Material 3 Expressive with dynamic color, Kotlin + Compose + Gradle KTS with a version catalog,
minSdk 31 and target/compile SDK 37, a monorepo with `core:*` and `apps/*`, no proprietary
dependencies or Play Services, F-Droid/IzzyOnDroid/GitHub Releases first. CLAUDE.md lists the
build order.

Build details added to support these decisions:

- build-tools pinned to 37.0.0, because AGP 9.5 otherwise picks 36.0.0. Pinning also helps
  reproducible builds.
- `dependenciesInfo` disabled in every app. F-Droid rejects the Google-encrypted dependency
  blob.
