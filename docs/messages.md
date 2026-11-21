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
| `SCHEDULE_EXACT_ALARM` | Scheduled messages go out on time. Optional: without it they may be a few minutes late, and the schedule sheet says so with a link to allow it |
| `RECEIVE_BOOT_COMPLETED` | Set scheduled messages' alarms again after a reboot |
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
  - The `incoming_messages` channel plays the default sound and vibrates (a channel doesn't
    vibrate unless it asks to; the old `messages` channel didn't, and is deleted). Silent and
    vibrate modes apply as usual.
  - Senders are the notification's people, with a `tel:` URI, so Do Not Disturb lets messages
    from starred contacts through.
- **Respond via message:** `HeadlessSmsSendService` sends the Phone app's "Can't talk now" replies.

## Features

- **Conversations:**
  - Every thread, with pinned ones first. Each row shows the name or number (a group icon for
    groups), a snippet, a relative time, and an unread dot or pin.
  - Search (the icon in the top bar, which turns into a search field) covers names, numbers and
    the latest message.
  - **Selection** (long-press):
    - Pin and unpin.
    - Archive and unarchive. Archived conversations are in their own view from the top bar.
    - Mark as read.
    - Block (one-to-one conversations), after confirmation.
    - Delete, after confirmation.
  - Pin and archive are kept by the app, because the system database has no field for them.
  - Names come from one query over every contact number, matched by the last 8 digits
    (`Addresses.names`), and are read again when Contacts change. Numbers with no contact show a
    person icon.
  - Database changes are gathered into one reload 300 ms after the last (an incoming message
    notifies several URIs).- **Conversation:**
  - Bubbles, day separators and a time under each run of messages.
  - In groups, the sender's name above each run.
  - Pictures show inline (Coil). Tapping one opens it in another app through a private copy.
    Video and audio show as chips that open the same way.
  - Failed messages are marked "Not sent".
  - A sent message shows at once (as sending), fades in, and the list scrolls to it. The stored
    copy takes over the same list item when it arrives, so it doesn't flicker. Incoming messages
    scroll into view only if you're already at the bottom.
  - Opening shows the newest 100 messages first, then loads the rest above them. MMS parts are
    read in batches (one query per 200 messages, not one per message). A loading indicator shows
    until the first messages are in.  - **Selection** (long-press a message):
    - Copy, share and forward the text.
    - Details (type, from or to, time) for a single message.
    - Delete, after confirmation.
- **Compose:**
  - Text, plus up to five pictures from the photo picker.
  - A single recipient with text only sends an SMS, split into parts when long, with a segment
    counter.
  - Pictures or several recipients send one MMS. Pictures are re-encoded as JPEG to fit the
    carrier's size limit (`MMS_CONFIG_MAX_MESSAGE_SIZE`, 300 KB if unknown).
  - **Scheduled send:** long-press Send. Quick choices are later today at 6 PM and tonight at 9 PM
    (while at least 30 minutes away) and tomorrow at 8 AM, or any future date and time. Scheduled
    messages show above the composer with Send now and Cancel.
    - Stored by the app (the system database has no scheduled state). Pictures are copied into
      app storage, because photo picker access doesn't outlive the app.
    - An alarm sends them: exact when allowed, otherwise `setAndAllowWhileIdle`. Alarms are set again
      on boot, app update, a change to exact-alarm access, and app start, and anything overdue is
      sent then.
- **New message:**
  - The To field takes names or numbers, with a normal keyboard by default and a button to
    switch to the dialpad.
  - Numbers are laid out for the phone's country as you type ("0412 345 678", "+61 412 345
    678"), using the platform's libphonenumber (`PhoneNumberUtils`). The field keeps bare digits.
  - A number that isn't valid for the country gets "Check this number". It doesn't block sending,
    and short codes (6 digits or fewer) are never flagged.
  - Contact suggestions. Tap one to message them.
  - Long-press a contact, or type a comma, to add more people for a group conversation.
- **Other apps:**
  - `SENDTO` and `VIEW` for `sms:`, `smsto:`, `mms:` and `mmsto:` open the conversation with any
    text pre-filled.
  - `SEND` of text or pictures opens the new-message screen with them attached.

## Engine (pure Kotlin, unit tested)

- `SmsLength`: GSM 7-bit or UCS-2 detection, segment sizes and the counter.
- `Addresses`: normalising numbers for matching.
- `Recipients`: name or number, digits to send, when to check validity, and cursor mapping for
  the formatted display.
- `ScheduleTimes`: the quick schedule choices, and checking a picked time is ahead.
- `Grouping`: day separators, and runs of messages (same side and sender, within 3 minutes).
- `Pdu`: MMS PDUs (OMA-MMS-ENC with WSP encodings):
  - Decodes m-notification-ind and m-retrieve-conf, including multipart bodies, charsets and
    encoded strings.
  - Encodes m-send-req (multipart/related with a generated SMIL layout), m-notifyresp-ind and
    m-acknowledge-ind.

## Out of scope for v0.2

- RCS.
- Retrying a failed message (delete it and send again).
- Delivery reports.
- Searching message text.
- Spam detection.
- Choosing the SIM on dual-SIM phones (the system default is used).
