# Messages: spec

Package `io.github.michaelmassoni.tessera.messages`. Status: v0.1, first implementation.

## Goals and the big limitation

A clean SMS app over the system message database.

**v0.1 does not ask to be the default SMS app.** A default SMS app must receive MMS (pictures
and group chats), and doing that needs the WAP-push PDU download and decoding that Android
doesn't expose as public API. An SMS-only default app would silently lose MMS. Until MMS
receiving is built, Messages:

- reads every conversation, including the text of MMS messages;
- shows its own sent messages from a private log, because Android hides them from the non-default app that sent them;
- sends SMS. Android writes messages sent by a non-default app into the shared database itself,
  so they appear in every messaging app.

Receiving stays with the current default messaging app, which keeps notifying as before.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_SMS` | Show conversations |
| `SEND_SMS` | Send replies and new messages |
| `READ_CONTACTS` | Show names instead of numbers |

## Features

- **Conversations:**
  - Every thread (`mms-sms/conversations?simple=true`), newest first.
  - Each row shows the contact name (or number), a snippet, a relative time and an unread
    marker.
  - Search filters by name, number or snippet.
- **Conversation:**
  - Message bubbles: received on the left in a surface colour, sent on the right in the
    primary container colour.
  - Day separators and a time under each message group.
  - MMS text parts are shown, with a "Picture message" placeholder for media.
  - Failed sends are marked.
- **Compose:**
  - A text field with a send button, and a character and segment counter once a message needs
    more than one SMS.
  - Long messages are split with `divideMessage` and sent as multipart.
- **New message:** a FAB opens a number field with contact suggestions (`READ_CONTACTS`).
- **Other apps:** handles `SENDTO` and `VIEW` for `sms:`/`smsto:`, opening the conversation with
  the text pre-filled, so "Text" in Contacts or Phone opens here when chosen.

## Engine (pure Kotlin, unit tested)

- `SmsLength`:
  - Detects GSM 7-bit vs UCS-2 (for example, an emoji forces UCS-2).
  - Segment sizes: 160 or 153 characters, and 70 or 67 for UCS-2.
  - The counter text.
- `Addresses`: normalises numbers for matching (digits, with a leading +).
- `Grouping`: day separators, and groups consecutive messages from the same side within 3
  minutes.

## Out of scope for v0.1

- Being the default SMS app.
- Receiving or notifying.
- Sending MMS, group messages and attachments.
- RCS.
- Scheduled messages.
- Blocking.
- Archive and delete (only the default app may change the database).
