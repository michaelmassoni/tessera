# Feature gaps

A review of each app against what people expect from the phone's built-in app (Pixel and Samsung
apps, plus popular open-source ones). Written 2026-11-19, from each spec and a check of the code.

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
| Delivery reports, scheduled send, swipe actions | P3 | |

## Gallery

| Gap | Priority | Notes |
|-----|----------|-------|
| Basic editor: crop, rotate, straighten | **P1** | Currently hands off to another app with `ACTION_EDIT`, which most de-Googled phones don't have. |
| Move or copy to an album, create an album | P2 | Through MediaStore `RELATIVE_PATH` updates (with user confirmation). |
| Rotate in the viewer | P2 | Writes the EXIF orientation. |
| Search | P2 | By file name, folder and date; no on-device AI. |
| Slideshow, sort options, hidden albums | P3 | |
| Motion photos, video trimming | P3 | |

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
| Lyrics (`.lrc` files), Android Auto, widget | P3 | |

## Clock

| Gap | Priority | Notes |
|-----|----------|-------|
| "Upcoming alarm" notification with Dismiss now | P2 | Pixel shows it 2 hours before. |
| Gradually increasing volume | P2 | |
| Skip the next occurrence of a repeating alarm | P2 | |
| Widgets (digital, analog, next alarm) | P2 | |
| Bedtime | P3 | |

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
| Widget, lock notes, images | P3 | |

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

## Suggested order

1. ~~**Phone P1s** (proximity sensor and audio routes).~~ Done in Phone 0.2.
2. ~~**Messages as default SMS app.**~~ Done in Messages 0.2.
3. **Gallery editor, Calendar week/day and single-instance edits, Music playlists and queue
   restore, Files trash.**
4. The P2s app by app.
