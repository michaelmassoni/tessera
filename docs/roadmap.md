# Roadmap to 1.0

Written 2026-11-26 from each app's spec and a check of the code; updated 2026-11-27. It replaces
[feature-gaps.md](feature-gaps.md) (2026-11-19/20), which is kept as a record. Strike items
through when they ship, with the version.

## Next session: still to do

Features the maintainer asked for on 2026-11-27 that haven't been built yet, in this order:

1. **Feeds:** swipe between articles (left and right in the reader, following the list's order
   and filter).
2. **Recorder:** trim a recording (pick start and end on the waveform; save as a copy or replace).
3. **Notes:** images in notes (attach from the photo picker; stored in app storage; shown in the
   note and in the grid; included in backups).
4. **Gallery:**
   - video trimming (Media3 Transformer, saved as a new video);
   - motion photos: press and hold a photo to play its motion part (the embedded MP4 in Pixel and
     Samsung motion photos, found through the XMP `MicroVideoOffset` / `GCamera` data);
   - tap, hold and drag across the grid to select many photos at once.

Each one ships as its own PR and release, as usual (spec section, version bump, changelog, goldens
checked in both themes, `check`, emulator test).

**Waiting on the maintainer's phone:** MMS receiving (sending works); Phone call waiting,
Bluetooth and proximity; delivery reports and swipe to answer on a real carrier.

**Waiting on GitHub Support:** purging the old PR refs #90 to #167 after the history rewrite of
2026-11-27.

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
- **F-Droid:** everything is ready (docs/fdroid.md). Submission waits until about 2027-02-19,
  because F-Droid asks for three months of active development (the first commit was 2026-11-19).
  Until then, releases go out through GitHub and Obtainium only.

**Decided 2026-11-27:** the remaining P3s not listed under "Next session" are dropped: unit
converter, timer starter widget, Tasks subtasks, Android Auto showing the chosen icon, Podcasts
widget, Miniflux and FreshRSS in Feeds, notes from the lock screen, ringtone per contact, Gallery
slideshow and hidden albums, Messages voice messages, Phone speed dial. Baseline profiles are on
hold.

**Priority:** P1 is expected by nearly everyone, or broken without it. P2 is common and missed by
many. P3 is nice to have.

## Suite-wide

| Item | State | Notes |
|------|-------|-------|
| Real-phone pass | Music and Podcasts in Android Auto: ✅ 2026-11-26. Feeds against yarr: ✅ 2026-11-26. Music Cast: ✅. Phone calls: ✅ (experimental label dropped 2026-11-27). MMS sending: ✅ 2026-11-27. Still to do: MMS receiving; Phone call waiting, Bluetooth and proximity. | |
| Accessibility audit | Partly | TalkBack quick actions done (#147). A full pass (TalkBack order, 200% font, touch targets, app by app) is still to do. |
| material3 | ✅ 1.5.0 stable (2026-11-26) | Was `1.5.0-rc01`. |
| Compose 1.13 (BOM 2026.11.01) | ✅ 2026-11-26 | compileSdk 37.1. F-Droid recipes need `platforms;android-37.1`. |
| `androidx.pdf` `1.0.0-beta01` (Files) | Pre-release | Stable when it ships, or accept the beta for Files 1.0 in `decisions.md`. |
| Final icons | Commissioned | `scripts/launcher-icons.sh` turns each glyph into the Colour, Light, Dark and monochrome icons. |
| Baseline profiles | On hold | Decided 2026-11-27. |
| F-Droid submission | Ready, waiting | About 2027-02-19 (see above). |
| ~~StrictMode in debug builds~~ | ✅ | `core:settings`. |
| ~~Launcher shortcuts~~ | ✅ | Calendar, Clock (new alarm in 0.10, timer, stopwatch), Contacts, Messages, Notes, Recorder, Tasks. |
| ~~Quick Settings tiles~~ | ✅ | Clock (stopwatch, timer), Notes (new note), Recorder. |

## Apps

Current versions on 2026-11-27: Calculator 0.4.2, Calendar 0.11.4, Clock 0.10.0, Contacts 0.7.0,
Feeds 0.3.7, Files 0.8.3, Gallery 0.6.5, Messages 0.11.0, Music 0.14.4, Notes 0.6.3, Phone 0.11.0,
Podcasts 0.5.4, Recorder 0.2.5, Sports 0.6.3, Tasks 0.6.2, Weather 0.8.5.

Every P1 and P2 on this roadmap has shipped (except the real-phone checks above).

| App | Shipped since 2026-11-26 | Still to do |
|-----|--------------------------|-------------|
| Calculator | ~~cursor editing, paste~~ (0.4) | |
| Clock | ~~gradual alarm volume~~ (0.7), ~~New alarm shortcut~~ (0.10), ~~tiles~~ | |
| Weather | Complete for its spec. Radar stays out (no free source). | |
| Sports | Complete for its spec. WNBL has no source. | |
| Recorder | | Trim a recording |
| Tasks | Complete for daily and medication use. | |
| Music | ~~Genres~~ (0.13; Folders removed in 0.13.2 at the maintainer's request), ~~offline downloads~~ (0.14), liked hearts, swipe the album art to skip | |
| Calendar | ~~search events~~ (0.10) | |
| Files | ~~background copies and moves, zip, bookmarks~~ (0.7), archives open in Files (0.8) | |
| Podcasts | ~~new-episode notifications~~ (0.5) | |
| Feeds | Works against yarr. | Swipe between articles |
| Notes | ~~links, colours, reminders~~ (0.5) | Images |
| Contacts | ~~photos, merge duplicates, several addresses~~ (0.5), ~~share as a QR code~~ (0.7) | |
| Gallery | ~~search~~ (0.6) | Video trimming, motion photos, drag to select |
| Messages | ~~search inside messages, contact photos~~ (0.7), ~~links~~ (0.9), ~~retry a failed SMS, delivery reports, select part of a message~~ (0.11) | MMS receiving on a real phone. RCS is out of scope (no open API). |
| Phone | ~~recents search, reply with message, dual SIM~~ (0.9), ~~copy and paste~~ (0.10), ~~swipe to answer~~ (0.11) | Real-phone: call waiting, Bluetooth, proximity |

## Order

1. **Next session list** above: Feeds, Recorder, Notes, Gallery.
2. **Real-phone checks:** MMS receiving; Phone call waiting, Bluetooth and proximity.
3. **Suite polish:** full accessibility audit.
4. **1.0 gate:** `androidx.pdf` stable (or the exception), final icons, F-Droid submission (about
   2027-02-19). Ship in waves: Calculator, Clock, Weather, Sports, Recorder and Tasks first.
