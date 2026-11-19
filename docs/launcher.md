# Launcher: spec

Package `io.github.michaelmassoni.tessera.launcher`. Status: v0.1, first implementation.

## Goals

A calm, fast home screen in the style of the Pixel launcher: your wallpaper, a clock, the apps
you pin, and an app drawer with search. It shows work-profile apps with the system badge.

## How it becomes the home app

The main activity handles `MAIN` with categories `HOME` and `DEFAULT`. The user picks Tessera
Launcher under Settings > Apps > Default apps > Home app. The launcher also offers a button for
this when it isn't the default yet (`RoleManager.ROLE_HOME`).

## Permissions

None. Listing apps uses `LauncherApps`, which is allowed for the home app. The `<queries>`
element declares the `MAIN`/`LAUNCHER` intent so package visibility works.

## Home screen

- **Wallpaper:** the theme sets `windowShowWallpaper` and the window is transparent.
- **At a glance:** the time (large) and the date at the top. Tapping the clock opens the clock
  app (`AlarmClock.ACTION_SHOW_ALARMS`) and tapping the date opens the calendar.
- **Pinned apps:** up to 20, in a grid of 4 or 5 columns depending on width. Long-press an app
  to unpin it or move it left or right.
- **Search pill:** at the bottom, it opens the drawer with the keyboard up.
- **Opening the drawer:** swipe up anywhere to open it.
- **Back:** does nothing on the home screen, like other home apps.

## App drawer

- **Apps:** every launchable activity for every profile (personal and work). Each shows an
  icon and label, sorted by label with locale collation.
- **Search:** filters as you type, matching the start of any word, ignoring case and accents.
  Pressing Enter opens the first result.
- **Long-press menu:**
  - App shortcuts (static and dynamic, from `LauncherApps.getShortcuts`). These are only
    available while Tessera is the default home app.
  - Pin to home or Unpin.
  - App info.
  - Uninstall (personal profile apps only).
- **Closing:** swiping down from the top of the list, or back.

## Icons

- **Source:** icons come from `LauncherActivityInfo.getBadgedIcon`, so work apps carry the
  system work badge.
- **Themed icons (setting, on by default on Android 13+):**
  - Apps with a monochrome adaptive-icon layer are drawn in theme colours (primary container
    background with the on-container tint), like system themed icons.
  - Apps without one keep their own icon.

## Engine (pure Kotlin, unit tested)

- `AppIndex`: sorting with collation, and word-prefix search with accent folding.
- `Pins`: an ordered set of app keys with a cap of 20. Supports add, remove and move, and
  serialises to a string.
- `AppKey`: `package/activity/userSerial`, stable across restarts.

## Layout

| Window | Home | Drawer |
|--------|------|--------|
| Compact | 4 columns | 4 columns |
| Medium/Expanded | 5–6 columns | 6+ columns |

## Out of scope for v0.1

- Widgets.
- Folders.
- Icon packs.
- Multiple home pages.
- Notification dots.
- Hiding apps.
- The Recents screen, which belongs to the system.
- Gesture navigation customisation.
