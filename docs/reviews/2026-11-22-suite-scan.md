# Tessera suite scan: 22 November 2026

This covers performance, security and features across the 12 apps in scope: Calculator, Clock,
Files, Contacts, Notes, Gallery, Calendar, Music, Phone, Messages, Weather and Sports.
Keyboard, Launcher and Camera were dropped on 2026-11-19 and have no code, so they're left out.

## Method

- **Search:** searched every app for known problem patterns: blocking I/O on the main thread,
  content observers, lazy lists without keys, image decoding without a cache or size, ticking
  loops, exported components, `PendingIntent` mutability, cleartext URLs, intent redirection,
  SQL built from strings, WebViews and logging.
- **Reading:** read the data-loading path of each app.
- **Emulator:** checked the fixes on the emulator (API 37). Each app was launched, sent to the
  background and resumed, with no crashes. Gallery was also checked with photos added while it was
  open.
- **Not done:** no profiling on a real device, and no macrobenchmarks. The numbers below are from
  reasoning, not measurement.

## Performance fixes applied

Each is in its own PR (#66 to #71) and is released once CI passes.

| App | Problem | Fix |
|-----|---------|-----|
| Calendar | Launching loaded everything twice (`onCreate`, then `onResume`). | Loads once. |
| Calendar | Every refresh ran an extra calendars query to check that a writable calendar exists. | Checks only until one exists. |
| Calendar | The provider observer stayed registered in the background and reloaded for each row a sync wrote. | Registered only while visible (onResume reloads anyway). Bursts coalesce into one reload 500 ms after the last change. |
| Gallery | The MediaStore observer reloaded the whole library on *any* file change on the device (downloads, other apps), even in the background, with no coalescing. | Visible-only, coalesced to 500 ms. |
| Gallery | Library and trash queries ran one after the other. | They run in parallel. |
| Gallery | (Found while testing.) New photos arriving while you're at the top of the grid landed just above the screen, because the lazy grid stays anchored to the old first item. | The grid stays at the top (`requestScrollToItem`). |
| Contacts | Photos were decoded again every time a row scrolled into view. | 8 MB LRU of decoded avatars, cleared when contacts change (a replaced photo keeps the same URI). |
| Contacts | Every change in a sync reloaded the whole list. | Coalesced to one reload 500 ms after the last change. |
| Music | Embedded artwork was decoded at full size (often 3000 px, about 36 MB) even for the 44 dp mini-player. | Decoded at the displayed size. |
| Music | Album thumbnails weren't cached, though lists repeat the same album many times. | 16 MB LRU. |
| Music | Search results were recomputed on every recomposition. | Remembered per query. |
| Files | Every keystroke in search started a new walk of the whole file system. | Starts 250 ms after typing pauses. |
| Phone | The call log and contacts were read one after the other on every resume, and grouped on the main thread. | Read in parallel, grouped off the main thread. |

Earlier today, separately: Weather 0.3.1 and Sports 0.2.4 fix the endless spinner after process
death. Both started loading only when there was no saved state.

### Performance items not fixed yet

- **Baseline profiles (suite-wide, biggest remaining win):**
  - Apps get only the Compose libraries' generic profiles. A baseline profile per app, generated
    by a `:baselineprofile` module with Macrobenchmark, typically cuts cold start and first-scroll
    jank by about 20–30%.
  - It needs a device or emulator in CI or a manual generation step. That fits F-Droid, because
    the profile is a checked-in text file.
- **StrictMode in debug builds (suite-wide):**
  - Turn on `detectDiskReads/Writes/Network` with `penaltyLog` in each debug `Application`, or
    once in `core:design`.
  - Main-thread I/O would then show up during development instead of in a scan like this.
- **Messages:**
  - Each MMS in a thread costs a separate `content://mms/<id>/addr` query, so MMS-heavy group
    chats open slowly. The provider has no bulk address URI. Caching senders per message id
    across reloads would avoid repeating the queries.
  - The observers also stay registered in the background. That's harmless, since they're
    already coalesced, but it's wasted work.
- **Notes:** the note list is rebuilt (filter, sort, labels) on the main thread for every saved
  edit. That's fine for hundreds of notes; move it to `Dispatchers.Default` if lists get large.
- **Music:** the play-position ticker updates the whole `PlayerUiState` twice a second. Compose
  skipping keeps this cheap today. Splitting position into its own `StateFlow` would stop the
  player screen recomposing everything else.
- **Clock:** the stopwatch lap list uses `itemsIndexed` without keys. A new lap shifts every row,
  so they all recompose. Key the laps by number.

## Security findings

Nothing critical. Every app uses HTTPS only, and no app uses a WebView or logs personal data.
Mutable `PendingIntent`s are used only for the explicit inline-reply action in Messages. Content
queries use bind arguments; the only string-built selections are numeric ids. The exported
components below are worth fixing.

| Severity | App | Finding | Suggested fix |
|----------|-----|---------|---------------|
| **Medium** | Calendar | `ReminderReceiver` is exported (it must receive `EVENT_REMINDER` from the provider), but it also handles the app's own `SNOOZED` action from intent extras. Any installed app can send it an explicit intent and post a high-priority notification with any title and location under Calendar's name, which could be used for phishing. | Move snooze handling into a separate receiver with `exported="false"` and keep only `EVENT_REMINDER` on the exported one. Small change. |
| Low | Calendar | Any app can trigger `EVENT_REMINDER` on the exported receiver. It only re-reads due alerts from the provider, so it can't be abused, but it costs a query. | Accept, or check that the intent data is a `content://com.android.calendar` URI. |
| Low | Clock | Any app can send `DISMISS_ALARM`/`SNOOZE_ALARM` to silence a ringing alarm. That's the public `AlarmClock` API, and AOSP DeskClock behaves the same. | Optionally show a confirmation unless `isVoiceInteraction()`, as DeskClock does for some actions. |
| Low | Files | The FileProvider exposes `external-path .` and `root-path storage`. That's needed to share any file, and it's safe because the provider isn't exported and grants are per URI. | None; note it in the spec. |
| Info | Notes | `allowBackup="false"`, while the other apps rely on `dataExtractionRules`. | Decide whether notes should be in device-to-device transfer; the user would probably expect them to be. |

## Feature suggestions

[`docs/feature-gaps.md`](../feature-gaps.md) (2026-11-19, updated 2026-11-20) already ranks most
first-party gaps as P1, P2 or P3. Suggestions marked **New** below aren't in it. The rest are
already tracked there and repeated here with a recommended order.

### Recommended next, in order

1. **Calendar: week and day views, and editing one occurrence of a repeating event** (P1 in
   feature-gaps). Today a change to a repeating event applies to the whole series, which
   surprises people.
2. **Music: restore the queue after the app is closed, and playlists** (P1).
3. **Files: trash** (P1). Deleting is permanent today.
4. **Widgets: Clock, then Notes, Music, Weather and Sports** (P1, suite-wide).
   - Weather and Sports aren't in feature-gaps yet.
   - Reuse the Glance keep rules in `apps/calendar/proguard-rules.pro`: without them, R8 broke the
     Calendar widgets in release builds.
5. **Weather: severe-weather warnings.** **New.** BOM publishes warnings per location
   (`/locations/<geohash>/warnings`), and Weather already uses BOM for Australian places. This is
   the most valuable addition for Australian users.

### New suggestions by app

**Phone:** these build on the long-press sheet added today.

- **Call details screen:** every call with that number, with direction, time and duration.
- **T9 name search on the keypad:** 2-6-6 matches "Ann". `Dialpad` knows the letters, but
  `NumberMatch` compares digits only.
- **Block number in the sheet:** `BlockedNumberContract`, writable as the default dialler.
  Messages already blocks numbers.
- **Delete from history in the sheet:** needs `WRITE_CALL_LOG` (feature-gaps lists delete as P2).

**Weather:** feature-gaps doesn't cover Weather yet.

- Severe-weather warnings, as above.
- **Rain radar:** BOM's radar images are public, but it's a larger piece of work.
- **Air quality and pollen:** Open-Meteo's air-quality API, keyless.
- **Notifications:** an optional morning forecast, and "rain starting soon" from the hourly
  precipitation.
- **Widget:** current conditions plus the next few hours.

**Sports:** feature-gaps doesn't cover Sports yet.

- **A WNBL source.** ESPN doesn't carry it.
- **Team pages:** schedule, results and roster.
- **Spoiler-free mode:** hide scores until tapped.
- **Widget:** a followed team's next or live game.

**Suite-wide:**

- **Backup and restore** of each app's own data (Notes, Clock alarms, Weather places, Sports
  follows) to a file through SAF, since there's no cloud. Notes is `allowBackup="false"`, so
  today a new phone loses every note unless it's exported.
