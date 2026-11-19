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
| `SET_WALLPAPER` | "Set as wallpaper" in the viewer (a normal permission, granted at install) |

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
  - Grid cells and album covers use MediaStore's cached thumbnails (`loadThumbnail`, through a
    Coil fetcher) instead of decoding the original or a video frame. On the emulator, scrolling 78
    items (72 photos of 12 MP, 6 videos) read 4 MB instead of over 300 MB, with about half the app
    CPU time. The viewer still loads the full item.- **Selection:** long-press starts selection; tapping a day heading selects that day. The
  contextual bar offers Share, Favourite and Move to trash.

### Albums

- **Folders:** one album per folder (MediaStore bucket), ordered by most recent item, with a
  cover and a count. Camera and Screenshots come first when present.
- **Collections:** Favourites, Videos and Trash. Trash lists trashed items with the days left
  before the system deletes them, and lets you restore them or delete them forever.

### Viewer

- **Navigation:** full screen, swipe between items in the current timeline or album (swipe left for older, like the grid reads), and tap
  to show or hide the controls (system bars hide with them).
- **Images:** pinch and double-tap zoom with subsampling for large images (Telephoto).
- **Videos:** play inline with Media3 ExoPlayer (play/pause and a seek slider). Playback pauses
  when you swipe away or leave the app.
- **Order:** the viewer walks items in exactly the grid's order (`MediaOrder`: date taken, or date
  added when there's none, then id). MediaStore can't sort by that fallback itself, so the list is
  sorted in Kotlin.
- **Actions:** Share, Favourite, Edit, Set as wallpaper, Info and Move to trash.
  - **Edit** (photos) opens the built-in editor (see Editor below). Its menu still offers
    **Open in another app** (`ACTION_EDIT` with read access only, so other editors save a copy),
    shown only when one is installed.
  - **Set as wallpaper** (photos only) asks Home screen, Lock screen or both, and sets it directly
    with `WallpaperManager`, so it works without a wallpaper picker app.
- **Rotation and wallpaper changes** keep the open album and photo.
- **Info:** a sheet with the date taken, file name, size, resolution, folder, and camera details
  from EXIF when present.

### Editor

For photos (JPEG, PNG, WebP, HEIC). Videos aren't edited.

- **Crop and rotate:** drag the corners or edges of the crop frame, or pick a ratio: Free,
  Original, Square, 4:3, 3:2, 16:9 (each turns with the photo). Rotate 90° and Flip. A
  **Straighten** dial from −45° to 45° zooms in just enough that no empty corners show.
- **Adjust:** Brightness, Contrast, Saturation and Warmth sliders, from −100 to 100.
- **Auto:** one tap sets brightness and contrast from the photo's histogram (stretching the
  darkest and brightest 0.5% to black and white), and a little saturation. It's done on the
  device, and the sliders show what it chose.
- **Undo, Redo and Reset.** In Adjust, press and hold the photo to compare with it unadjusted.
- **Save as copy** (the default) writes a new JPEG (quality 95) next to the original, in the same
  folder with the same date taken, so it sits beside it in the timeline. **Save** replaces the
  original, after the system asks (`MediaStore.createWriteRequest`). Leaving with changes asks
  whether to discard them.
- The preview is drawn from a downscaled copy (at most 2048 px) with the adjustments as a colour
  matrix, so dragging stays smooth. Saving decodes the full photo and applies the same steps.
- EXIF orientation is applied when the photo is opened, and the saved copy is upright with
  orientation "normal". Date taken, camera details and GPS are copied to it.

### Other apps

- **View:** `ACTION_VIEW` for `image/*` and `video/*` opens the viewer for that single item.
- **Pick:** `ACTION_GET_CONTENT` and `ACTION_PICK` for `image/*` and `video/*` let other apps
  pick from the grid, one item or several (`EXTRA_ALLOW_MULTIPLE`).

## Engine (pure Kotlin, unit tested)

- `Timeline`: groups items into day sections with locale-aware relative labels.
- `Albums`: builds the album list from items (counts, cover, ordering, pinned folders).
- `TrashInfo`: days remaining from `DATE_EXPIRES`.
- `Edit`: the editor's state and history, crop frames and ratios, the straighten zoom, the
  colour matrix for the adjustments, and auto enhance from a histogram.
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

- Search.
- Moving between albums.
- Creating albums.
- Hidden or locked folders.
- Map and people views.
- Slideshows.
- Cloud.
