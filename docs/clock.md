# Clock: spec

Package `io.github.michaelmassoni.tessera.clock`. Status: v0.1, first implementation.

## Goals

The clock must be **reliable**. An alarm that doesn't ring is the worst bug this suite can
ship. Alarms have to fire on time in Doze, with the screen locked, after a reboot (including
before the first unlock), across time-zone and daylight-saving changes, and after an app update.
After that, it should feel first-party: four tabs (Alarm, Clock, Timer, Stopwatch) in Material 3
Expressive, with adaptive navigation.

No network access. No analytics.

## Navigation

`NavigationSuiteScaffold` gives a bottom navigation bar on compact screens and a navigation rail
on medium and expanded widths. Its tabs are Alarm, Clock, Timer and Stopwatch. The selected tab
is saved across configuration changes and process death. On wide windows, lists use an adaptive
grid (two or more columns).

## Alarms

### Features

- The list shows every alarm: its time, label, repeat days, and an on/off switch. Tapping an
  alarm expands it to edit:
  - **Repeat days:** one toggle per weekday, ordered by the locale's first day of the week.
  - **Label.**
  - **Sound:** the system ringtone picker for alarm sounds, with "Default" and "Silent" options.
    No storage permission is needed.
  - **Vibrate.**
  - **Delete**, with an undo snackbar that times out (`SnackbarDuration.Long`; with an action,
    Material 3 otherwise defaults to indefinite).
- **Adding an alarm:** a floating action button opens the Material 3 time picker (dial or
  keyboard input). New alarms are enabled and don't repeat.
- **Feedback:** enabling an alarm, or changing its time, shows a snackbar such as "Alarm set
  for 7 hours and 12 minutes from now".
- **Format:** times follow the system 12/24-hour setting and the locale (`DateFormat`).
- **When an alarm rings:** a full-screen activity appears over the lock screen with the time,
  the label, and Snooze and Dismiss buttons. The heads-up notification has the same two actions.
- **Snooze:** 10 minutes by default (configurable: 5, 10, 15 or 20). A snoozed alarm shows
  "Snoozed until 7:40" in the list, with a way to cancel the snooze.
- **Gradually increasing volume (0.7):** with it on, an alarm starts just audible (5%) and rises to
  full over the chosen time, along a cubed curve so it sounds like a steady climb (`Crescendo`,
  unit tested). Only alarms: timers ring at full volume at once. The last-resort generated beep
  can't change volume, so it plays at full.
- **Auto-silence:** after 10 minutes of ringing (configurable: 5, 10, 15 or never) the alarm
  stops and a "Missed alarm" notification is posted.
- **Next alarm:** the system shows it in the status bar and on the lock screen, because
  `setAlarmClock` is used.
- **Other apps** can create alarms through the standard `AlarmClock` intents:
  - `SET_ALARM`, with `EXTRA_HOUR`, `EXTRA_MINUTES`, `EXTRA_MESSAGE`, `EXTRA_DAYS`,
    `EXTRA_VIBRATE` and `EXTRA_SKIP_UI`.
  - `SHOW_ALARMS`.
  - `DISMISS_ALARM` and `SNOOZE_ALARM`, which act on the ringing alarm.

### Time rules (`AlarmTimes`, pure Kotlin, unit tested)

- **Next trigger:** the first wall-clock occurrence of HH:MM after "now" in the current zone.
  - **One-off alarms:** today if the time is still ahead, otherwise tomorrow.
  - **Repeating alarms:** the next day in the set, which may be today.
- **Daylight-saving gap** (for example 02:30 on a spring-forward night): the alarm rings at the
  first valid instant after the gap. `ZonedDateTime` shifts the time forward by the length of
  the gap.
- **Daylight-saving overlap** (for example 01:30 on a fall-back night): the alarm rings at the
  **earlier** offset, once.
- **Time-zone and clock changes:** alarms keep their wall-clock time, so every enabled alarm is
  rescheduled on `TIME_SET` and `TIMEZONE_CHANGED`.
- **After ringing:** a one-off alarm is disabled. A repeating alarm is scheduled for its next
  day, computed from the scheduled time rather than "now", so a late dismissal can't skip a day.
