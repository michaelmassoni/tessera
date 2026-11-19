# Notes: spec

Package `io.github.michaelmassoni.tessera.notes`. Status: v0.1, first implementation.

## Goals

A fast, private notebook that feels first-party. You can jot something down in two taps, find it
again, and it never leaves the device unless you share or export it. It needs no permissions
and no network.

## Features

### Notes list

- **Layout:** a staggered grid of note cards. Each card shows the title, up to 8 lines of the
  body (or the first checklist items with their boxes) and labels. One column below 600dp, two
  at medium widths, and more on expanded widths.
- **Pinned notes:** shown first, under a "Pinned" heading. The rest follow under "Others",
  newest edit first.
- **Search:** matches title and body, ignoring case and accents. Results update as you type.
- **Labels:** a note can have any number of labels. Each label is a view in the navigation
  drawer.
- **Archive and Trash:** separate views, chosen from a navigation drawer that also lists labels
  and Settings.
  - Archived notes leave the main list but stay searchable.
  - Deleted notes go to Trash and are removed permanently after 7 days. They can be restored or
    deleted for good from there.
- **New note:** a FAB creates a text note. A second, smaller FAB creates a checklist.
- **Selection:** long-press starts selection mode. The contextual top bar shows pin, archive and
  delete as icons, and select all, labels and share in its menu, so the count stays on one line
  at phone width.

### Editor

- **Fields:** a title and a body, in plain text.
- **Checklist mode:** each line becomes an item with a checkbox.
  - Enter adds an item, and Backspace on an empty item removes it.
  - Checked items move to a collapsible "Checked items" group at the bottom.
  - Converting between text and checklist keeps the lines.
- **Saving:** changes save automatically. They're debounced while typing and flushed when
  leaving the editor or the app. An empty new note is discarded.
- **Undo and redo:** in the editor's top bar, per editing session.
- **Actions:** pin, archive, labels, share (`ACTION_SEND` text), make a copy, and delete (to
  Trash). Each delete shows a snackbar with Undo.
- **Timestamps:** "Edited <time>" at the bottom.

### Other apps

- **Receive text:** `ACTION_SEND` with `text/plain` from other apps creates a new note. The
  subject becomes the title and the text the body.
- **Shortcuts:** "New note" and "New checklist" app shortcuts (static shortcuts.xml).

### Backup

- **Export:** all notes to a single JSON file (`tessera-notes-<date>.json`) with the system
  file picker (`ACTION_CREATE_DOCUMENT`).
- **Import:** from the same format (`ACTION_OPEN_DOCUMENT`). Imported notes get new IDs, so
  importing twice duplicates instead of overwriting.
- No storage permission is needed.

## Data

- Each note is one JSON file in app-private storage (`files/notes/<id>.json`), written
  atomically (temp file + rename). Notes are loaded into memory at start. Thousands of notes
  load in well under a second.
- **Model:** `Note(id, title, body, checklist: List<Item>?, labels, pinned, archived,
  trashedAt?, created, modified)`.
- Android Auto Backup is disabled (`data_extraction_rules`). Export is the backup path, so notes
  never go to a cloud without the user choosing to.

## Engine (pure Kotlin, unit tested)

- `NoteRepository`: load, save, delete, and purging Trash after 7 days.
- `NoteQuery`: filtering by view, label and search, then sorting (pinned first, then by
  modified time).
- `Checklist`: converting text to and from checklist items, and the editing operations (split
  on Enter, merge on Backspace, toggle and reorder).
- `UndoStack`: coalesces typing into undo steps.
- `Backup`: encodes and decodes the export format, with a version field.

## Layout

| Window | Layout |
|--------|--------|
| Compact | List, with the editor full-screen on top (back returns to the list) |
| Medium | Same, two-column grid |
| Expanded | List and editor side by side; the list pane is 360–420dp |

## Accessibility

- Cards read as "title, body preview, pinned, labels".
- Checklist items are checkboxes with their text as the label.
- The FABs and every icon have labels.
- Every action is reachable without long-press (the editor's menu).

## Permissions

None.

## Settings

- **Notes:**
  - Move checked items to the bottom (on by default).
  - Export and import (see Backup).
- **Shared:** theme and app icon (from `core:settings`).

## Out of scope for v0.1

- Rich text and Markdown rendering.
- Tappable links inside notes.
- Images, drawings and voice notes.
- Reminders.
- Note colours.
- Sync.
- Widgets.
- Locking notes.
