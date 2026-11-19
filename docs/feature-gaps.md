# Feature gaps

A review of each app against what people expect from the phone's built-in app (Pixel and Samsung
apps, plus popular open-source ones). Written 2026-11-19, from each spec and a check of the code.
Updated 2026-11-20 with a theme audit, a performance audit, suite-wide features and the 2026
changes in the market-leading apps (see the sections after the apps).

**Priority:**

- **P1:** expected by nearly everyone, or something that's broken without it.
- **P2:** common, and missed by many people.
- **P3:** nice to have.

Items marked "Needs default SMS app" only work once Messages can receive (see the Messages
section).

## Phone

| Gap | Priority | Notes |
|-----|----------|-------|
| ~~Screen off near the ear during calls~~ | Done in 0.2 | Not verifiable on the emulator (it has no earpiece route). |
| ~~Audio route picker~~ | Done in 0.2 | Bluetooth needs testing on a real phone. |
| Voicemail | P2 | Long-press 1 to call voicemail (`TelephonyManager.voiceMailNumber`). Visual voicemail depends on the carrier and is out of scope. |
| Dual SIM | P2 | Ask which SIM to use, or use a per-contact default, through `PhoneAccountHandle`. |
| Quick reply on decline | P2 | "Can't talk now…" texts, which need `RESPOND_VIA_MESSAGE` handling. |
| Recents: delete, search, filter missed calls | P2 | Delete needs `WRITE_CALL_LOG`. |
| Swap and merge calls | P3 | Swap is close to free once hold works; merge needs conference support. |
| Speed dial | P3 | |
| Avatar for unknown numbers | P3 | Shows "#"; Messages shows the first digit. Use the same person icon in both. |
| Swipe to answer and decline | P3 | Pixel Phone is testing it in beta (2026). Buttons stay for accessibility. |

## Messages

| Gap | Priority | Notes |
|-----|----------|-------|
| ~~Become the default SMS app (receive SMS and MMS, notifications, reply from the notification)~~ | Done in 0.2 | MMS still needs testing on a real phone. |
| ~~Selection: copy, share and forward messages; pin and archive conversations~~ | Done in 0.2 | |
| ~~Delete conversations and messages, mark as read~~ | Done in 0.2 | |
| ~~Send pictures (MMS), group messages~~ | Done in 0.2 | |
| Search inside messages | P2 | The list search covers names and the latest message only. |
| Contact photos in the list | P2 | |
| ~~Block a number~~ | Done in 0.2 | |
| Retry a failed message | P2 | |
| Delivery reports, swipe actions | P3 | Scheduled send was done in 0.3. |
| Select part of a message to copy | P3 | Google Messages added precise copying in 2026. |
| Voice messages | P3 | Record audio and send it as MMS. |
| Avatar for unknown numbers | P3 | Shows the number's first digit ("0"). Use a person icon, as Phone should too. |

## Gallery

| Gap | Priority | Notes |
|-----|----------|-------|
| Basic editor: crop, rotate, straighten | **P1** | Currently hands off to another app with `ACTION_EDIT`, which most de-Googled phones don't have. |
| Move or copy to an album, create an album | P2 | Through MediaStore `RELATIVE_PATH` updates (with user confirmation). |
| Rotate in the viewer | P2 | Writes the EXIF orientation. |
| Search | P2 | By file name, folder and date; no on-device AI. |
| Slideshow, sort options, hidden albums | P3 | |
| Motion photos, video trimming | P3 | |
| One-tap auto enhance | P3 | Part of the editor: automatic levels and contrast, done on the device. Google Photos made its (cloud) version worldwide in 2026. |
| Albums and viewer previews | P2 | No screenshot tests cover them, so dark theme isn't checked there. |

## Calendar

| Gap | Priority | Notes |
|-----|----------|-------|
| Week and day views | **P1** | Only month and schedule views exist. |
| Edit or delete one instance of a repeating event | **P1** | Today a change applies to the whole series, which surprises people. |
| Search events | P2 | |
| Widget (agenda or month) | P2 | |
| Time zone picker, event colours | P3 | |
| Export ICS, attendees | P3 | |

## Music

