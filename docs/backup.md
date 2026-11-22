# Backup and restore

Every Tessera app can save its data to a file and restore it, and any of them can do it for all
the Tessera apps at once. There's no cloud: files go wherever the person saves them (the
Storage Access Framework), such as Downloads, a USB stick or a sync folder.

## Where

Settings → Backup, in every app (the shared settings screen in `core:settings`):

- **Back up**: this app's data to "Tessera Notes 2026-11-22.zip".
- **Restore**: from such a file, after a confirmation that names the app and the backup's date.
  The app then restarts.
- **Back up all Tessera apps**: one file, "Tessera 2026-11-22.zip", with every installed Tessera
  app signed like this one. A snackbar says how many were backed up.
- **Restore all Tessera apps**: lists the installed apps the file has data for, then restores
  each (the others first; each restarts itself), and this one last.

## What's in a backup

An app's private files and settings, in normal and device-protected storage (Clock keeps alarms
in the latter): `files/`, `prefs/` (shared preferences), `de_files/`, `de_prefs/`. Left out:
temporary files, caches and library state (Glance widget state, WorkManager, profile installer),
and per app, through `<meta-data android:name="io.github.michaelmassoni.tessera.BACKUP_EXCLUDE">`:

- Weather: cached forecasts (they refresh).
- Music: the queue (content URIs that only mean something on this phone).
- Files: the trash index (paths on this phone; trashed files themselves aren't backed up).

What isn't Tessera's own data isn't included: contacts, calendar events, messages and the call
log live in Android's providers (Contacts can export vCards and Calendar can import ICS), and
photos, music and files are the person's files already.

Music playlists refer to songs by MediaStore id, which differs on another phone; restored there,
playlists keep their names but may lose songs.

## Format

A zip (`BackupArchive`, unit tested): a `manifest` first (properties: app package, label,
versionCode, creation time, format 1), then the files under their root names. A suite backup is
a zip of app backups (`apps/<package>.zip`) after its own manifest (app "suite").

## Restoring safely

- The manifest is checked first: another app's backup, or one from a newer version of this app
  (its data may not read), is refused with a message, and nothing changes.
- The backup is unpacked into a staging folder first; only once all of it has arrived are the
  app's files replaced. Entries whose paths would leave their folder are refused.
- The app restarts afterwards so it reads the restored data. Apps that keep system state set it
  up again: `AppBackup.consumeRestored()` is true once after a restore, and Clock then puts its
  alarms back into AlarmManager.

## Between apps

"All Tessera apps" uses the settings provider every app already exports for theme sync, with the
same check: only callers signed with the same certificate are served. `openFile("backup")`
streams the app's backup out through a pipe; `openFile("restore")` takes one in, and
`call("restore_status")` reports when it's done (then that app restarts). Apps signed differently
(a GitHub build beside an F-Droid build) or on versions from before backups are skipped.
