# Contacts: spec

Package `io.github.michaelmassoni.tessera.contacts`. Status: v0.1, first implementation.

## Goals

A first-party-feeling contacts app over the system contacts database (`ContactsContract`). It
works with any account that syncs contacts (for example DAVx⁵ or a device-only "Phone" account)
and needs no network, Google account or Play Services.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_CONTACTS` | List, search and show contacts |
| `WRITE_CONTACTS` | Create, edit, star and delete contacts; import vCards |

Both are asked for on first launch, behind an explanation screen. If they're denied, the screen
stays with a button to open the app's system settings. Calling and texting use `ACTION_DIAL` and
`ACTION_SENDTO`, which hand off to the dialer and messaging app, so no phone or SMS permission is
needed.

## Features

### List

- **Order:** contacts sorted by display name. Letter headers stick while scrolling. Names
  starting with a digit or symbol go under "#", and accented letters sort with their base
  letter.
- **Favourites:** starred contacts appear first, under a "Favourites" heading.
- **Rows:** each row shows the photo (or a coloured initial avatar), the name, and nothing else,
  like the system app.
- **Search:** matches name (any word, ignoring accents and case), phone number (digits only, so
  "412 345" finds "+61 412 345 678") and email. Results update as you type.
- **Add:** a FAB opens the editor for a new contact.
- **Account filter:** an overflow menu lists the accounts that hold contacts and shows "All
  accounts" by default.
- **Import and export:**
  - **Import:** reads a `.vcf` file picked with `ACTION_OPEN_DOCUMENT`.
  - **Export:** writes every contact to a `.vcf` file via `Contacts.CONTENT_MULTI_VCARD_URI` and
    `ACTION_CREATE_DOCUMENT`.

### Detail

- **Header:** a large avatar, the name and the organisation.
- **Actions:** buttons for Call, Text and Email, each disabled when the contact has no number or
  email.
- **Phones:** each with its type (Mobile, Home, Work, ...). Tap to dial; a trailing button opens
  messages.
- **Emails:** tap to compose.
- **Addresses:** tap to open a `geo:` intent in a maps app, if one is installed.
- **Other fields:** birthday and other events, website, notes and the account the contact is
  stored in.
- **Top bar:** star (favourite), edit, and an overflow with Share (vCard) and Delete. Delete asks
  for confirmation.

### Editor

- **Fields:** first and last name, phones (with type), emails (with type), one postal address,
  company, birthday (date picker) and notes.
  - Each repeating field has an "Add" button and a remove button.
- **New contacts:** the account to save to is chosen in the editor. "Device" (no account) is
  always offered, along with every account that already stores contacts. The account last used is
  remembered.
- **Saving:** only changed rows are written, in one `applyBatch`. Rows the editor doesn't show
  (such as photos and group memberships) are never touched.
- **Leaving with unsaved changes:** asks to discard them.

### Other apps

- `ACTION_INSERT` and `ACTION_INSERT_OR_EDIT` (`vnd.android.cursor.dir/contact`) with
  `ContactsContract.Intents.Insert` extras (name, phone, email, company, notes) open the
  editor pre-filled.
- `ACTION_VIEW` on contact URIs opens the detail screen. `ACTION_EDIT` opens the editor.
- `ACTION_VIEW` on a `text/x-vcard` file offers to import it.
- `ACTION_PICK` on `Contacts.CONTENT_URI`, `Phone.CONTENT_URI` and `Email.CONTENT_URI` returns
  the chosen contact, phone or email URI, so other apps can use Tessera as their picker.

## Engine (pure Kotlin, unit tested)

- `ContactIndex`: sections, search matching and phone digit normalisation.
- `Initials`: avatar initials and a stable colour slot (a hash of the lookup key mapped to the
  theme's container colours, not fixed colours).
- `VCard`: a vCard 2.1, 3.0 and 4.0 reader.
  - Fields: `FN`, `N`, `TEL`, `EMAIL`, `ADR`, `ORG`, `BDAY`, `NOTE` and `URL`.
  - Handles line folding, quoted-printable (2.1), escaped characters and TYPE parameters.
- `EditDiff`: compares the original data rows with the edited fields and produces insert, update
  and delete operations.

## Layout

| Window | Layout |
|--------|--------|
| Compact | List, with detail and editor full-screen on top |
| Medium | Same |
| Expanded | List (360–420dp) next to the detail; the editor replaces the detail pane |

## Accessibility

- Rows read the name.
- Header letters are headings.
- Avatars are decorative.
- Action buttons have text labels.
- Phone numbers are read digit by digit (`TtsSpan`), as the system does.

## Out of scope for v0.1

- Editing photos.
- Groups and labels.
- Merging and linking duplicates.
- Ringtone per contact.
- Blocking numbers.
- QR sharing.
- Multiple addresses in the editor (all of them are still shown on the detail screen).
