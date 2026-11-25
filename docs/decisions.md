# Decision log

Newest first. Each entry records the date, the decision, the reason and its status. `Proposed`
entries wait for the maintainer's sign-off. Agreed changes to the founding decisions are recorded
here.

## 2026-11-26: Feeds, an RSS reader that syncs with yarr (Accepted)

The maintainer reads feeds in yarr on their NAS and asked for a Tessera app that works with it or
imports from it. They picked the name Feeds and "sync plus local".

- **yarr's own JSON API, not its Fever API.** Fever works only when yarr runs with `-auth`, and it
  can't add or remove feeds or folders. The JSON API works with or without a login (a cookie from
  `/login`) and covers everything the web page does, including readability ("Full article").
  Fever (and Google Reader for Miniflux and FreshRSS) can come later behind the same sync code.
- **jsoup** (MIT, Maven Central) parses feeds and article HTML. Feeds in the wild are often broken
  XML, which strict parsers reject, and articles are shown natively in Compose rather than in a
  WebView (no View-based UI), so the HTML has to be parsed into blocks anyway.
- **Article content is stored apart** from the article list (`files/articles/`), so marking one
  read doesn't rewrite megabytes of JSON.
- **Refresh defaults to every hour** (the DAV apps default to 4) and uses the shared sync settings
  from `core:dav`.
- **Podcasts** (a separate app, built alongside) has its own feed parser for now; one shared
  `core:feed` module can follow once both are merged.
## 2026-11-25: Podcasts app (Accepted)

