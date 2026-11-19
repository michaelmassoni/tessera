# Keyboard: spec

Package `io.github.michaelmassoni.tessera.keyboard`. Status: v0.1, first implementation.

## Goals

A private, offline keyboard. It has no network permission, so typed text can never leave the
device. Its look and feel match the system keyboard: Material keys in theme colours, light and
dark, and it follows dynamic colour.

## Setup

The app's main screen walks through two steps:

1. Turn on Tessera Keyboard in system settings (`ACTION_INPUT_METHOD_SETTINGS`).
2. Switch to it with the system input method picker.

Each step shows a tick once it's done. A test field lets you try the keyboard straight away.

## Permissions

None. In particular there's no `INTERNET`.

## Layouts

- **Letters:** QWERTY in three rows, plus a bottom row: `?123`, comma, space, full stop and the
  action key. Shift sits left of Z and backspace right of M.
- **Number row (setting, off by default):** a row of digits above the letters. When it's off,
  long-pressing Q–P types 1–0.
- **Symbols:** two pages, `?123` and `=\<`, with the usual punctuation and currency.
- **Numbers and phone:** fields for numbers, phone numbers and PINs get a 3×4 keypad.
  - Phone fields add `+ * #`.
  - Decimal fields add `.` and `-`.
- **Accents:** long-press a letter to get its alternatives in a strip above the keys, for
  example `e` gives é è ê ë ē. Lift on one to type it.

## Behaviour

- **Shift:**
  - Tap for one capital letter.
  - Double-tap for caps lock.
  - Tap again to turn it off.
- **Auto-capitalisation:** follows the field's caps mode (`getCursorCapsMode`). Sentences start
  with a capital where the app asks for it, and never in password, email or URL fields.
- **Backspace:**
  - Hold it to repeat, speeding up after half a second.
  - A selection is deleted in one press.
- **Action key:** shows the field's action (Go, Search, Send, Next, Previous or Done) as an icon
  and sends it. In multi-line fields without an action it types a newline.
- **Space:** a double space types ". " (setting, on by default).
- **Feedback:** each key press vibrates briefly (setting, on by default).

## Settings

- Number row.
- Key vibration.
- Double-space full stop.
- Shared theme and app icon from `core:settings`.

## Engine (pure Kotlin, unit tested)

- `Layouts`: key rows for each layout as data.
- `Shift`: the off, once and locked state machine, including double-tap.
- `Accents`: alternative characters per letter.
- `ActionKey`: maps `EditorInfo.imeOptions` (plain ints) to an action and whether Enter types a
  newline.

## Out of scope for v0.1

- Word suggestions and autocorrect (no dictionary yet).
- Glide typing.
- Emoji.
- Voice input.
- Clipboard history.
- Other languages and layouts (AZERTY, QWERTZ and so on).
- One-handed mode.
- Floating keyboard.
