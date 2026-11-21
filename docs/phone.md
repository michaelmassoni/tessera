# Phone: spec

Package `io.github.michaelmassoni.tessera.phone`. Status: v0.1, first implementation.
**Experimental until tested on a real device with a SIM.**

## Goals

A dialler with favourites, recents, contacts and a keypad. Once you make it the default phone
app, it also shows its own in-call screen. Until then, calls go through the existing phone app,
so trying Tessera Phone can't break calling.

## Two levels

1. **Not the default (safe):**
   - Browse recents, favourites and contacts, and use the keypad.
   - Calls are placed with `ACTION_CALL`, so the system's current phone app shows the call.
2. **Default phone app (`RoleManager.ROLE_DIALER`, opt-in from a banner):**
   - The `InCallService` shows Tessera's in-call screen for incoming and outgoing calls,
     including a full-screen incoming call over the lock screen.
   - Android always shows emergency calls in a system-approved UI. If the in-call service fails,
     Telecom falls back to the system dialler.

## Permissions

| Permission | Why |
|------------|-----|
| `CALL_PHONE` | Place calls directly from the app |
| `READ_CALL_LOG` | Recents |
| `READ_CONTACTS` | Names, favourites and the contact list |
| `POST_NOTIFICATIONS` | Incoming-call and ongoing-call notifications (when default) |
| `WAKE_LOCK` | Turning the screen off near the ear during calls |

When it's the default phone app, the role grants the in-call binding. `USE_FULL_SCREEN_INTENT`
covers the incoming-call screen.

## Tabs

- **Favourites:** starred contacts in a grid of avatars. Tap to call the primary number.
- **Recents:**
  - The call log, newest first, grouped by day.
  - Each row has an icon for incoming, outgoing, missed or rejected, the name (or number), and
    the time.
  - Answered calls show how long they lasted after the time ("6:15 PM · 3 min, 20 sec", from
    `CallLog.Calls.DURATION`, formatted with `MeasureFormat` in the phone's language).
  - The call log and contact numbers are read in parallel on every resume.
  - Tap to call back. Long-press for a sheet of options: View contact (a saved number) or Create
    new contact and Add to a contact (an unsaved one, through the contacts app's insert and
    insert-or-edit intents), Send message (`smsto:`), Copy number, and Edit number before call
    (opens the keypad with it).
- **Contacts:** every contact with a phone number, with search. Tap to call, or choose a
  number if there are several.
- **Keypad:**
  - The number field formats as you type (`PhoneNumberUtils`) and backspace works.
  - Long-press 0 for +.
  - The call button is filled.
  - Contacts whose number contains the digits typed are suggested above the keypad.

## In-call screen (default phone app only)

- **Shows:** caller name and photo (contacts lookup) or the number, the call state (calling,
  ringing, on hold, ended) and a timer.
- **Incoming:** Answer and Decline buttons. The notification uses `CallStyle` with a
  full-screen intent: over the lock screen (or with the screen off) it wakes the phone into the
  in-call screen; while the phone is in use it's a heads-up with Decline and Answer.
- **Ringing is Telecom's:** the service doesn't declare `IN_CALL_SERVICE_RINGING`, so the system
  plays the ringtone (per-contact ringtones too) and vibrates exactly as the phone's settings say:
  ring and vibrate modes, "Vibrate for calls", Do Not Disturb, and the volume key to silence.
  Declaring it (before 0.2.3) made Telecom skip both, and Phone played neither. The call
  notifications use silent channels (`incoming_calls`, `ongoing_calls`) so nothing dings over
  the ringtone. The caller is the notification's person with a `tel:` URI, so Do Not Disturb lets
  starred contacts' full-screen call through.
- **Active:**
  - Mute (`InCallService.setMuted`).
  - Audio: with only the earpiece and speaker, a Speaker toggle. With a wired headset or
    Bluetooth connected, an Audio button that shows the current route and lists them all
    (`CallEndpoint` on Android 14+, with device names; `CallAudioState` before, where Bluetooth
    devices are just "Bluetooth" because names need a permission Phone doesn't ask for).
  - Hold.
  - Keypad, which sends DTMF.
  - End.
- **Screen off at the ear:** while a call is live and its audio is on the earpiece, a
  `PROXIMITY_SCREEN_OFF_WAKE_LOCK` turns the screen off when the phone is near the face. It's
  released with `RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY` when the call ends or audio moves to the
  speaker, a headset or Bluetooth.
- **Ongoing call notification:** with Hang up. Tapping it returns to the call.
- **Second calls:** a second incoming call during a call shows Answer (which holds the first) and
  Decline. Conference merging is out of scope.

## Engine (pure Kotlin, unit tested)

- `Dialpad`: key-to-letter map and appending or deleting digits; the long-press 0 gives +.
- `CallLogGrouping`: day sections, and collapses consecutive calls from the same number into one
  row with a count ("(3)").
- `NumberMatch`: matches typed digits against contact numbers by digits only.
- `AudioInfo` and `Proximity`: when the audio button is a toggle or a list, the toggle's target,
  and when the screen should turn off near the ear.

## Out of scope for v0.1

- Voicemail.
- Conference calls.
- Call recording.
- Spam and caller ID.
- Blocked numbers UI (the system's is linked from settings).
- RTT.
- Video calls.
- Dual-SIM picking (the system's default SIM is used).