The maintainer asked for a podcast app to join the suite, kept simple in the manner of Apple
Podcasts and the old Google Podcasts (they aren't a heavy podcast listener).

- **Finding shows: Apple's iTunes Search API and fyyd**, searched together, plus "Add by URL"
  (a feed or an Apple Podcasts link) and OPML import. Both directories are keyless; only the
  search words are sent. Apple's is the most complete directory (AntennaPod uses it too); fyyd is
  an open one. Searching a proprietary service makes the app NonFreeNet on F-Droid, which the
  maintainer accepted. Podcast Index was set aside because it needs an API key in the app.
- **No sync server in 0.1** (gPodder / Nextcloud gPodder Sync can come later).
- **Streaming, manual downloads and automatic downloads of new episodes on Wi-Fi**, into the
  app's own external folder (no storage permission). Downloads are WorkManager foreground work
  (`dataSync`).
- **Cleartext HTTP is allowed** in this app only: many feeds and audio files are still plain
  HTTP.
- **Our own feed parser** (SAX through `javax.xml`, streaming), no new dependency. Feeds, built
  at the same time, has its own (XmlPullParser); a shared `core:` module for both can be
  considered later.
- Icon colour 7B3FB5 (violet, between Clock's and Gallery's hues).

## 2026-11-25: Files gets a PDF viewer, on androidx.pdf 1.0.0-beta01 (Accepted)

Files opened PDFs only through another app, and with none installed it said so. The maintainer
chose a viewer inside Files over a separate Documents app (as Files by Google does): one less
icon, and other apps' PDFs (mail, browser, Messages) open in it too. Scanning stays out: the
maintainer uses Papra for that.

- **Library:** `androidx.pdf` (`pdf-compose` for the `PdfViewer` composable, `pdf-document-service`
  for `SandboxedPdfLoader`). It parses PDFs in an `isolatedProcess` service, so a malicious file
  can't reach the app's data, and backports rendering to minSdk 28. Apache 2.0, no Play
  Services (the OCR add-on, `pdf-ocr-play-services`, is not used).
- **Pre-release exception:** it has no stable release; 1.0.0-beta01 is the first beta and the
  Compose API is `@ExperimentalPdfApi`. Move to stable when it ships.
- **Views inside:** `PdfViewer` wraps the library's own `PdfView` (a View) in `AndroidView`. Our
  code stays Compose; this is the same as using any Compose component built on a View, and
  writing a renderer, text selection and zoom ourselves isn't worth it.

## 2026-11-23: Weather fills BOM's gaps from Open-Meteo; no pollen outside Europe (Accepted)

The maintainer noticed no visibility, pressure, air quality or pollen for Melbourne.

- **Visibility and pressure:** BOM's API has neither. For BOM places, the Open-Meteo forecast
  already fetched for the hours past BOM's three days now also supplies visibility and pressure
  for BOM's own hours, and pressure now. BOM's values stay the source for everything it has.
- **Air quality** was a bug, not a gap: it (and warnings) loaded only after a network fetch, so a
  fresh cached forecast never showed them. They now load on every open.
- **Pollen:** Open-Meteo's pollen (CAMS Europe) covers Europe only. Australia's real pollen counts
  (AusPollen / Melbourne Pollen, University of Melbourne and AirHealth) have a JSON feed behind
  their sites, but their terms forbid reproducing the forecasts without written permission.
  Google's Pollen API needs a key with billing, which Tessera can't ship. The maintainer chose to
  skip pollen for Australia for now; asking AusPollen for permission is the way to add it later.

## 2026-11-23: Phone stays "experimental" until call waiting is tested on a real phone (Accepted)

The review for lifting the label found one gap a daily dialler can't have: with a second call,
the screen showed the held call, so hang-up could end the wrong one, and there was no swap.
Phone 0.7 adds call waiting (Hold & answer, End & answer, Swap). The emulator's modem can't hold a
call or take a second one, so the label goes once the maintainer has tested, on their Pixel: call
waiting both ways and swapping, Bluetooth audio, and the screen turning off at the ear.

## 2026-11-23: Sports' live score as an Apple Sports-style card, not a Live Update (Accepted)

The maintainer asked for the live score to look like Apple Sports' lock-screen card: logos and big
scores at the sides, the clock between, names under the logos (right-aligned for the home team),
the situation and last play below. Android promotes a notification to a Live Update (the status
bar chip, the pinned place on the lock screen) only with a standard layout, so a custom one can't
be both. The maintainer chose the layout.

- The notification is a RemoteViews layout (collapsed and expanded) in the system's frame
  (`DecoratedCustomViewStyle`), like Clock's widget: notifications can't be Compose. Text uses the
  system's notification styles, so light and dark follow the shade.
- `POST_PROMOTED_NOTIFICATIONS` is dropped; the setting is now "Live scores".
- ESPN's last play and football's down and distance are read for it.

## 2026-11-23: Calendar, Contacts and Tasks sync with your own server, without DAVx5 (Accepted)

The maintainer asked for Tessera to replace DAVx5, with DAVx5's sync settings (they use "every 4
hours, and immediately on local changes" for contacts, calendars and tasks), calendar colours with
a picker and hex codes, and both of DAVx5's contact group methods. Spec: docs/sync.md.

- **`core:dav`** holds what the three apps share: the WebDAV client, discovery, iCalendar and
  vCard text, the sign-in form and setup page, the encrypted account and the Sync settings.
- **Calendar and Contacts sync into Android's own databases** through an account and sync adapter
  per app, as DAVx5 does, so every app sees the data and edits anywhere are picked up. Each app
  signs in on its own: sharing one sign-in would mean passing a password between apps.
- **Intervals as DAVx5's** (manual, 15 min to once a day; default 4 hours), immediately on local
  changes (on by default) and Wi-Fi only, scheduled with WorkManager.

## 2026-11-23: Recorder app (Accepted)

The maintainer asked for a voice recorder in the spirit of Pixel Recorder, as the next app after
Tasks (an authenticator was considered and deferred: the maintainer keeps 2FA in Bitwarden).

- **Recordings are shared files** in `Recordings/Tessera` (MediaStore), not in the app's private
  storage, so Files, other players and backup or sync tools see them. No storage permission is
  needed for the app's own files. The cost: after a reinstall Android no longer counts the old
  files as the app's, so v0.1 doesn't list them (they stay in the folder).
- **AAC in .m4a** through MediaRecorder: every phone and computer plays it, and the media server
  closes the file properly even if the app is killed.
- **A foreground service of type microphone**, started only from the app's own screens (Android
  14+ refuses one started from the background); the Quick Settings tile opens the app to record.
