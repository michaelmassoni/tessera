# First-run setup

Every Tessera app except Calculator shows a short setup the first time it opens. It's shared
code in `core:settings` (`Setup.kt`: `SetupSpec`, `SetupGate`), so every app's setup looks and
behaves the same.

## The flow

1. **Welcome:** the app's icon and name, and "Everything stays on this phone".
2. **One page per permission or role** the app asks for, saying what it's for, with Allow.
   - Steps already granted are skipped, including ones granted along the way: becoming the
     default phone app brings call, contacts and notification permissions with it, so Phone
     goes straight to restore.
   - Optional steps have "Not now". A required step has no way past it until it's refused
     once; then "Continue without it" appears, because the app's own "needs access" screen
     takes over from there.
   - Refused twice, Android stops asking: the page says so and offers the app's settings.
     Coming back with it allowed moves setup on.
   - Access granted in system Settings (Files' all-files access, Clock's full-screen alarms)
     opens that settings page; coming back with it on moves setup on.
3. **Restore from a backup** (apps with data of their own): choose a backup file, which can be
   this app's or a "Back up all Tessera apps" file, or start fresh. Restoring all apps from
   one app's setup also marks the others as restored, so their setup doesn't offer it again.
   After a restore the app restarts into its main screen.

Setup runs **once, on a fresh install only**. An app that was installed before setup existed
and then updated skips it. Whether setup has run is kept in `noBackupFilesDir`, so restoring onto
a new phone still asks for that phone's permissions. Back goes to the previous page; leaving
setup partway runs it again next time. Gallery skips setup when another app opens a single photo
in it.

After setup, an app missing a permission it can't work without shows its "needs access"
screen (Allow, or the app's settings). Phone and Messages now use the shared
`NeedsAccessScreen` instead of asking for permissions as soon as they open.

## By app

| App | Steps (optional in *italics*) | Restore offered |
|---|---|---|
| Calculator | No setup | |
| Notes | Welcome only | Yes |
| Clock | Notifications; *Alarms over the lock screen* (Android 14+, when not already allowed) | Yes |
| Phone | *Default phone app*; Calls and contacts; *Notifications* | Yes |
| Messages | *Default SMS app*; Texts and contacts; *Notifications* | Yes |
| Contacts | Your contacts | No (contacts live in the phone's accounts) |
| Calendar | Your calendars; *Notifications* (reminders) | No (settings only) |
| Gallery | Your photos and videos (all, or selected ones) | No |
| Music | Where the music is (this phone or a server); then Your music (phone) or Sign in to your server | Yes |
| Files | All files access | No |
| Weather | Welcome only; Weather's own "use my location or add a place" follows | Yes |
| Sports | Welcome only; the team picker follows | Yes |

Optional features that are off by default (Weather's notifications, Sports' spoiler-free mode)
aren't part of setup; they stay in each app's settings, which ask for any permission they need.
