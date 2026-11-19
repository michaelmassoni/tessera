# Keyboard: spec

Package `io.github.michaelmassoni.tessera.keyboard`. Status: v0.2, adds suggestions and autocorrect.

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
- **Auto-capitalisation (setting, on by default):** sentences start with a capital in every
  ordinary text field, even when the app doesn't ask for it (many don't). The field's own caps
  flags (words, characters) always apply. Never in password, email or URL fields.
- **Backspace:**
  - Hold it to repeat, speeding up after half a second.
  - A selection is deleted in one press.
- **Action key:** shows the field's action (Go, Search, Send, Next, Previous or Done) as an icon
  and sends it. In multi-line fields without an action it types a newline.
- **Space:** a double space types ". " (setting, on by default).
- **Feedback:** each key press vibrates briefly (setting, on by default).

## Suggestions and autocorrect

All offline: an English dictionary ships in the app and learned words stay on the device.

- **Dictionary:** about 130,000 words from SCOWL (English, American, British and Australian
  spellings, contractions, capitalised names), ranked by frequency from FrequencyWords' subtitle
  counts. Built by `scripts/keyboard-dictionary.sh`; credits in `assets/en.dict.NOTICE` and on
  the settings screen.
- **Strip:** three slots above the keys.
  - Unknown word with a correction: the typed word in quotes (keep it), the correction in bold
    (what space will use), and another option.
  - Known word: two alternatives either side of it.
  - Tapping a slot replaces the word and adds a space. Punctuation typed next takes that space
    ("hello.").
- **Matching:** words match by their key (lower case, no apostrophes), so "dont" finds
  "don't" and "sydney" finds "Sydney". Typos use a weighted Damerau–Levenshtein distance where
  neighbouring keys, swapped letters and a missing or extra doubled letter cost less. Only words
  starting with the typed first letter, a neighbour of it, or the second letter are checked.
- **Autocorrect (setting):** on space, punctuation or a newline, an unknown word becomes the best
  candidate if it's common enough. Case and apostrophe fixes always apply ("im" → "I'm").
  "cant" and "wont" become "can't" and "won't".
  - Delete straight after a correction (or the Undo slot) puts the typed word back and learns it.
  - Picking the quoted word also learns it. Learned words are never corrected; settings can clear
    them.
  - Not in names, passwords, emails, URLs or fields with `TYPE_TEXT_FLAG_NO_SUGGESTIONS`.
  - Nothing is learned in fields with `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito).
- **Offensive words (setting, on):** never suggested unless typed in full.
- **Where it runs:** the strip is computed off the main thread; space reuses its answer.

## Settings

- Number row.
- Key vibration.
- Double-space full stop.
- Auto-capitalisation.
- Show suggestion strip, autocorrect, block offensive words, forget learned words.
- Shared theme and app icon from `core:settings`.

## Engine (pure Kotlin, unit tested)

- `Layouts`: key rows for each layout as data.
- `Shift`: the off, once and locked state machine, including double-tap.
- `Accents`: alternative characters per letter.
- `Dictionary`: the word list, key lookup, completions and the typo distance.
- `Suggester`: autocorrect decisions, the strip, capitalisation of suggestions.
- `ActionKey`: maps `EditorInfo.imeOptions` (plain ints) to an action and whether Enter types a
  newline.

## Out of scope for v0.1

- Next-word prediction.
- Dictionaries for other languages.
- Glide typing.
- Emoji.
- Voice input.
- Clipboard history.
- Other languages and layouts (AZERTY, QWERTZ and so on).
- One-handed mode.
- Floating keyboard.
