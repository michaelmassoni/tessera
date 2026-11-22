# Calendar: spec

Package `io.github.michaelmassoni.tessera.calendar`. Status: v0.1, first implementation.

## Goals

- **Data:** a calendar over the system calendar database (`CalendarContract`). It works with any
  syncing account (for example DAVx⁵ for CalDAV) and with a local, device-only calendar.
- **Reminders:** it posts event reminder notifications itself, which a phone without Google
  Calendar otherwise lacks.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_CALENDAR`, `WRITE_CALENDAR` | Show, create, edit and delete events |
| `POST_NOTIFICATIONS` | Event reminders |
| `RECEIVE_BOOT_COMPLETED` | Not needed: the calendar provider re-arms its own alarms |

## Calendars

- **Device calendar:** if there are no writable calendars (no syncing account), the app creates a
  local "Device" calendar (`ACCOUNT_TYPE_LOCAL`), so events can be added straight away. It checks on each
  refresh until a writable calendar exists, then stops checking.
- **Visibility:** each calendar can be shown or hidden. This is stored in `Calendars.VISIBLE`,
  so other calendar apps agree.
- **Reloading:** the provider is watched only while the app is visible (returning reloads anyway),
  and a burst of changes from a sync becomes one reload 500 ms after the last. Launching loads
  once, not once in onCreate and again in onResume.
- **Colours:** event and calendar colours are the user's data (they come from the account) and
  are shown as is, like contact photos. Everything else uses theme colours.

## Views

- **Month:** the month as rounded day cells, with each event as a pill filled with its
  calendar's colour (black or white text, whichever reads). An event over several days is one
  bar across them, cut at the week's edges; single days stack under it (`WeekLayout`, unit
  tested). As many lanes show as the row has room for, and a day with more ends in "+N"; a row
  too short for a pill (a phone in landscape) shows a dot per event instead. Today's cell is
  tinted and its number circled.
  - On phones the grid fills the screen, and tapping a day opens a sheet with its events and New
    event. On wide windows the selected day is outlined and its events list beside the grid.
  - Pills don't take touches: a tap anywhere on a day opens the day. TalkBack reads each day as
    its date, event count and titles.
  - A day tapped in the month or date widget opens on that day's sheet.
  - Months are pages: the grid follows a swipe and settles on the next or previous month, and
    the arrows and "Today" animate the same way. The weekday names stay put. Events for the
    months either side are loaded too, so a page arrives with its pills.
- **Week and day:** a time grid (an hour is 56dp) with the hours down the side, the days (7, or
  1) across the top with today circled, and a strip above the grid for all-day events and timed
  events that cover the whole day (two per day in the week, then "+N"). Timed events are blocks
  in their calendar's colour from start to end (at least 20 minutes tall, so short ones stay
  tappable), clipped at midnight. Overlapping events share the width side by side, as many
  columns as the busiest moment of their cluster needs (`Timeline`, unit tested). A line marks
  the current time on today. The grid opens an hour before now.
  - Weeks and days are pages like months: swipe, or the arrows, for the next or previous one;
    Today jumps back. The week starts on the locale's first day.
  - Tapping an event opens it; tapping an empty slot starts a new event at that half hour.
    Tapping a day's heading in the week opens that day.
- **Schedule:** a scrolling list of upcoming events grouped by day, starting today, loading more
  as you scroll.
- **Picking a view:** an icon in the top bar shows the current view and opens Month, Week, Day
  and Schedule. The choice is remembered.

## Events

- **Detail:** a sheet with the title, time (or "All day"), repeat rule in words, location (tap to
  open maps), description, calendar and reminders. It has Edit and Delete.
- **Editor:** title, all-day, start and end date and time (pickers), time zone (the device's),
  calendar, repeat (none, daily, weekly, monthly, yearly), reminders (none, at the start, 5, 10,
  15 or 30 minutes, 1 hour or 1 day before; up to several), location and description.
  Laid out as current Android apps do: a large borderless title ("Add title"), then rounded
  tonal sections of rows with icons (when and repeat; calendar, with its colour; reminders;
  location and description as borderless fields). Rows open pickers or menus; no outlined boxes.
- **Repeating events:** Edit and Delete first ask "This event", "This and following events" or
  "All events".
  - **This event:** the series leaves that occurrence out (an `EXDATE`) and, when editing, a
    one-off event with the changes takes its place. The editor opens at the occurrence's own
    time and hides Repeat.
  - **This and following:** the series ends the second before the occurrence (`UNTIL`; a
    `COUNT` is dropped) and, when editing, a new series starts there with the changes, keeping
    what's left of a `COUNT` (`SeriesSplit`, unit tested). From the first occurrence it's the
    whole series.
  - **All events:** the series itself.
  - Exception rows (`CONTENT_EXCEPTION_URI`) aren't used: on a local calendar the provider then
    stops showing the series' other occurrences (seen on API 37). For the same reason every
    save of a repeating event writes its start, duration, rule, time zone and exclusions
    together; the provider doesn't re-expand it otherwise.
- **Creating:** the FAB creates an event on the selected day, starting at the next whole hour.
  `ACTION_INSERT` from other apps (`Events.CONTENT_URI`, with `CalendarContract` extras)
  pre-fills the editor.
- **Viewing:** `ACTION_VIEW` on an event URI opens its detail.

## Reminders

- **Delivery:** the calendar provider sends `EVENT_REMINDER` when an alert is due.
  `ReminderReceiver` queries `CalendarAlerts` for alerts that are due and still scheduled, posts
  one notification per event (title, time, location; tap opens the event), and marks each alert
  fired.
  The receiver is exported because the provider must reach it, so it handles only
  `EVENT_REMINDER` and shows only what it reads from the provider, never intent extras.
- **Snooze:** an action snoozes the reminder for 5 minutes with an alarm. Snoozing and the
  snoozed reminder go through `SnoozeReceiver`, which isn't exported, so no other app can post a
  notification under Calendar's name.

## Import

- **ICS files:** `ACTION_VIEW` of `text/calendar` or the overflow "Import" reads an `.ics`
  file's `VEVENT`s into a chosen calendar.
- **Fields:** `SUMMARY`, `DTSTART`, `DTEND` or `DURATION`, all-day dates, `LOCATION`,
  `DESCRIPTION`, `RRULE`, and `VALARM` triggers become reminders.
- **Time zones:** `TZID`s are honoured, and UTC times with a trailing `Z` are handled.

## Engine (pure Kotlin, unit tested)

- `MonthGrid`: weeks of a month, starting on the locale's first day of the week.
- `DayLayout`: assigns instances to days, with all-day and multi-day events on each day they
  cover.
- `Recurrence`: builds RRULE strings for the editor's choices and describes RRULEs in words.
- `Ics`: an iCalendar reader for VEVENT and VALARM, with line unfolding, escapes, `TZID` and
  value types.

## Layout

| Window | Layout |
|--------|--------|
| Compact | Month grid with the selected day's events below; Schedule as a list |
| Medium/Expanded | Month grid beside the selected day's list |

## Accessibility

- Day cells read "Wednesday 19 November, 3 events". Event chips read title and time.
- Colour is never the only signal: titles are always shown.

## Widgets (0.2)

Three home screen widgets, built with Jetpack Glance. They use the launcher's widget colours
(dynamic colour, light and dark follow the system), as first-party widgets do.

- **Agenda** (3×3, resizable): today's weekday and date, a + for a new event on today, then the
  next 30 days. Each day with events gets a heading ("Today", "Tomorrow", "Mon, Nov 23"),
  all-day events first. Events that have ended drop off; a multi-day event shows on each day.
  Tapping an event opens it. Below 240dp wide the date shortens to "Nov 21".
- **Month** (4×4, resizable): looks like the app's month view: rounded day cells (today's
  tinted) with events as pills in their calendar's colour, multi-day events as one bar, laid out
  by the same `WeekLayout`. As many lanes as the size allows, then "+N". Tapping a day opens the
  app on that day's sheet. The header has ‹ and › to move between months, today's date to come
  back to this month, and + (all 40dp; the year shows for other years).

  **Settings (0.5)**, shown when the widget is added and from its long-press menu (Settings): a
  live preview of the widget, then Background (Solid, Tinted, Transparent), Opacity (0–100% in
  steps of 10, of the background only), Theme (Follow app, Light, Dark) and Calendars (all visible
  calendars, each can be left out). Each placed widget keeps its own settings and month in its
  Glance state. Backing out of a new widget's settings cancels adding it.
- **Date** (1×1, resizable): the weekday and date; from 140dp wide, the next event too.

Without calendar access a widget says "Open Calendar to allow access to your calendars".

Redrawing, without polling:

- When the calendar changes (an edit here, or a sync by DAVx⁵ or any other app): a WorkManager
  job with a content trigger on `content://com.android.calendar`. Each run redraws and arms the
  next; it stops when no widget is placed.
- At midnight, and when an event ends: an inexact (non-waking) alarm. Nobody sees the widget
  with the screen off.
- On time, time zone and locale changes.

The widget picker shows generated previews with made-up events (Android 15 and later),
published once per app version.

## Out of scope for v0.1

- Editing a single instance of a repeating event.
- Attendees and invitations.
- Event colours.
- Tasks.
- Exporting ICS.
- Time zone picker.

## First run

See [setup.md](setup.md) for what calendar asks for the first time it opens.