- **No network permission.** Transcription is left out of v0.1: on-device speech models are large.
- During a call Android silences other apps' microphones; the recording carries on (that stretch
  is silent) rather than pausing itself, which could leave it paused for good.

## 2026-11-22: Tasks app with its own CalDAV sync (Accepted)

The maintainer asked for a Tasks app modelled on Google Tasks, for simple daily tasks and
repeating medication reminders, used mostly from a home-screen widget. It syncs VTODOs over CalDAV
itself ("option C"), not through DAVx5 and the OpenTasks provider, so it needs no other app and
works the same with or without DAVx5. A list can also stay on the phone only.

- **Network:** `INTERNET`, only to the server the person adds. Plain `http://` only for private
  and Tailscale addresses. `ServerAddress` (that check) moved from Music into `core:settings`, and
  `SecretBox` (the Keystore-encrypted password) with it, now that two apps need them.
- **OkHttp** for WebDAV: `HttpURLConnection` can't send PROPFIND, REPORT or MKCALENDAR. It's
  Apache-2.0 and on F-Droid.
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`, asked in setup) so medication reminders come on time.
  Without it Android may deliver a reminder up to an hour late, and the list says so with a
  button to allow it.
- Tested against Radicale, which OpenCloud uses for CalDAV.

## 2026-11-22: Android Auto for Music and Messages only (Accepted)

The maintainer asked for Android Auto where it makes sense. Android Auto accepts third-party
media, messaging (through notifications), navigation, points of interest and a few other
categories; calendars, tasks, notes and the rest aren't allowed. Phone calls already reach the
car through Telecom whichever dialer is default. So: Music (a MediaLibraryService) and Messages
(its existing MessagingStyle notifications). Neither needs a Google library.

## 2026-11-22: Music looks up lyrics on LRCLIB, only if turned on (Accepted)

The maintainer asked for lyrics like Spotify's. Lyrics come from their server first; most
libraries have none, so Music can also ask LRCLIB, an open, keyless lyrics database. It's off by
default and sends only the song's title, artist, album and length. Shuffle now reorders the queue
itself (Media3 shuffle orders are invisible to the queue). The Reorderable library (Apache 2.0)
is used for dragging songs in the queue.

## 2026-11-22: Music plays from Subsonic servers (Accepted)

The maintainer keeps their music in Navidrome (HTTPS, reached over Tailscale), so Music 0.4 adds
a Subsonic-compatible source, chosen in first-run setup or Settings. Music now has `INTERNET`,
used only for the server the person adds. Plain http is allowed by the network security config
but refused by the app outside private networks and Tailscale; user-installed CAs are trusted
for self-hosted servers with a private CA. An exported, read-only `ArtProvider` serves cover art
to media controllers. Jellyfin (not Subsonic) is out of scope. See docs/music.md.

## 2026-11-22: First-run setup for every app but Calculator (Accepted)

The maintainer agreed: a shared setup (welcome, one page per permission, restore) on fresh
installs only; apps that can't work without a permission block on a "needs access" screen;
"Restore all" from any app's setup covers the others; optional features stay in Settings. See
docs/setup.md.

## 2026-11-22: Phone labels spam from your own reports; no community list yet (Accepted)

The maintainer chose: a community spam list if a popular, trusted one exists; label only (never
silence or reject); reports stay on the phone. No list met that bar (Yet Another Call Blocker's
database is a proprietary app's service; no downloadable Australian list), so Phone 0.5 labels
numbers the person reported, and caller location comes from libphonenumber's offline data. A
list can be added later behind the same label.

## 2026-11-22: Backups are files, per app and for the whole suite (Accepted)

The maintainer asked for backup and restore and left the design to Claude. Each app backs up its
own data to a file the person picks (SAF), and "Back up all Tessera apps" puts every app's backup
in one file, collected through the existing signature-checked settings provider. No cloud and no
new permissions. Android's own providers (contacts, calendar, messages, call log) aren't
included: they belong to the system and have their own export paths. See docs/backup.md.

## 2026-11-22: Clock's widget is a RemoteViews layout, not Glance (Accepted)

A clock widget must show the current minute. Glance has no `TextClock`, and redrawing a Glance
widget every minute would wake the app 1,440 times a day. The Clock widget is therefore a plain
`AppWidgetProvider` with an XML RemoteViews layout (`widget_clock.xml`). It's the only XML layout
in the suite and is allowed as a resource. Other widgets keep using Glance.

## 2026-11-21: Sports joins the suite, with scores from ESPN (Accepted)

**Decision:** Add a Sports app (the maintainer asked for it, after Apple Sports). Scores, fixtures
and standings come from ESPN's public JSON (`site.api.espn.com`), which the maintainer chose over
TheSportsDB (live scores need a paid key) and league-specific APIs (a key each). Leagues: NBA, WNBA,
NBL, AFL, NRL, MLB, NFL. Live Updates for followed teams' games are in v0.1.

**Reason:** ESPN is keyless and covers every requested league but one, with live scores. It's
undocumented and unofficial, so the app reads it behind a `ScoreSource` interface, and F-Droid
would mark it NonFreeNet. The WNBL isn't on ESPN; it needs another source later.

**Status:** Accepted (maintainer, 2026-11-21).

## 2026-11-21: Weather joins the suite; the Bureau for Australia, Open-Meteo elsewhere (Accepted)

**Decision:** Add a Weather app (the maintainer asked for it). It is the first Tessera app that
uses the network, which docs/weather.md justifies. Places in Australia get the Bureau of
Meteorology's issued forecasts and observations, through the API its own app uses; everywhere
else, and whenever BOM fails, forecasts come from [Open-Meteo](https://open-meteo.com) (open
source, keyless, CC BY 4.0, credited). Place search uses Open-Meteo. Only rounded coordinates or
a 6-character geohash are sent. Location is optional (`ACCESS_COARSE_LOCATION` through the
platform `LocationManager`, not Play Services).

**Reason:** The maintainer trusts the Bureau's forecasts. Weather 0.1 used Open-Meteo alone,
and its notes said Open-Meteo includes BOM's ACCESS-G model: that was wrong. Checked on
2026-11-21, Open-Meteo's `bom_access_global` had returned no data for at least 30 days, so its
Australian forecasts come from global models. BOM's app API is undocumented and Australia-only,
hence the Open-Meteo fallback.

**Amended 2026-11-21 (Weather 0.3):** for the day details, an Australian place also fetches
Open-Meteo, whose hours fill only what BOM's three-day hourly forecast doesn't cover (BOM's own
hours always win; the screen credits Open-Meteo for them). The maintainer asked for every day to
open an hour-by-hour view, which BOM alone can only give for three days.

**Status:** Accepted (maintainer, 2026-11-21).

## 2026-11-19: Messages is a full default SMS app (Accepted)

**Decision:** Messages 0.2 can be the default SMS app. It receives SMS and MMS, notifies, records
what it sends, and deletes, marks read and blocks. The maintainer asked for delete and mark as
read, and Android only allows those for the default app.

**How MMS works without hidden APIs:** Android moves the bytes (`SmsManager` downloads and sends
over the carrier's MMS connection). The app decodes and encodes the PDUs itself
(`engine/Pdu.kt`, unit tested) and writes the messages through the public `Telephony` provider.

**Risk:** MMS behaviour varies by carrier and can't be tested on the emulator. Until it's been
tested on a real phone, it stays opt-in through the "Set as default" banner, and the previous
messaging app can be made default again at any time.

**Pin and archive** are kept in the app's own preferences, because the message database has no
field for them.

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

## 2026-11-19: Messages isn't a default-SMS-app candidate yet (Superseded the same day)

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