| Gap | Priority | Notes |
|-----|----------|-------|
| Playlists (read MediaStore and `.m3u` playlists, create, edit) | **P1** | |
| Restore the queue and position after the app is closed | **P1** | Today closing the app loses what was playing. |
| Folders view | P2 | Common in open-source players; useful for audiobooks and podcasts. |
| Sleep timer | P2 | |
| Equaliser | P2 | Open the system equaliser through `AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`. |
| Reorder and remove in the queue | P2 | The queue sheet can only jump to a track. |
| Lyrics (`.lrc` files), Android Auto | P3 | |
| Album art in the song list | P2 | Lists show a note icon for every song; art only appears in the player. Use `loadThumbnail` on the album URI. |
| Genres and Folders tabs | P2 | Gramophone and Auxio both have them. |

## Clock

| Gap | Priority | Notes |
|-----|----------|-------|
| "Upcoming alarm" notification with Dismiss now | P2 | Pixel shows it 2 hours before. |
| Gradually increasing volume | P2 | |
| Skip the next occurrence of a repeating alarm | P2 | |
| Widgets (digital, analog, next alarm) | P2 | |
| Bedtime | P3 | |
| "Timer starter" widget | P3 | One-tap preset timers. Google Clock was reported to add one in July 2026. |

## Files

| Gap | Priority | Notes |
|-----|----------|-------|
| Trash (undo delete) | **P1** | Deleting is permanent today. A `.trash` folder per volume with 30-day expiry. |
| Zip: extract and compress | P2 | Pure-Java `java.util.zip`. |
| Recent files and categories (Images, Videos, Audio, Documents, Downloads) | P2 | MediaStore queries. |
| Bookmarked folders | P2 | |
| Transfers that keep running in the background, with progress | P2 | Large copies currently stop if you leave the app. |
| Storage usage summary | P3 | |

## Notes

| Gap | Priority | Notes |
|-----|----------|-------|
| Tappable links, phone numbers and emails | P2 | |
| Checklists: drag to reorder, move ticked items to the bottom | P2 | |
| Note colours | P2 | |
| Reminders | P2 | Could reuse Clock's approach to exact alarms. |
| Lock notes, images | P3 | |
| Notes from the lock screen | P3 | Hold the Android notes role (`ROLE_NOTES`), so the lock screen and stylus open a new note. Google Keep is testing this. |

## Contacts

| Gap | Priority | Notes |
|-----|----------|-------|
| Set or change a contact's photo | P2 | Through the photo picker, then crop. |
| Groups and labels | P2 | `ContactsContract.Groups`. |
| Find and merge duplicates | P2 | `AggregationExceptions`. |
| More than one address in the editor | P2 | |
| Custom ringtone per contact | P3 | |
| Share as a QR code | P3 | |

## Calculator

| Gap | Priority | Notes |
|-----|----------|-------|
| Move the cursor to edit inside the expression | P2 | Input only adds at the end. |
| Paste a number into the calculator | P2 | Copying works, pasting doesn't. |
| Unit converter | P3 | Pixel Calculator has added one. Currency needs the network, so it stays out. |

## All apps

| Gap | Priority | Notes |
|-----|----------|-------|
| Home screen widgets | **P1** | No app has one. The ones people expect: Clock (time and next alarm), Calendar (agenda), Notes (a note, and quick add), Music (now playing). Jetpack Glance is open source and Compose-based. |
| Launcher shortcuts (long-press the icon) | P2 | Only Messages and Notes have them. Clock: New alarm, Timer, Stopwatch. Calendar: New event. Contacts: New contact. Phone: favourites. |
| Quick Settings tiles | P3 | Clock: start the stopwatch or a timer. Notes: new note. Calculator: open it. |

## Theme audit (2026-11-20)

**Method:**

