# Calculator: spec

Package `io.github.michaelmassoni.tessera.calculator`. Status: v0.1, first implementation.

## Goals

A calculator that feels first-party next to the Pixel calculator. It should be quick to use
with one thumb, give **exact decimal results** (0.1 + 0.2 = 0.3, never 0.30000000000000004),
show a live preview of the result while you type, and keep a history. It needs no permissions
and no network access.

## Features

### Basic mode (always visible)

| Row | Keys |
|-----|------|
| 1 | `AC` · `( )` · `%` · `÷` |
| 2 | `7` `8` `9` · `×` |
| 3 | `4` `5` `6` · `−` |
| 4 | `1` `2` `3` · `+` |
| 5 | `0` · decimal separator · `⌫` · `=` |

- **`( )`** is one smart key, as on the Pixel. It inserts `)` when there are unclosed
  parentheses and the expression ends in something that can be closed (a digit, `)`, a
  constant, `%` or `!`). Otherwise it inserts `(`.
- **`AC`** clears everything. A long press on `⌫` does the same.
- **`⌫`** deletes the last token; a function name such as `sin(` goes in one step.
- **The decimal key** shows the locale's decimal separator (`.` or `,`).

### Scientific mode

The scientific keys are in a panel above the keypad. On compact portrait screens the panel
is collapsed to one row by default and expands with a toggle; it's always fully visible in
landscape and on large screens.

| Key | Normal | With `INV` |
|-----|--------|------------|
| `INV` | toggles inverse functions | |
| `DEG`/`RAD` | angle unit (persisted) | |
| `sin` `cos` `tan` | trig functions | `sin⁻¹` `cos⁻¹` `tan⁻¹` |
| `ln` | natural log | `eˣ` (inserts `e^(`) |
| `log` | log base 10 | `10ˣ` (inserts `10^(`) |
| `√` | square root | `x²` (inserts `^2`) |
| `xʸ` | power `^` | |
| `π` `e` | constants | |
| `!` | factorial | |

### Display

- **Expression line:** what you've entered, with the locale's thousands separators. Long
  expressions shrink the text (auto-size), down to a minimum size, and then scroll
  horizontally so the end stays visible.
- **Preview line:** the live result, shown when the expression contains an operation and
  evaluates successfully. Nothing is shown for incomplete input such as `5+`.
- **`=`** moves the result onto the expression line. The next digit starts a new
  calculation, and the next operator continues from the result.
- **Errors** appear in the preview line, using `colorScheme.error`, only after `=`:
  "Can't divide by 0", "Domain error", "Number too large" and "Bad expression". While you're
  typing, an error just hides the preview.
- **Copying:** long-press the display to copy the result, or the expression if there isn't
  one. The copy uses the full precision, without grouping separators.

### History

- Every successful `=` adds an entry (expression, result and time) to the top of the history.
  Up to 100 entries are kept.
- History is stored in the app's private storage and is included in backups.
- **Compact screens:** a history button in the top bar opens a modal bottom sheet, which
  supports predictive back. **Expanded width** (≥ 840dp): history is a permanent side pane.
- Tap an entry to insert its result. Long-press an entry to insert its expression. "Clear
  history" asks for confirmation.

## Number handling (evaluator)

- **Arbitrary-precision decimals:** values are `BigDecimal`, calculated with 60 significant
  digits internally and rounded to 32 significant digits for the final result. Addition,
  subtraction and multiplication of finite decimals are exact.
- **Transcendental functions** (`sin`, `cos`, `tan` and their inverses, `ln`, `log`, `exp`,
  non-integer powers and `√`) are computed with series and Newton iteration at 60 digits, not
  through `Double`.
- **Snapping to exact values:** trig results with magnitude below 1e-45 are reported as exactly
  0, so `sin(π)` = 0. In degree mode, multiples of 90° are handled exactly. `tan(90°)` and
  `tan(π/2)` are a domain error.
