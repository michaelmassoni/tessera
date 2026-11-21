# Files: spec

Package `io.github.michaelmassoni.tessera.files`. Status: v0.1, first implementation.

## Goals

A fast, predictable file manager that feels first-party: browse every storage volume, open and
share files, and copy, move, rename and delete them safely. It's offline and private. The only
broad permission is all-files access, and it's used only to read and write files the user
browses to.

## Storage access (and why all-files access)

**Decision (see `docs/decisions.md`):** Files asks for **All files access**
(`MANAGE_EXTERNAL_STORAGE`) on first launch.

Why the Storage Access Framework (SAF) and scoped storage aren't enough for a general file
manager on Android 11+:

- SAF tree pickers can't grant the root of internal storage or the `Download` folder root. Every
  other folder needs its own grant, one picker trip at a time.
- SAF access is slow for large folders, because every listing is an IPC call per document. It
  also hides ordinary file paths, which users need to see.
- MediaStore only covers media files and documents in standard collections. It can't show
  arbitrary folders, hidden files or non-media files.

What all-files access does **not** give, and how the app behaves there:

- **`Android/data` and `Android/obb`** are blocked by the OS on Android 11+. The app shows these
  folders but explains that Android restricts them, and offers no workaround.
- **Other apps' private storage** is never accessible.

**First-run flow:**

1. The first screen explains what the permission is for. Its button opens
   `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
2. The status is re-checked on resume.
3. Until access is granted, the explanation stays on screen. No file is listed or touched without
   it.

The app never uploads, indexes in the background, or scans storage when nobody is looking at it.

## Features

### Home

- **Storage volumes:** internal storage and any SD card or USB drive (`StorageManager`), each
  with a used/total bar and the free space.
- **Standard folders:** shortcuts to Download, DCIM, Pictures, Documents, Music and Movies.

### Browsing

- **Listing:** folders first, then files. Each item shows an icon by type (folder, image, video,
  audio, document, PDF, archive, APK or other). Images and videos get thumbnails, loaded off the
  main thread and cached.
- **Breadcrumbs:** a scrollable bar of path segments, each one tappable.
- **Views:** list or grid. On wide windows the grid gets more columns.
- **Sorting:** by name (natural order, so "file2" comes before "file10"), date modified, size or
  type, ascending or descending.
- **Hidden files:** an option to show files that start with ".".
- **Remembered settings:** sort order, view mode and the hidden-files option persist.
- **Back:** goes up one folder, and from a top-level folder returns home. It supports predictive
  back.
- **Pull to refresh:** the folder also refreshes automatically on resume.

### File actions

- **Open:** `ACTION_VIEW` through `FileProvider`, with the MIME type from the extension. If no
  app can open the file, a message says so. APKs go to the system package installer, which needs
  `REQUEST_INSTALL_PACKAGES` and only acts when the user taps the file.
- **Share:** one or several files (`ACTION_SEND` or `ACTION_SEND_MULTIPLE`).
- **Selection:** long-press starts selection mode, which has a contextual top bar showing the
  count, select all, share, copy, move, delete and more. Rename and details are available for a
  single item.
- **Copy and move:** choose Copy or Move, browse to the destination, then press "Paste here" in
  the bottom bar (Cancel is next to it).
  - A file with the same name gets a new one, "name (1).ext", and is never silently overwritten.
  - Moving or copying a folder into itself or one of its subfolders is refused.
  - Moves within one volume are renames, which are instant. Across volumes, a move is a copy
    followed by a delete, and the source is deleted only after every file copied successfully.
  - Free space is checked before copying.
  - Progress shows the file count and bytes, and the operation can be cancelled. A cancelled or
    failed copy removes its partial file.
- **Delete moves to the trash:** a dialog ("Move 3 items to the trash?") confirms it, then a
  snackbar offers Undo. Each item is renamed into a hidden `.Trash-tessera` folder at the root
  of its own volume (instant, no extra space; folders go whole) with a `.nomedia` file so
  galleries skip it, and recorded in `trash.json` in the app's private storage.
- **Trash (home screen):** trashed items, newest first, with size and days left. Tap to select;
  Restore puts items back where they were (with "name (1)" if that name has been reused), Delete
  removes them for good, and Empty trash removes everything, each after a confirmation. Items
  are deleted for good after 30 days, checked when the app starts and when Trash opens.
- **Rename:** the base name is pre-selected, not the extension. Names containing `/` or a NUL
  character, empty names, `.` and `..` are rejected. Clashing with an existing name shows an error.
- **New folder:** uses the same name checks as rename.
- **Details:** name, path, type, size (a folder's size and item count are calculated in the
  background), and the date modified.
- **Search:** searches the current folder and its subfolders by name, case- and
  accent-insensitively. It starts 250 ms after typing pauses, not on every key. Results stream in as they're found. The search stops at 1,000 results
  and skips `Android/data` and `Android/obb`.

## Engine (`FileOperations`, pure Kotlin on `java.io`/`java.nio.file`, unit tested)

- `copy(sources, targetDir)` and `move(sources, targetDir)`, with progress callbacks and
  cancellation (they check a cancellation flag between buffers).
- `delete(paths)`, recursive.
- `Trash`: `move`, `restore`, `delete`, `empty` and `purge`, on real files (unit tested).
- `uniqueName(dir, name)` produces "a.txt", then "a (1).txt", "a (2).txt", and so on. For a
  folder it produces "dir (1)". Compound extensions stay together: "x.tar.gz" becomes
  "x (1).tar.gz".
- `validateName(name)`.
- `isInside(child, parent)` prevents copying a folder into itself.
- Symbolic links are copied as links and never followed when deleting, so deleting a link never
  deletes its target.

## Layout

| Window | Layout |
|--------|--------|
| Compact | One pane, list by default |
| Medium | One pane, grid with 3–4 columns |
| Expanded | Home sidebar (volumes and standard folders) next to the browser |

## Accessibility

- Each item has a spoken label: name, type, size and date.
- Selection state is announced ("selected").
- Grid and list items have touch targets of at least 48dp.
- Every action is reachable without long-press: each item has an overflow menu.
- The breadcrumb bar is a list of buttons with clear labels.

## Permissions

| Permission | Why |
|------------|-----|
| `MANAGE_EXTERNAL_STORAGE` | Browse and manage all shared storage (see above) |
| `REQUEST_INSTALL_PACKAGES` | Let the user install an APK they tap; the system installer still asks for confirmation |

No network or media permissions are requested; all-files access already covers media files.

## Out of scope for v0.1

- Background (foreground-service) transfers that continue after leaving the app.
- Archives (zip and others).
- Network and cloud storage.
- Root access.
- Media categories view.
- Duplicate finder.
- Acting as a SAF `DocumentsProvider`.
- Tabs.