1. A code scan for hard-coded colours, screens painted with `Modifier.background` instead of a
   `Surface` (the cause of Phone's black-on-dark text, fixed in 0.2.1), and activities that skip
   `setTesseraContent`.
2. A review of every dark-theme screenshot golden.
3. Every app launched on the emulator in Dark and in Light.

**Result:** no app shows black-on-dark or white-on-light text. The live dark and light runs are
clean in all ten apps. The hard-coded colours are only the pure-black theme in `core:design`.
Clock's ringing screen uses `TesseraAppTheme` on purpose (it shows before unlock), inside a
`Surface`.

| Finding | Priority | Notes |
|---------|----------|-------|
| Notes: the selection bar title wraps ("2 / select / ed") | **P1** | A layout bug in both themes. Six actions leave the title no room at phone width. Move the less common ones (label, archive, share) into an overflow menu. |
| Clock: tab previews have no background | P2 | The Alarm, Clock, Timer keypad and Stopwatch previews draw the tab without `ClockRoot`'s `Scaffold`, so their dark goldens show black text on white. The app itself is fine, but these goldens can't catch a dark-theme regression. Wrap the previews in a `Surface`. |
| Screens with no screenshot test | P2 | Dark theme is only checked in CI where a preview exists. Missing, for example: Phone recents, keypad and contacts (only the call screen has previews); Messages new message and schedule sheet; Music albums and artists; Gallery albums and viewer; Calendar event details. |
| Gallery: the viewer is painted with `Modifier.background` | P3 | Every text in it sets its own colour today, but a new one wouldn't. Use a `Surface` (same root cause as Phone 0.2.1). |

## Performance audit (2026-11-20)

| Finding | Priority | Notes |
|---------|----------|-------|
| Gallery decodes full photos and video frames for thumbnails | **P1** | Coil decodes each original file (and extracts a video frame with `VideoFrameDecoder`) for every grid cell. MediaStore already keeps thumbnails: a small Coil fetcher using `ContentResolver.loadThumbnail` for `content://media` URIs would make scrolling a large library much smoother. This is the biggest win in the suite. |
| Theme sync wakes every Tessera app on each cold start | P2 | `setTesseraContent` calls `pullFromPeers()` once per process, which calls every other Tessera app's settings provider and starts up to nine processes. Changes are already pushed to every app when they happen, so pull only on first launch (when nothing is stored yet). |
| Messages reloads up to three times per incoming message | P2 | It observes `mms-sms`, `sms` and `mms`, and each callback reloads the conversation list and the open thread. Debounce them into one reload (about 300 ms). Gallery, Calendar and Contacts observers would also benefit, since a camera burst or a sync fires many changes. |
| Messages looks up each conversation's contact name separately | P2 | One `PhoneLookup` query per address on first load: hundreds with a long history. The cache never refreshes, so a contact added later shows as a number until the app restarts. Read all numbers in one query (as Phone's `numbers()` does), match them with `Addresses`, and refresh on Contacts changes. |
| No app Baseline Profiles | P3 | The bundled library profiles are installed (`profileinstaller` is present), but Tessera's own startup and scrolling code isn't covered. A baseline profile per app usually gives a noticeably faster first launch. Generating them needs an emulator in CI. |
| Lazy lists without stable keys | P3 | Music queue (reordering will need keys), Messages contact suggestions and draft attachments, Files breadcrumbs. |

Checked and fine: app start-up does almost nothing on the main thread (only Clock creates its
notification channels), there's no `runBlocking`, Contacts, Phone and Calendar read in bulk, and
Messages long threads were fixed in 0.3.

## What the market leaders added in 2026

Most of 2026's headline features in Google's apps need its servers or Gemini (Call Screen
conversations, "Call for me", "Help me edit" and AI touch-ups in Photos, RCS group mentions and
effects). They're out of scope for an offline suite. The offline ones are in the tables above:
Phone's swipe to answer, Messages' precise copy, Clock's timer starter widget, Keep's lock screen
notes, and the Genres and Folders tabs that open-source players (Gramophone, Auxio) offer. Google
Clock also added Live Updates for timers this year; Tessera Clock has had them since 0.2.1.

## Suggested order

1. ~~**Phone P1s** (proximity sensor and audio routes).~~ Done in Phone 0.2.
2. ~~**Messages as default SMS app.**~~ Done in Messages 0.2.
3. **Quick fixes from the audits:** the Notes selection bar, Clock preview backgrounds, the
   unknown-number avatar in Phone and Messages, the theme sync pull, and debounced observers.
4. **Gallery thumbnails** (the performance P1), then the **Gallery editor**.
5. **Widgets:** Clock and Calendar first, then Notes and Music.
6. **Calendar week/day and single-instance edits, Music playlists and queue restore, Files
   trash.**
7. The P2s app by app.
