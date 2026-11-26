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
| `INTERNET` | Only to sync with the CardDAV server the person adds (docs/sync.md); nothing else is contacted |
| `ACCESS_LOCAL_NETWORK` | Android 17 asks before an app reaches the local network. Asked for only when the server is on it: at sign-in, and once on launch for an account made earlier. Without it a server on the local network can't be reached; Settings says so and links to the permission. |
| `READ_SYNC_SETTINGS`, `WRITE_SYNC_SETTINGS` | The sync accounts: whether they sync on local changes |

Both are asked for on first launch, behind an explanation screen. If they're denied, the screen
stays with a button to open the app's system settings. Calling and texting use `ACTION_DIAL` and
`ACTION_SENDTO`, which hand off to the dialer and messaging app, so no phone or SMS permission is
needed.

## Features

- **Sync with a server (0.3):** CardDAV address books, each its own account, with both of DAVx5's
  group methods (docs/sync.md). First run offers it after contacts access; Settings have a Sync
  section and the group method.

### List

- **Order:** contacts sorted by display name. Letter headers stick while scrolling. Names
  starting with a digit or symbol go under "#", and accented letters sort with their base
  letter.
- **Favourites:** starred contacts appear first, under a "Favourites" heading.
- **Reloading:** a burst of changes (a sync) reloads the list once, 500 ms after the last.
  Decoded photos are kept in memory (8 MB) so rows scrolled back into view don't decode again;
  the cache is cleared whenever contacts change, because a contact's photo URI stays the same when
  the photo is replaced.
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

## Contacts made of several copies (0.5.1)

A contact can join raw contacts from several accounts: the person's own (device, CardDAV,
Google) and copies other apps add for themselves (Signal, WhatsApp, Telegram…), which repeat its
numbers. The detail screen shows each number and email once (numbers match on their last nine
digits, emails ignoring case), keeping the own copy's row. The editor edits the person's own copy:
an account whose contacts sync adapter uploads changes, or the device; a mirroring app's copy
(adapter that doesn't upload, or a known messaging app) is never edited, since the app would
overwrite it. Name, company, birthday and note come from that copy too (`RawChoice`, unit tested).

## Photos, duplicates and addresses (0.5)

- **Photo:** the editor shows the photo (or initials) with Add or Change photo and Remove. Change
  opens the system photo picker (no storage permission); the picture's centred square, scaled to
  at most 720 px (`PhotoCrop`, unit tested), becomes the contact's display photo when it's saved.
  There's no crop editor: the centre is used, as most phones' quick photo set does. A synced
  account sends the new photo with its next sync (CardDAV PHOTO).
- **Merge duplicates** (list menu): groups of contacts with the same name (ignoring case, accents
  and spacing), or a phone number (last nine digits, so national and international forms match)
  or email in common, joined through any link (`Duplicates`, unit tested). Merge joins a group
  into one contact through `AggregationExceptions` (keep together), as the system's Contacts
  does: each account keeps its own data, nothing is deleted. The page updates as groups are merged.
- **Addresses:** any number in the editor (was one), each with its type; a second defaults to Work.

## Out of scope for v0.1

- Groups and labels.
- Ringtone per contact.
- Blocking numbers.
- QR sharing.

## First run

See [setup.md](setup.md) for what contacts asks for the first time it opens.