- **StrictMode in debug builds** (see Performance).

**Smaller, by app:**

- **Gallery:** move or copy to an album and create albums (P2) are the most-missed. Rotating in
  the viewer (P2) is small.
- **Notes:** checklist drag-to-reorder (P2) pairs well with today's tighter rows.
- **Clock:** skip the next occurrence (P2), and the "Upcoming alarm" notification (P2).

### Out-of-date entries in feature-gaps.md

The code has caught up with these entries. Strike them through on the next edit.

- Performance: "Messages reloads up to three times per incoming message". Debounced (`RELOAD_DELAY_MS`).
- Performance: "Messages looks up each conversation's contact name separately". It now uses one
  query, refreshed when contacts change.
- Performance: "Theme sync wakes every Tessera app on each cold start". It now pulls only after
  an app update (`needsPull(versionCode)`).
- Performance: debounced observers for Gallery, Calendar and Contacts, done today (#66 to #68).
- Music: "Album art in the song list", done (`AlbumArt` in `SongList`).
- Phone: Recents now shows durations and a long-press sheet (0.3.0). Delete, search and the
  missed-calls filter are still open.

## Follow-up (same day)

The maintainer asked for everything above to be implemented. Released:

| Release | What |
|---------|------|
| Calendar 0.5.4 | Security: snooze moved to a non-exported receiver; the exported one handles only `EVENT_REMINDER` |
| Phone 0.3.2 | Contacts tab like the Contacts app (requested during the follow-up) |
| Phone 0.4.0 | Call history, T9 name search, block/unblock and delete from the long-press sheet |
| Clock 0.3.0 | Skip next alarm, "Upcoming alarm" notification, widget; stopwatch laps keyed |
| Notes 0.2.0 | Checklist drag-to-reorder, widget; list computed off the main thread |
| Files 0.3.0 | Trash with Undo and 30-day expiry |
| Music 0.2.0 | Queue restore (and system resumption), playlists, Now playing widget; progress split from the player state |
| Calendar 0.6.0 | Week and day views; edit or delete this / this and following / all occurrences |
| Weather 0.4.0 | BOM warnings, air quality and pollen, morning/rain/warning notifications, widget |
| Sports 0.3.0 | Team pages, spoiler-free mode, My team widget |
| Gallery 0.3.0 | Move/copy to album, new albums, rotate |
| Messages 0.3.4 | MMS sender cache; observers only while visible. Debug builds of every app log StrictMode |
| All apps (patch releases) | Backup and restore per app and for the whole suite (docs/backup.md) |

Not done:

- **Weather radar:** no keyless radar source with a basemap; BOM's loops are only on its web
  pages (docs/weather.md).
- **WNBL in Sports:** ESPN doesn't carry it; another source is needed.
- **Baseline profiles:** need a decision on how they're generated (an emulator in CI, or a manual
  step whose output is committed) before adding a `:baselineprofile` module per app.
- **Clock confirmation for `DISMISS_ALARM` from other apps** (Low): left as AOSP behaves.
- **Calendar `EVENT_REMINDER` data check** (Low): the receiver now only re-reads due alerts from
  the provider, so a spoofed broadcast costs one query and shows nothing new.
- **Notes `allowBackup="false"`:** left; the new file backups cover moving phones.

Found while doing it: on the API 37 emulator, inserting an exception row for one occurrence of a
repeating event on a local calendar made the provider stop showing the series' other
occurrences, and updating a repeating event without its full time fields didn't re-expand it.
Calendar 0.6 avoids both (EXDATE plus a one-off event; full-field updates).
