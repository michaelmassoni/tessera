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
  - **Reorder:** drag an item by its handle (left of the checkbox). The row follows the finger,
    the rows it passes slide aside, and the move is one undo step when the finger lifts.
    Unchecked and checked items reorder within their own group. TalkBack gets Move up and Move
    down actions on the handle.
  - Items sit close together like a paper list: each row is the checkbox's 48dp touch target,
    growing only when an item wraps (a plain text field, not a Material `TextField` with its 56dp
    minimum and padding).
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
- **Widget (Glance, 3×3, resizable):** pinned notes first, then the most recently edited (up to
  20), each with its title and first lines or open checklist items. Tapping one opens it; the
  header has New checklist and New note. `NotesApplication` redraws it a second after notes
  stop changing.

### Backup

- **Export:** all notes to a single JSON file (`tessera-notes-<date>.json`) with the system
  file picker (`ACTION_CREATE_DOCUMENT`).
- **Import:** from the same format (`ACTION_OPEN_DOCUMENT`). Imported notes get new IDs, so
  importing twice duplicates instead of overwriting.
- No storage permission is needed.

## Colours, links and reminders (0.5)

- **Colours:** Colour in the editor's menu: Default or one of ten (red, orange, yellow, green,
  teal, blue, purple, pink, brown, grey). The note's card and editor are tinted with it: the
  colour's hue blended into the theme's surface (22% light, 30% dark), so it follows light, dark
  and dynamic colour and the text keeps the theme's colours (docs/decisions.md). Each swatch is
  named for TalkBack.
- **Links:** web addresses, email addresses and phone numbers in a text note (`Links`, unit
  tested). When the cursor is in one, a chip under the text offers Open, Email or Call (as in
  Keep): the text field can't style or link text without the deprecated `VisualTransformation`.
  Not in checklist items yet.
- **Reminders:** Remind me in the menu: a date, then a time (default: the next whole hour). The
  reminder shows as a chip under the note (tap to change, × to remove) and on its card; one that
  has gone off is struck through. At the time, a notification with the note's title and text,
  Open (tap) and Done (clears the reminder). `ReminderPlan` (unit tested) decides: one alarm for
  the next reminder; a time already past when set doesn't remind; one missed while the phone was
  off reminds afterwards, up to a day late. `NotesApplication` sets the next alarm whenever a
  note's reminder changes (set, moved, trashed, deleted, imported); a restart, app update or time
  change shows missed reminders and sets the next.

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

Only for reminders (0.5); a note without a reminder needs none of them.

- `POST_NOTIFICATIONS`: the reminder itself. Asked for the first time a reminder is set.
- `SCHEDULE_EXACT_ALARM`: so a reminder goes off on time. Not granted by default on current
  Android; until it's allowed, the reminder dialog says reminders may be a few minutes late and
  links to the setting (the alarm is then inexact, `setAndAllowWhileIdle`).
- `RECEIVE_BOOT_COMPLETED`: to set the next alarm again after a restart.

## Settings

- **Notes:**
  - Move checked items to the bottom (on by default).
  - Export and import (see Backup).
- **Shared:** theme and app icon (from `core:settings`).

## Out of scope for v0.1

- Rich text and Markdown rendering.
- Images, drawings and voice notes.
- Sync.
- Locking notes.

## First run

See [setup.md](setup.md) for what notes asks for the first time it opens.
