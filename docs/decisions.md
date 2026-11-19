# Decision log

Newest first. Each entry records the date, the decision, the reason and its status. `Proposed`
entries wait for the maintainer's sign-off. Agreed changes to the founding decisions are recorded
here.

## 2026-11-19: Launcher and Camera are dropped from the suite (Accepted)

**Decision:** Tessera no longer includes a launcher or a camera. The modules, specs and CameraX
catalog entries are removed; the last versions are in git history (tags `launcher-v0.1.1` and
`camera-v0.1.0`).

**Reason:** the maintainer doesn't need them. The suite focuses on the apps that replace Google's
defaults where there's a clear gap.

## 2026-11-19: Keyboard is dropped from the suite (Accepted)

**Decision:** Tessera no longer includes a keyboard. The module, spec and dictionary script are
removed; the last version is in git history (tag `keyboard-v0.2.0`).

**Reason:** in testing on a real phone, fast typing dropped keys and felt laggy. A keyboard has to
match a mature system keyboard to be worth switching to, and the maintainer chose not to pursue it.

## 2026-11-19: Keyboard dictionary and autocorrect (Superseded: Keyboard dropped)

**Decision:** The keyboard ships an English dictionary built from SCOWL (which words exist) and
FrequencyWords (how common they are), and corrects typos offline. Auto-capitalisation applies to
sentence starts in every ordinary text field unless turned off.

**Reason:**

- **Two sources:** FrequencyWords alone strips apostrophes and contains subtitle typos ("helo"),
  so it can't say which words are real. SCOWL is the list spell checkers use, but has no
  frequencies.
- **Licences:** SCOWL's licence is permissive. FrequencyWords' content is CC BY-SA 4.0, which is
  one-way compatible with GPLv3. Attribution is in `assets/en.dict.NOTICE` and on the keyboard's
  settings screen.
- **Capitalisation:** many apps (including Compose text fields by default) don't set
  `TYPE_TEXT_FLAG_CAP_SENTENCES`, so following only the field gave no capitals in most apps.

**Privacy:** the keyboard still has no network permission. Learned words are stored only in the
keyboard's own preferences, and nothing is learned in incognito fields.

## 2026-11-19: Messages isn't a default-SMS-app candidate yet (Accepted)

**Decision:** Messages v0.1 doesn't declare the components that would let it become the default
SMS app. It reads every conversation, sends SMS, and keeps a private log of what it sent.

**Reason:**

- **MMS:** a default SMS app must receive MMS, which needs WAP-push PDU download and decoding
  with no public API. Becoming default without that would silently lose picture messages and
  group chats.
- **Hidden sent messages:** Android records SMS sent by a non-default app in the shared database,
  but hides those rows from the app that sent them. Testing showed this. The private sent log
  lets them appear in Tessera's own conversation view; every other messaging app sees them from
  the database.

**Next:** MMS receiving (PDU parsing, download through `SmsManager.downloadMultimediaMessage`)
before offering the default role.
## 2026-11-19: Phone is opt-in for in-call handling (Accepted)

**Decision:** Phone works fully as an ordinary app: recents, favourites, contacts and the keypad,
with calls placed through Telecom and shown by the current phone app. Its own in-call screen
(`InCallService`) is used only after the user explicitly makes it the default phone app from
the banner.

**Reason:** A broken default phone app can stop you from answering calls. Making the in-call
part opt-in means installing Tessera Phone can't break calling. Testing on the emulator found
two crashes, which are now fixed:

- A call-style notification posted outside a foreground service.
- The wrong bind permission, so the default-phone role was refused.

Real-device and SIM testing is still needed before recommending it as the default.

## 2026-11-19: Settings in every app; app icon styles; shared theme (Accepted)

**Request:** every app gets a settings page with an App icon setting (Dark, Light and Custom,
where Custom takes any foreground and background colour) and an option to give every Tessera
app the same theme.

**Decisions:**

- **`core:settings`** holds the shared settings screen (`TesseraSettingsActivity`),
  appearance settings and theme sync. It's the second shared module, because every app needs it.
  Apps call `setTesseraContent { }` instead of `enableEdgeToEdge()` + `setContent { TesseraTheme {} }`.
- **Theme:** System, Light or Dark, wallpaper colours on or off, and pure black for OLED. Light
  and dark go through `UiModeManager.setApplicationNightMode` (API 31+), so the window
  background, splash screen and system bars follow, not only the Compose content.
- **App icon styles:** Colour (the existing icon), Light, Dark and Custom. Colour, Light and
  Dark are built-in icons switched with `activity-alias` launcher entries.
  - The switch waits until the app goes to the background, because Android closes a task
    whose launch component gets disabled.
  - `scripts/launcher-icons.sh <app> <RRGGBB>` generates all three variants from one glyph and
    one colour.
- **Custom icon colours are a pinned home-screen shortcut, not the app-drawer icon.** Android
  has no API for an app to recolour its own launcher icon. The only built-in mechanism is
  switching between icons the APK already contains, so arbitrary colours can't be one of them.
  - The custom icon is drawn at full adaptive-icon size with
    `Icon.createWithAdaptiveBitmap`.
  - It is pinned with `ShortcutManager.requestPinShortcut`, and recolouring updates it in place.
  - The app drawer keeps the Colour icon.
  - Launchers, including AOSP Launcher3, may show a small app badge on the shortcut.
  - The screen says all of this. If the user prefers a fixed palette of built-in coloured
    icons instead, that's a small change.
- **Same theme in all Tessera apps:** each app exports a `SharedSettingsProvider` (`call()`
  only), found through an intent filter, so package visibility works through `<queries>`.
  - **Who can call:** a caller is served only if `PackageManager.checkSignatures` matches.
    A signature-level custom permission was rejected: two apps that define the same
    permission with different signers can't both be installed, which would break mixing
    GitHub and F-Droid builds.
  - **Conflicts:** the newest change wins (by timestamp). The sync switch is shared too, so
    turning it on or off in one app does the same everywhere.
  - **New apps:** an app installed later adopts the shared theme on first launch.
  - **Storage:** settings live in device-protected storage, so Clock's ringing screen can
    read them before the first unlock.
- Each app's settings activity handles `APPLICATION_PREFERENCES`, so system Settings > Apps
  links to it.

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