- **Snooze:** sets `snoozedUntil = now + snooze`. That instant replaces the next trigger until it
  passes, or until the user dismisses or edits the alarm.
- **Skip next:** a repeating alarm's card has "Skip next alarm". It stores that occurrence's
  instant in `skipped`, and the next trigger moves to the occurrence after it. The card shows
  "Skipping Mon, 24 Nov" with Undo. Editing the time or days, turning the alarm off, or the alarm
  ringing clears it. Skipping a one-off alarm turns it off.
- **Upcoming notification:** 2 hours before an alarm (straight away if it's sooner), a quiet
  "Upcoming alarm" notification (channel "Upcoming alarms", low importance) shows the day, time
  and label, with Dismiss now. Dismiss now skips a repeating alarm once, turns a one-off alarm
  off, and dismisses a snoozed one ("Snoozed alarm"). It's scheduled with an inexact
  `setAndAllowWhileIdle` beside the alarm, is cancelled whenever the alarm is rescheduled or
  deleted, and times out when the alarm rings.

## How alarms are delivered (reliability)

| Concern | Approach |
|---------|----------|
| Exact time in Doze | `AlarmManager.setAlarmClock()`. Alarm clocks are exempt from Doze and App Standby and shown to the user |
| Permission to use exact alarms | `USE_EXACT_ALARM` (API 33+, granted at install for alarm-clock apps; F-Droid has no policy against it). `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` for API 31–32, checked with `canScheduleExactAlarms()`. If it's revoked, an in-app banner links to the settings page, and alarms are rescheduled when `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` arrives |
| Ringing | The alarm's `PendingIntent` targets `AlarmReceiver`, which starts `RingingService`. That's allowed from the background because the alarm came from `setAlarmClock` |
| Foreground service type | `systemExempted`, which Android 14+ allows for apps holding exact-alarm permission that use it to keep alarms ringing. It needs `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` |
| UI over the lock screen | A full-screen-intent notification (`CATEGORY_ALARM`) opens `RingingActivity` (`showWhenLocked`, `turnScreenOn`). On Android 14+, if `canUseFullScreenIntent()` is false, a banner links to the setting; the heads-up notification still has Snooze and Dismiss |
| Notification permission (Android 13+) | `POST_NOTIFICATIONS` is requested when the first alarm or timer is created, with an explanation. If it's denied, a banner explains that alarms will sound but can't show on screen |
| Sound | `MediaPlayer` with `USAGE_ALARM`, looping. If that fails it falls back to the chosen URI, then the system default alarm, then a `ToneGenerator` beep on the alarm stream. Some sound always plays |
| Vibration | `VibrationEffect` waveform, repeating, with alarm usage attributes |
| Reboot | Alarms are stored in **device-protected storage**, and `LOCKED_BOOT_COMPLETED` (the receiver is `directBootAware`) reschedules them **before the first unlock**. `BOOT_COMPLETED` is a fallback |
| App update | `MY_PACKAGE_REPLACED` reschedules everything |
| Process death while ringing | The service is sticky, and the state of the ringing alarm is persisted |
| Two alarms at once | The second one replaces the first in the ringing service. The first is treated as dismissed, and its repeat schedule continues |

## Timer

- **Entry:** a keypad where digits shift in from the right (`h:mm:ss`), as on the Pixel.
  Start, then add more timers with the floating action button.
- **Running timers:** shown as cards, each with a `CircularWavyProgressIndicator` (Expressive),
  the remaining time, its label, Pause/Resume, Reset, +1:00 and Delete.
- **Labels:** tap the title ("Add label" until set) to name a timer. The original duration shows
  under it. The name is used in the notification and on the "Time's up" screen.
- **Scheduling:** an exact alarm on `ELAPSED_REALTIME_WAKEUP` (`setExactAndAllowWhileIdle`) for
  each running timer. No foreground service runs while a timer counts down. An ongoing
  notification shows the countdown (`setChronometerCountDown`) with Pause and Stop actions. See
  "Lock screen and Live Updates" below.
- **Expiry:** the same `RingingService` and full-screen UI, showing "Time's up". The actions are
  Stop and +1:00. The display counts up past zero, as on the Pixel.
- **State:** timers are persisted with both an elapsed-realtime deadline and a wall-clock
  deadline. After a reboot, a timer that was running is restored using the wall clock. If it
  expired while the device was off, it shows as expired.

## Stopwatch

- **Controls:** a matched pair of large Expressive icon buttons: Lap/Reset (round, tonal) and
  Start/Pause (wide, filled). Both are 96dp tall and morph shape when pressed.
- **Display:** a large `h:mm:ss.cc` readout. The ring around it shows progress through the
  current minute.
- **Laps:** a list with the lap number, lap time and total. The fastest and slowest laps are
  marked with text, not color alone.
- **Persistence:** the state is kept as an elapsed-realtime base plus accumulated time, so it
  survives process death. It doesn't survive a reboot; the elapsed clock resets, and this is
  documented.
- **While running:** an ongoing notification with a chronometer shows the elapsed time, with
  Pause and Lap actions.

## Lock screen and Live Updates

- Timer and stopwatch notifications use default-importance channels with no sound or vibration
  (`timers_live`, `stopwatch_live`). Silent (low-importance) notifications are hidden from the lock
  screen by default, which is why 0.2 moved off them. The old `timers` and `stopwatch` channels
  are deleted.
- They're `VISIBILITY_PUBLIC`: a countdown isn't private, so it shows even with "hide sensitive
  content".
- On Android 16 QPR1+ they request promotion (`setRequestPromotedOngoing`, with
  `POST_PROMOTED_NOTIFICATIONS`). The system then shows them as Live Updates: a status bar chip and
  near the top of the lock screen. The user can turn this off per app.

## World clock

- **Local time:** large local time and date at the top.
- **Cities:** each one shows its local time, how it differs from local time ("+5 hr",
  "−3 hr 30 min"), and "Today", "Tomorrow" or "Yesterday".
- **Adding a city:** a search screen lists every IANA time zone that has an exemplar city, using
  ICU's **localized** city names (`TimeZoneNames.getExemplarLocationName`) and the country name.
  No bundled database or network access is needed.
- **Removing a city:** swipe to delete, with undo.
- **Updates:** times tick every minute, aligned to the minute boundary, and only while the tab
  is visible.

## Widget

- **Clock (3×2, resizable, home and lock screen):** the time (large, primary colour), the date,
  and the next alarm from `AlarmManager.getNextAlarmClock()`, which includes other apps' alarms
  as on Pixel. Tapping the time opens World clock; tapping the alarm opens Alarms.
- A RemoteViews layout with `TextClock`, so the time stays current without waking the app. It's
  redrawn only on `NEXT_ALARM_CLOCK_CHANGED` and `LOCALE_CHANGED`. Colours are the system
  dynamic palette, with a values-night set for dark mode.

## Sleep calculator (0.5)

For waking between sleep cycles rather than in the middle of one, as apps like Sleep Time do.

- **Opening it:** a moon button in the Alarms tab's top bar opens a full-screen page (back
  closes it).
