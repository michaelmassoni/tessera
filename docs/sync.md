# Sync with your own server (CalDAV, CardDAV): spec

Status: agreed 2026-11-23. Calendar, Contacts and Tasks sync with the person's own server
(OpenCloud, Nextcloud, Radicale, Baïkal and others) by themselves, so DAVx5 isn't needed. The
settings follow DAVx5's, which the maintainer uses today ("every 4 hours, and immediately on local
changes" for all three).

## Design

- **Shared code in `core:dav`** (it's the second and third app to need it): the WebDAV client
  (OkHttp), multistatus parsing, service discovery, the iCalendar and vCard reader/writer (one line
  format, keeping every property it doesn't understand), the sign-in form, the encrypted password
  (`SecretBox`) and the sync settings screen. Tasks moves onto it.
- **Each app has its own Android account** ("Tessera Calendar", "Tessera Contacts"), with a sync
  adapter for the system calendar or contacts database. Synced events and contacts live in
  Android's own databases, as with DAVx5, so every app sees them, and the provider marks local
  edits dirty for the next sync. Tasks keeps its own store (there's no system tasks database).
- **Each app signs in on its own** (three sign-ins for three apps). Sharing one sign-in between apps
  would mean passing a password from one app to another; not in v1.
- Plain `http://` only for private and Tailscale addresses, as in Music and Tasks.

## Settings (each syncing app, under "Sync")

| Setting | Choices | Default |
|---------|---------|---------|
| Server | Sign in / the account ("michael at dav.example.com") / Sign out | – |
| What to sync | The server's calendars, address books or task lists, each on or off | all |
| Sync every | Manually only, 15 min, 30 min, 1 hour, 2 hours, **4 hours**, once a day | 4 hours |
| Sync immediately on local changes | on/off | on |
| Only on Wi-Fi (unmetered) | on/off | off |
| Sync now | – | – |
| Last synced | "2 minutes ago", or the last error with what to do about it | – |

- Periodic syncs use WorkManager (network required; unmetered when chosen), so the 15 min to
  once-a-day choices behave as in DAVx5.
- "Immediately on local changes": Calendar and Contacts register `syncAutomatically` for their
  account, so the system's content-changed trigger runs a sync after an edit in any app. Android
  waits about 30 seconds (a burst of edits makes one sync), so it's usually within a minute; it can
  wait longer while the phone is idle. DAVx5 relies on the same trigger. Off: local changes wait for the next periodic or manual sync. Tasks schedules a sync after
  its own changes.

## Calendar

- Each server calendar holding events (`VEVENT`) becomes one Android calendar under the account,
  with its name, colour and read-only state (from `current-user-privilege-set`).
- **Past events:** "Ignore events older than" 30 / 90 / 180 / 365 days / keep all (default 90
  days), as DAVx5, so a long history isn't copied to the phone.
- **Mapping:** start, end or duration with time zone, all-day, title, location, description,
  repeat rules (`RRULE`, `RDATE`, `EXDATE`), changed or cancelled occurrences (`RECURRENCE-ID`) as
  Android exception events, reminders (`VALARM` to Reminders), attendees and organizer, busy/free
  (`TRANSP`), private (`CLASS`), status. Everything else in the event is kept and written back
  unchanged. Time zones: Olson IDs, Windows names through ICU, else the `VTIMEZONE`'s offset; new
  events get a `VTIMEZONE` built from the zone's rules.
- **Calendar colours (any calendar, synced or not):** a Colour item per calendar in the app's
  calendar list opens a picker: the palette, hue/saturation/value sliders, and a hex code field
  (`#RRGGBB`). For Tessera-synced calendars the colour is also saved to the server
  (`calendar-color`), so other devices follow; for local calendars it's stored on the phone; for
  another account's calendars (DAVx5, Google) it's set on the phone, and that account's own sync
  may put it back.
- New calendars made in the app can go to the server (`MKCALENDAR`) or stay on the phone.
- **Moving from DAVx5:** a calendar synced by both shows every event twice. The calendar list marks
  a server calendar that DAVx5 also syncs (same URL) and says to turn it off there.

## Contacts

- Each server address book becomes Android contacts under the account (one Android group set per
  address book).
- **vCard:** reads 3.0 and 4.0; writes 3.0 (what DAVx5 writes; every server accepts it). Mapped:
  name parts, nickname, phones, emails, addresses, organisation and title, notes, websites,
  birthday and anniversary, relations, instant messaging, SIP, photo. Everything else is kept.
- **Contact group method** (per account, as DAVx5):
  - **Groups as separate vCards** (default): each group is its own vCard (`KIND:group`, or
    Apple's `X-ADDRESSBOOKSERVER-KIND:group` in 3.0) listing members by UID (`MEMBER` /
    `X-ADDRESSBOOKSERVER-MEMBER`). What iOS, macOS and Nextcloud use.
  - **Groups as per-contact categories**: each contact's `CATEGORIES` lists its groups; there are
    no group vCards.
  - Changing the method re-reads the address book from the server after uploading local changes,
    as DAVx5 does.
- Tessera Contacts doesn't show groups yet; they sync both ways and other apps see them.

## Tasks

The same Sync settings (interval, immediately on local changes, Wi-Fi only) replace the fixed
15 minutes.

## Engine (pure Kotlin, unit tested)

iCalendar and vCard parsing and writing (with unknown properties kept), the VEVENT to event-row
mapping both ways, time zone resolution, `VTIMEZONE` generation, the vCard to contact-row mapping
both ways, both group methods, discovery and multistatus parsing, sync planning (what to fetch,
upload, delete) and the interval choices. Sync runs against a real Radicale in a test, as Tasks'
does.

## Order

1. `core:dav`, Tasks moved onto it, Tasks' sync settings.
2. Calendar sync and calendar colours.
3. Contacts sync with both group methods.

Each is its own PR and release.
