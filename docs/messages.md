# Messages: spec

Package `io.github.michaelmassoni.tessera.messages`. Status: v0.2, a full default SMS app.
**MMS and receiving need testing on a real phone with a SIM;** the emulator can only receive
SMS.

## Goals

A clean SMS and MMS app over the system message database. It works in two modes:

1. **Default SMS app** (chosen from a banner, through `RoleManager.ROLE_SMS`):
   - Receives SMS and MMS.
   - Notifies.
   - Records everything it sends and receives.
   - Can delete messages, mark them read and block numbers.
2. **Not the default:**
   - Reads every conversation and sends SMS and MMS; Android records those.
   - Android hides SMS sent by a non-default app from that app, so a private sent log fills in.
   - Actions that change the database explain that they need the default SMS app, with a
     button to make it the default.

## Permissions

| Permission | Why |
|------------|-----|
| `READ_SMS`, `SEND_SMS` | Show and send messages |
| `RECEIVE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH` | Receive as the default SMS app |
| `READ_CONTACTS` | Names instead of numbers; contact suggestions |
| `POST_NOTIFICATIONS` | New-message notifications |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | Leave your own number out of group conversations. Only used if the SMS role grants them; never asked for. |

## Receiving (default SMS app)

- **SMS (`SMS_DELIVER`):**
  - The parts are joined and written to the inbox with their thread.
  - A conversation that was archived comes back to the list.
  - A notification is posted.
- **MMS (`WAP_PUSH_DELIVER`):**
  1. The m-notification-ind is decoded and recorded straight away, so the message shows even if
     the download fails. It shows as "Picture message not downloaded yet", with a Download
     button.
  2. `SmsManager.downloadMultimediaMessage` fetches the m-retrieve-conf. The message is
     decoded and stored with its addresses and parts, in a group thread when it has several
     recipients.
  3. The MMSC is acknowledged with an m-acknowledge-ind or m-notifyresp-ind.
- **Notifications:**
  - One per conversation, using `MessagingStyle` with the unread messages and sender names.
  - Published as a long-lived conversation shortcut, so they appear in Android's Conversations
    section.
  - Actions: Reply (inline) and Mark as read.
  - After a reply, the notification is updated silently with the reply. Android doesn't let an
    app cancel a notification that was just replied to.
  - Nothing is posted while that conversation is open.
- **Respond via message:** `HeadlessSmsSendService` sends the Phone app's "Can't talk now" replies.

## Features

- **Conversations:**
  - Every thread, with pinned ones first. Each row shows the name or number (a group icon for
    groups), a snippet, a relative time, and an unread dot or pin.
  - Search covers names, numbers and the latest message.
  - **Selection** (long-press):
    - Pin and unpin.
    - Archive and unarchive. Archived conversations are in their own view from the top bar.
    - Mark as read.
    - Block (one-to-one conversations), after confirmation.
    - Delete, after confirmation.
  - Pin and archive are kept by the app, because the system database has no field for them.
- **Conversation:**
  - Bubbles, day separators and a time under each run of messages.
  - In groups, the sender's name above each run.
  - Pictures show inline (Coil). Tapping one opens it in another app through a private copy.
    Video and audio show as chips that open the same way.
  - Failed messages are marked "Not sent".
  - **Selection** (long-press a message):
    - Copy, share and forward the text.
    - Details (type, from or to, time) for a single message.
    - Delete, after confirmation.
- **Compose:**
  - Text, plus up to five pictures from the photo picker.
  - A single recipient with text only sends an SMS, split into parts when long, with a segment
    counter.
  - Pictures or several recipients send one MMS. Pictures are re-encoded as JPEG to fit the
    carrier's size limit (`MMS_CONFIG_MAX_MESSAGE_SIZE`, 300 KB if unknown).
- **New message:**
  - Contact suggestions. Tap one to message them.
  - Long-press a contact, or type a comma, to add more people for a group conversation.
- **Other apps:**
  - `SENDTO` and `VIEW` for `sms:`, `smsto:`, `mms:` and `mmsto:` open the conversation with any
    text pre-filled.
  - `SEND` of text or pictures opens the new-message screen with them attached.

## Engine (pure Kotlin, unit tested)

- `SmsLength`: GSM 7-bit or UCS-2 detection, segment sizes and the counter.
- `Addresses`: normalising numbers for matching.
- `Grouping`: day separators, and runs of messages (same side and sender, within 3 minutes).
- `Pdu`: MMS PDUs (OMA-MMS-ENC with WSP encodings):
  - Decodes m-notification-ind and m-retrieve-conf, including multipart bodies, charsets and
    encoded strings.
  - Encodes m-send-req (multipart/related with a generated SMIL layout), m-notifyresp-ind and
    m-acknowledge-ind.

## Out of scope for v0.2

- RCS.
- Scheduled messages.
- Retrying a failed message (delete it and send again).
- Delivery reports.
- Searching message text.
- Spam detection.
- Choosing the SIM on dual-SIM phones (the system default is used).
