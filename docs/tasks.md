# Tasks: spec

Package `io.github.michaelmassoni.tessera.tasks`. Status: draft for v0.1 (2026-11-22).

## Goals

A simple to-do app modelled on Google Tasks, not on Tasks.org's feature set: lists of tasks with
an optional date, time and repeat, subtasks, and a star. Most use is from a home-screen widget:
tick things off and add one quickly. Repeating tasks with a time are reminders that must fire on
time (the maintainer uses them for medications). Tasks sync over CalDAV (VTODO) to the
maintainer's server (OpenCloud, which serves CalDAV with Radicale), with no other app needed; a
list can also stay on the phone only.

## Model

- **List:** a name, a colour (from the server, else chosen), and where it lives: this phone, or a
  CalDAV calendar collection that supports VTODO.
- **Task:**
  - Title, details (notes).
  - Due date, optional time. A task with a time reminds at that time.
  - Repeat: daily, weekly (on chosen weekdays), monthly, yearly, every N of those, from the due
    date or from completion ("after done"). Stored as an RRULE.
  - Starred.
  - Subtasks: one level, as in Google Tasks.
  - Completed (with when).
  - Order within the list, set by dragging.

## Screens

- **Lists:** the list's tasks; a list switcher (chips or a drawer); "Starred" view across lists.
  Sort: my order (drag), due date, starred first, title. With only one list (and stars off) there's
  nothing to switch between: no tabs or list heading; the list's name, sort and menu (with "New
  list") sit in the top bar.
- **Settings > Tasks > Starred tasks** (on by default): off hides the stars on rows, in the editor and the
  add sheet, the Starred view and "starred first". Stars already set stay on the tasks (and sync).
- **Task rows:** a round checkbox, title, details' first line, due date/time chip (red when
  overdue), repeat icon, star. Subtasks indented under their parent. Swipe to complete; long-press
  to drag.
- **Completed:** a collapsed "Completed (n)" section at the bottom of each list; tap to expand,
  untick to restore, "Delete all completed".
- **Add:** a bottom sheet with title, then optional details, date/time, repeat, star; Enter adds
  and keeps the sheet open for the next one.
- **Task details:** full-screen editor: title, details, list (move), date/time, repeat, star,
  subtasks (add, tick, reorder), delete.
- **Settings:** accounts (add a CalDAV account, sign out), default list, reminder sound/vibrate
  (the notification channel), sync interval.

## Completing a repeating task

As Google Tasks, Tasks.org and OpenTasks do: ticking it moves it to its next date (same task, same
UID on the server) and records the completion; it stays in the list. "After done" repeats count
from the completion time. Over CalDAV this keeps one VTODO whose DUE/DTSTART move forward, which
every client reads the same way.

## Reminders

- Exact alarms (`SCHEDULE_EXACT_ALARM`, asked in setup; `USE_EXACT_ALARM` isn't for to-do apps)
  at each task's due time, re-armed at boot, time-zone change and after sync.
- Notification: title, details, list; actions **Done** (completes, so a repeating one moves to
  its next time) and **Snooze 10 min**. Opening shows the task. A high-importance channel, so it
  sounds and peeks like an alarm-lite.
- Missed while the phone was off: shown on boot.
- Without exact alarms Android may deliver a reminder up to an hour late. The list then shows a
  banner saying so, with a button that opens the setting.

## Widget

- A list's tasks with checkboxes (tap to complete, with a short strike-through before it leaves),
  the list's name (with more than one list, tap it to switch to the next), and **+** that opens the add sheet over the home
  screen (a translucent activity) without opening the app.
  Tapping a task opens it in the app; tapping anywhere else (not a checkbox, + or the list switcher)
  opens the app.
- Due today/overdue shown in colour. Resizable.
- **Widget settings** (each widget its own; shown when it's placed, and from the launcher's
  reconfigure): list (only with more than one), order (due date by default), show list name, show
  due dates, tasks due later (all, the next 7 days, or hidden until their day; undated tasks always
  show), background (tinted, solid, none) and opacity, theme (same as Tessera, light, dark).
- Glance, with the keep rules from Calendar (R8 broke Glance widgets there).

## CalDAV sync

- **Account:** server address, username, password (an app password). Discovery: PROPFIND
  `current-user-principal`, then `calendar-home-set`, then the home's calendars, keeping those
  whose `supported-calendar-component-set` includes VTODO. The person picks which become lists.
  Creating a list makes a new calendar collection (MKCALENDAR, VTODO only).
- **Sync:** per collection, `sync-collection` (RFC 6578, which Radicale supports) with the saved
  sync token; else compare `getetag`s from a `calendar-query`. Changed resources are fetched
  with `calendar-multiget`. Local changes are PUT with `If-Match` (new ones `If-None-Match: *`),
  deletions DELETEd. A 412 means the server changed it: fetch it, keep the server's version of
  fields not changed here, re-apply local changes to the rest, PUT again.
- **When:** on open, a few seconds after a change, every 15 minutes (WorkManager, network
  required), and from pull-to-refresh.
- **iCalendar:** our own VTODO reader/writer (pure, unit tested), keeping every property and
  component it doesn't understand, so other clients' data (Tasks.org's alarms, categories,
  geo) survives a round trip. Mapping: SUMMARY, DESCRIPTION, DUE/DTSTART (date or date-time with
  TZID), RRULE, STATUS/COMPLETED/PERCENT-COMPLETE, PRIORITY 1 = starred (as Tasks.org does),
  RELATED-TO;RELTYPE=PARENT for subtasks, X-APPLE-SORT-ORDER for order.
- **Password:** encrypted with a Keystore key, kept out of backups (as Music's server password).
- **Plain http:** only for private addresses and Tailscale (as Music).

## Storage

`files/tasks.json` (lists, tasks, sync state: href, etag, dirty/deleted flags, sync token) written
atomically, as Notes does. Tessera backups include it except the account's password.

## First run

Welcome; Notifications (for reminders); Alarms & reminders (exact alarms); "Sync with a server?"
(sign in, pick calendars, or "Keep tasks on this phone"); restore.

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | CalDAV sync with the server the person adds; nothing else is contacted |
| `ACCESS_LOCAL_NETWORK` | Android 17 asks before an app reaches the local network. Asked for only when the server is on it: at sign-in, and once on launch for an account made earlier. Without it a server on the local network can't be reached; Settings says so and links to the permission. |
| `POST_NOTIFICATIONS` | Reminders |
| `SCHEDULE_EXACT_ALARM` | Reminders at the exact time (medications) |
| `RECEIVE_BOOT_COMPLETED` | Re-arm reminders after a restart |

## Engine (pure Kotlin, unit tested)

VTODO reader/writer, RRULE next-occurrence, task ordering and subtasks, sync merge
(three-way field merge on conflict), WebDAV XML parsing (multistatus).

## Sync settings (0.2)

Sync every (manually, 15 min to once a day; default 4 hours), immediately on local changes, and
only on Wi-Fi, shared with Calendar and Contacts through `core:dav` (docs/sync.md). 0.1 synced
every 15 minutes, fixed.

## Out of scope for v0.1

Locations, tags/labels, attachments, time estimates, sharing lists, DAVx5 or OpenTasks providers,
Android Auto (not an allowed category).

## Launcher shortcuts (0.6)

Long-press the app icon: **New task** opens the quick-add sheet, as the widget's + does. Each icon is the Material Symbol on the app's launcher colour.
