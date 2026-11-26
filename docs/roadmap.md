# Roadmap to 1.0

Written 2026-11-26 from each app's spec and a check of the code. It replaces
[feature-gaps.md](feature-gaps.md) (2026-11-19/20), which is kept as a record. Strike items
through when they ship, with the version.

## What 1.0 means

An app reaches 1.0 when:

1. **Parity:** someone switching from the built-in app (Pixel and Samsung, plus popular
   open-source ones) doesn't miss anything they use every week.
2. **Tested on a real phone:** the maintainer's Pixel, not only the emulator, for anything the
   emulator can't do.
3. **Accessible:** TalkBack order, font scaling to 200% and 48 dp touch targets checked.
4. **No pre-release dependencies** (or an exception accepted in `decisions.md`).
5. **Final icon:** the commissioned human-drawn icon.
6. **No open P1s.**

**Decided 2026-11-26:**

- **1.0 is English-only.** All strings are in `strings.xml` already, so translations can follow.
- **F-Droid:** submit if the apps meet its criteria. They should: everything is open source and
  built from source, with no Play Services or trackers. F-Droid builds and hosts apps in its main
  repository for free. Apps that use non-free network services are listed with the `NonFreeNet`
  anti-feature rather than refused (Sports: ESPN; Podcasts: Apple's search). The work is a build
  recipe per app in `fdroiddata`, the review, and F-Droid's build servers supporting AGP 9 and
  Gradle 9.

**Priority:** P1 is expected by nearly everyone, or broken without it. P2 is common and missed by
many. P3 is nice to have.

## Suite-wide

| Item | State | Notes |
|------|-------|-------|
| Real-phone pass | Music and Podcasts in Android Auto: ✅ 2026-11-26. Feeds against yarr: ✅ 2026-11-26. Music Cast: ✅. Still to do: Phone call waiting, Bluetooth and proximity; Messages MMS. | Blocks Phone's "experimental" label. |
| Accessibility audit | Not done | TalkBack, 200% font, touch targets, app by app. Scriptable on the emulator. |
| material3 | ✅ 1.5.0 stable (2026-11-26) | Was `1.5.0-rc01`. |
| Compose 1.13 (BOM 2026.11.01) | ✅ 2026-11-26 | compileSdk 37.1; password fields, Messages recipients and Notes checklist rows moved to the state-based text fields. F-Droid recipes need `platforms;android-37.1`. |
| `androidx.pdf` `1.0.0-beta01` (Files) | Pre-release | Stable when it ships, or accept the beta for Files 1.0 in `decisions.md`. |
| Final icons | Commissioned | `scripts/launcher-icons.sh` turns each glyph into the Colour, Light, Dark and monochrome icons. |
| Baseline profiles | None | Biggest remaining performance win (about 20–30% faster cold start). A `:baselineprofile` module with Macrobenchmark; the profile is a checked-in text file, so it suits F-Droid. |
| F-Droid submission | Not started | See above. |
| StrictMode in debug builds | Not done | Catches main-thread I/O during development. |
| Launcher shortcuts | Notes, Recorder | P3. Clock (new alarm, timer, stopwatch), Calendar (new event), Contacts (new contact), Tasks (new task). |
| Quick Settings tiles | Recorder | P3. Clock (stopwatch, timer), Notes (new note). |
| Android Auto shows the chosen icon | Not done | P3. Auto shows the playback service's icon, not the launcher alias. Needs one service component per icon, switched with the alias. |

## Apps

### Nearly ready: polish and audits

**Calculator 0.3**

| Gap | Priority |
|-----|----------|
| Move the cursor to edit inside the expression | P2 |
| Paste a number | P2 |
| Unit converter (no currency: that needs the network) | P3 |

**Clock 0.6**

| Gap | Priority |
|-----|----------|
| Gradually increasing alarm volume | P2 |
| Timer starter widget (one-tap preset timers) | P3 |
| Shortcuts and tiles (see suite-wide) | P3 |

**Weather 0.8:** complete for its spec. Radar stays out (no free source). Needs only the suite
audits.

**Sports 0.6:** complete for its spec. WNBL has no source.

**Recorder 0.2:** complete for v0.1. P3: trim a recording.

**Tasks 0.5:** complete for daily and medication use (repeats, widget). P3: subtasks.

### Mid-way: a feature or two each

**Music 0.12**

| Gap | Priority | Notes |
|-----|----------|-------|
| Genres and Folders tabs | P2 | Gramophone and Auxio have them. |
| Sleep timer, equaliser | Won't do | Not wanted (2026-11-26). |
| Android Auto: chosen icon | P3 | See suite-wide. |

**Calendar 0.9**

| Gap | Priority | Notes |
|-----|----------|-------|
| Search events | P2 | |

Done since the gap list: week and day views, single-occurrence edits, CalDAV sync, event colours,
ICS, attendees, time zones.

**Files 0.6**

| Gap | Priority | Notes |
|-----|----------|-------|
| Copies and moves that keep running after leaving the app, with progress | P2 | Large copies stop today. |
| Zip: extract and compress | P2 | `java.util.zip`. |
| Bookmarked folders | P2 | |

Done since: trash, PDF viewer, storage summary.

**Podcasts 0.4**

| Gap | Priority |
|-----|----------|
| New-episode notifications | P2 |
| Widget (now playing / Up Next) | P3 |

**Feeds 0.3:** works against yarr (2026-11-26). P3: swipe between articles; Miniflux and
FreshRSS through the Fever or Google Reader API.

### Further out

**Notes 0.4**

| Gap | Priority |
|-----|----------|
| Tappable links, phone numbers and emails | P2 |
| Note colours | P2 |
| Reminders | P2 |
| Notes from the lock screen (`ROLE_NOTES`) | P3 |
| Images | P3 |

**Contacts 0.4**

| Gap | Priority |
|-----|----------|
| Set or change a contact's photo | P2 |
| Find and merge duplicates | P2 |
| More than one address in the editor | P2 |
| Ringtone per contact, share as a QR code | P3 |

Done since: groups and labels, CardDAV sync.

**Gallery 0.5**

| Gap | Priority |
|-----|----------|
| Search (name, folder, date; no on-device AI) | P2 |
| Slideshow, hidden albums | P3 |
| Video trimming, motion photos | P3 |

**Messages 0.6**

| Gap | Priority | Notes |
|-----|----------|-------|
| MMS on a real phone | P1 | Untested off the emulator. |
| Search inside message text | P2 | The list search covers names and the latest message. |
| Contact photos in the list | P2 | |
| Retry a failed SMS | P2 | MMS has retry. |
| Delivery reports, voice messages, precise copy | P3 | |

RCS is out of scope (no open API); the README should say so plainly.

**Phone 0.8 (experimental)**

| Gap | Priority | Notes |
|-----|----------|-------|
| Real-phone test: call waiting, Bluetooth, proximity | P1 | Then drop "experimental" (README lines ~53 and ~118, `docs/phone.md`). |
| Dual SIM: choose per call or per contact | P2 | `PhoneAccountHandle`. |
| Quick reply when declining | P2 | `RESPOND_VIA_MESSAGE`. |
| Recents: search, missed-calls filter | P2 | |
| Speed dial, swipe to answer | P3 | |

## Order

1. **Quick wins** (2026-11-26): ~~stale dependency PRs~~, ~~material3 stable~~, this roadmap, Calculator paste and cursor
   editing, Clock gradual volume, Calendar search.
2. **Real-phone pass:** Phone and Messages MMS. Then drop Phone's "experimental".
3. **P2 batches:** Notes (links, colours, reminders); Contacts (photo, merge, addresses); Files
   (background copies, zip, bookmarks); Podcasts notifications; Gallery search; Messages search
   and photos; Phone dual SIM, decline replies and recents; Music genres and folders.
4. **Suite polish:** accessibility audit, baseline profiles, shortcuts and tiles, StrictMode.
5. **1.0 gate:** `androidx.pdf` stable (or the exception), final icons, F-Droid submission. Ship in
   waves: Calculator, Clock, Weather, Sports, Recorder and Tasks first.
