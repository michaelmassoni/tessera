# Gallery: spec

Package `io.github.michaelmassoni.tessera.gallery`. Status: v0.1, first implementation.

## Goals

A fast, offline photo and video gallery over the device's MediaStore. It never uploads anything
and needs no account.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` | Show photos and videos (Android 13+) |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ partial access: works with "Allow limited access" too |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 32) | The same on Android 12 |

- **Limited access:** the grid shows a banner to choose more photos or allow all.
- **Changes need confirmation:** trash, restore, delete and favourite go through MediaStore's
  `createTrashRequest`, `createDeleteRequest` and `createFavoriteRequest`, so the system asks the
  user to confirm. No broad write permission is requested.

## Features

### Photos

- **Timeline:** every image and video, newest first, in a grid grouped by day ("Today",
  "Yesterday", weekday names this week, then dates).
  - The columns adapt to the window width: 3 on a phone, more on larger screens.
  - Videos show their duration and favourites show a heart.
- **Selection:** long-press starts selection; tapping a day heading selects that day. The
  contextual bar offers Share, Favourite and Move to trash.

### Albums

- **Folders:** one album per folder (MediaStore bucket), ordered by most recent item, with a
  cover and a count. Camera and Screenshots come first when present.
- **Collections:** Favourites, Videos and Trash. Trash lists trashed items with the days left
  before the system deletes them, and lets you restore them or delete them forever.

### Viewer

- **Navigation:** full screen, swipe between items in the current timeline or album (swipe right for older), and tap
  to show or hide the controls (system bars hide with them).
- **Images:** pinch and double-tap zoom with subsampling for large images (Telephoto).
- **Videos:** play inline with Media3 ExoPlayer (play/pause and a seek slider). Playback pauses
  when you swipe away or leave the app.
- **Actions:** Share, Favourite, Edit (`ACTION_EDIT` to an installed editor), Use as
  (`ACTION_ATTACH_DATA`), Info and Move to trash.
- **Info:** a sheet with the date taken, file name, size, resolution, folder, and camera details
  from EXIF when present.

### Other apps

- **View:** `ACTION_VIEW` for `image/*` and `video/*` opens the viewer for that single item.
- **Pick:** `ACTION_GET_CONTENT` and `ACTION_PICK` for `image/*` and `video/*` let other apps
  pick from the grid, one item or several (`EXTRA_ALLOW_MULTIPLE`).

## Engine (pure Kotlin, unit tested)

- `Timeline`: groups items into day sections with locale-aware relative labels.
- `Albums`: builds the album list from items (counts, cover, ordering, pinned folders).
- `TrashInfo`: days remaining from `DATE_EXPIRES`.

## Libraries

All open source and F-Droid compatible:

- Coil 3: image and video thumbnails.
- Telephoto: zoomable, subsampled images.
- Media3 ExoPlayer and media3-ui-compose: video playback.

## Layout

| Window | Layout |
|--------|--------|
| Compact | Bottom navigation (Photos, Albums); 3–4 grid columns |
| Medium/Expanded | Navigation rail; more columns |

The viewer is always full-screen.

## Accessibility

- Grid cells have content descriptions: "Photo" or "Video, 0:42", plus the date.
- Selection state is announced.
- Every viewer action has a label.
- Video controls are reachable without gestures.

## Out of scope for v0.1

- An editor.
- Search.
- Moving between albums.
- Creating albums.
- Hidden or locked folders.
- Map and people views.
- Slideshows.
- Cloud.