- **Display rounding:** results are shown with up to 16 significant digits, without trailing
  zeros. Numbers ≥ 1e16 or < 1e-6 in magnitude use scientific notation (`1.2345E20`).
  Scientific notation can be typed back in, because `=` may place it on the expression line.
- **Limits:** magnitude ≥ 1e10000 is "Number too large". Results smaller than 1e-10000 become 0.
  Factorial accepts integers from 0 up to that limit (0 ≤ n ≤ 3248).

### Grammar and precedence (highest first)

1. Numbers (`12`, `1.5`, `.5`, `5.`, `1.5E-7`), constants `π` and `e`, parentheses, and
   functions `f(…)`.
2. Postfix `!` and `%`.
3. `^`, right-associative: `2^3^2` = 512. The exponent may have a sign: `2^−1`.
4. Prefix `−` and `√`. `−2^2` = −4 and `√2^2` = √4 = 2.
5. `×`, `÷` and implicit multiplication (`2π`, `3(4)`, `(1)(2)`, `2sin(30)`), left to right.
6. `+` and `−`, left to right.

**Percent:** `x%` alone is x/100. When a percentage is the right-hand side of `+` or `−`, it's
a percentage of the left side, as on the Pixel and most handheld calculators: `50+10%` = 55
and `50−10%` = 45. With `×` and `÷` it stays x/100: `50×10%` = 5.

**Unclosed parentheses** are closed automatically when the expression is evaluated:
`(2+3` = 5.

## Input rules (edge cases)

- **Operators on an empty expression:** only `−` (a sign) is accepted. After `=`, an operator
  continues from the result.
- **Two operators in a row:** the second replaces the first, except that `−` after `×`, `÷`
  or `^` is kept as a sign: `5×−3`.
- **Decimal separators:** only one per number. A decimal at the start of a number inserts
  `0.`.
- **Postfix `%` and `!`** need an operand before them; otherwise they're ignored.
- **Leading zeros:** a number that is just `0` is replaced by the next digit (`0` then `7`
  gives `7`, not `07`). `0.0` is still allowed.
- **Length cap:** expressions are limited to 1000 characters, so huge pastes and stuck keys
  stay harmless.
- **Process death:** the current expression survives it (`SavedStateHandle`).

## Screens and layouts

Layouts are chosen from the space available, not from orientation alone:

| Window | Layout |
|--------|--------|
| Compact width, portrait | Display above a collapsible scientific panel and a 4×5 keypad |
| Compact height (phone landscape, < 480dp) | Display above scientific keys (left) and basic keys (right), side by side |
| Medium width (600–839dp, e.g. foldable) | Like portrait, with the scientific panel always expanded |
| Expanded width (≥ 840dp, tablet or desktop) | Calculator on the left, permanent history pane on the right |

Keys are Material 3 Expressive buttons that change shape when pressed. Color roles:

- Digits: `surfaceContainerHighest`.
- Operators: `secondaryContainer`.
- `=`: `primary`.
- `AC`: `tertiaryContainer`.
- Scientific keys: tonal on `surfaceContainer`.

Everything uses theme tokens and dynamic color.

## Accessibility

- Every symbol key has a spoken label ("divide", "multiply", "minus", "backspace", "square
  root", "pi", "inverse functions", "switch to radians" and so on).
- The display announces a spoken form of the expression, and the result is a polite live
  region, so TalkBack reads the outcome after `=`.
- Keys are at least 48dp. Labels and the display scale with font size: display text
  auto-sizes, and key labels fit their key at 200% font scale.
- Keys give haptic feedback.
- Hardware keyboards work: digits, `+ - * / ^ % ( ) .`, Enter or `=` to evaluate, Backspace,
  and Escape to clear.

## Out of scope for v0.1 (possible later)

- Moving the cursor to edit inside the expression (input only appends at the end).
- Unit and currency conversion. Currency conversion needs the network.
- Programmer mode (hex, binary) and fractions output.
- A home-screen widget.

## Permissions

None.