- **Modes** (a segmented button):
  - **Sleep now:** wake-up times if you go to bed now. Follows the clock, minute by minute.
  - **Wake at:** pick a wake-up time (the next enabled alarm's time by default, else 07:00);
    shows bedtimes. Bedtimes already past are left out; if none are left, it says so.
  - **Bed at:** pick a bedtime (23:00 by default); shows wake-up times, with a "Remind me at
    23:00" button for the bedtime itself.
- **The maths** (`SleepCycles`, pure Kotlin, unit tested): asleep = bedtime + time to fall
  asleep; wake = asleep + n × cycle, for n = 3 to 6 (4.5 to 9 hours with 90-minute cycles).
  Times are instants in the current zone, so a night across a daylight-saving change still gets
  the real number of hours. A picked time means its next occurrence after now.
- **Each suggestion** shows the time, the number of cycles and the hours of sleep. 5 and 6
  cycles are marked Recommended (7.5 to 9 hours, what adults are advised to get).
- **Tapping a wake-up time** sets a one-off alarm for it (or switches on an existing one-off
  alarm at that time, rather than adding a duplicate), with the usual "Alarm set for 7 hours
  30 minutes from now" snackbar. One already on shows a tick. A wake-up time more than a day
  away (a far-off bedtime) can't be a one-off alarm, so it isn't tappable.
- **Tapping a bedtime** asks what to set: a bedtime reminder, an alarm for the wake-up time, or
  both (both ticked, the alarm unticked if it's already on).
- **Bedtime reminder:** one notification at that time ("Time for bed: go to sleep now to wake
  up at 06:30"), on its own "Bedtime" channel (default importance, so it makes a sound). One at
  a time: a new one replaces it. The page shows it with Cancel. It's an exact alarm
  (`setExactAndAllowWhileIdle`, `RTC_WAKEUP`), kept in `bedtime.json` and rescheduled after a
  reboot or a clock change; one already past is dropped.
- **Footnote** on the page: the cycle length and falling-asleep time in use, and that they can
  be changed in Settings.

## Settings (overflow menu, then Settings)

- Snooze length: 5, 10, 15 or 20 minutes.
- Silence after: 5, 10 or 15 minutes, or Never.
- Gradually increase volume (0.7): Off (default), 15 or 30 seconds, or 1, 2 or 5 minutes.
- Sleep calculator: sleep cycle length (80, 90 or 100 minutes; 90 by default) and time to fall
  asleep (0, 5, 10, 15, 20 or 30 minutes; 15 by default).
- Permission and status checks, with links to the system settings: exact alarms, full-screen
  intents, notifications, and battery optimization (shown for information only; it isn't
  needed with `setAlarmClock`).

## Permissions and why each is needed

| Permission | Why |
|------------|-----|
| `USE_EXACT_ALARM` (33+) / `SCHEDULE_EXACT_ALARM` (≤32) | Alarms, timers and bedtime reminders must fire at an exact time |
| `POST_NOTIFICATIONS` | Ringing UI, timer countdown, missed alarms, bedtime reminders |
| `POST_PROMOTED_NOTIFICATIONS` | Timer and stopwatch as Live Updates (Android 16 QPR1+; normal permission) |
| `USE_FULL_SCREEN_INTENT` | Show the ringing alarm over the lock screen |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | Keep sound and vibration going while an alarm or timer rings |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after a reboot |
| `VIBRATE` | Vibrating alarms |
| `WAKE_LOCK` | Keep the CPU awake between the alarm broadcast and the service starting |

Not requested: storage, location (cities are chosen by hand), network.

## Accessibility

- Every time is announced in full ("7:30 AM, Monday and Wednesday, alarm on").
- Each repeat-day toggle announces the full day name and its state.
- The ringing screen uses large buttons (not only a swipe gesture) for Snooze and Dismiss.
- The timer keypad has spoken labels, and the remaining time is a polite live region that is
  updated once a minute, so TalkBack isn't flooded.
- Layouts are checked at 200% font scale.

## Testing

- **Unit tests:** next-trigger maths, including repeat days, DST gaps and overlaps, zone changes,
  snooze and late dismissal; timer and stopwatch arithmetic; city-name search.
- **Robolectric:**
  - Enabling an alarm schedules a `setAlarmClock` at the correct time.
  - Disabling or deleting it cancels the schedule.
  - The boot receiver reschedules enabled alarms.
  - The fire receiver starts the ringing service, disables a one-off alarm and schedules the
    next repeat.
- **Emulator end to end:**
  - An alarm a minute or two ahead rings in forced Doze (`dumpsys deviceidle force-idle`) with
    the screen off and locked.
  - A 10-second timer expires in Doze.
  - The alarm is re-registered after a reboot (`dumpsys alarm`).

## Out of scope for v0.1

- Bedtime mode.
- Wake-up challenges.
- Spotify and other streaming sounds.
- Analog clock faces.
- Wear OS.

## First run

See [setup.md](setup.md) for what clock asks for the first time it opens.

## Launcher shortcuts (0.8)

Long-press the app icon: **Timer** and **Stopwatch** open those tabs (`SHOW_TIMERS`, and the tab extra). Each icon is the Material Symbol on the app's launcher colour.

## Quick Settings tiles (0.9)

- **Stopwatch** starts and pauses the stopwatch without opening the app (on while running; the
  paused time underneath). The app redraws it when the stopwatch changes there.
- **Timer** opens the Timer tab.
