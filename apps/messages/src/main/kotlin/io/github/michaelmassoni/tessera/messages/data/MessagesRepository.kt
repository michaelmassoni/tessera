package io.github.michaelmassoni.tessera.messages.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.messages.engine.Addresses
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message

/**
 * Reads the system SMS/MMS database and sends messages. As the default SMS app, Messages records
 * what it sends ([MessageStore]); otherwise Android records sent messages, but hides them from
 * the app that sent them, so a private [SentLog] fills in. Calls block; run them off the main
 * thread.
 */
class MessagesRepository(
    private val context: Context,
) {
    private val resolver = context.contentResolver
    private val addressCache = mutableMapOf<String, String>()
    private val nameCache = mutableMapOf<String, String?>()
    private val sentLog = SentLog(context)

    fun conversations(): List<Conversation> = conversations(null, null)

    private fun conversations(
        selection: String?,
        args: Array<String>?,
    ): List<Conversation> {
        val list = mutableListOf<Conversation>()
        resolver
            .query(
                "content://mms-sms/conversations?simple=true".toUri(),
                arrayOf("_id", "date", "recipient_ids", "snippet", "read"),
                selection,
                args,
                "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val ids =
                        c
                            .getString(2)
                            .orEmpty()
                            .split(' ')
                            .filter { it.isNotBlank() }
                    list +=
                        Conversation(
                            threadId = c.getLong(0),
                            addresses = ids.mapNotNull(::address),
                            snippet = c.getString(3).orEmpty(),
                            date = c.getLong(1),
                            unread = c.getInt(4) == 0,
                        )
                }
            }
        return list
    }

    /** The other people in [thread]. */
    fun addresses(thread: Long): List<String> = conversations("_id = ?", arrayOf(thread.toString())).firstOrNull()?.addresses.orEmpty()

    private fun address(recipientId: String): String? =
        addressCache
            .getOrPut(recipientId) {
                resolver
                    .query("content://mms-sms/canonical-address/$recipientId".toUri(), arrayOf("address"), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }
                    .orEmpty()
            }.takeIf { it.isNotEmpty() }

    /**
     * Messages in [threadId] in date order. When not the default SMS app, adds what this app sent
     * to [addresses] from the [SentLog].
     */
    fun messages(
        threadId: Long?,
        addresses: List<String>,
    ): List<Message> {
        val messages = if (threadId == null) mutableListOf() else threadMessages(threadId, unreadOnly = false)
        if (!context.isDefaultSmsApp() && addresses.size == 1) {
            // The database copy, if this app can see it, wins over the local one.
            sentLog
                .messages(addresses.single())
                .filter { local ->
                    messages.none {
                        it.outgoing && it.body == local.body &&
                            kotlin.math.abs(it.date - local.date) < 60_000
                    }
                }.let(messages::addAll)
        }
        return messages.sortedBy { it.date }
    }

    /** Unread received messages in [thread], oldest first, for its notification. */
    fun unreadIncoming(thread: Long): List<Message> = threadMessages(thread, unreadOnly = true).filter { !it.outgoing }.sortedBy { it.date }

    private fun threadMessages(
        threadId: Long,
        unreadOnly: Boolean,
    ): MutableList<Message> {
        val messages = mutableListOf<Message>()
        val unread = if (unreadOnly) " AND read = 0" else ""
        resolver
            .query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?$unread",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    val outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX
                    messages +=
                        Message(
                            id = "s${c.getLong(0)}",
                            body = c.getString(1).orEmpty(),
                            date = c.getLong(2),
                            outgoing = outgoing,
                            failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                            pending = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                            sender = if (outgoing) null else c.getString(4),
                        )
                }
            }
        resolver
            .query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.MESSAGE_TYPE,
                    Telephony.Mms.SUBJECT,
                ),
                "${Telephony.Mms.THREAD_ID} = ?$unread",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val box = c.getInt(2)
                    val outgoing = box != Telephony.Mms.MESSAGE_BOX_INBOX
                    val notification = c.getInt(3) == MessageStore.TYPE_NOTIFICATION
                    val parts = if (notification) MmsParts("", emptyList(), false) else mmsParts(id)
                    val subject = c.getString(4)?.takeIf { it.isNotBlank() }
                    messages +=
                        Message(
                            id = "m$id",
                            body = listOfNotNull(subject, parts.text.ifEmpty { null }).joinToString("\n"),
                            // MMS dates are in seconds.
                            date = c.getLong(1) * 1000,
                            outgoing = outgoing,
                            failed = box == Telephony.Mms.MESSAGE_BOX_FAILED,
                            pending = box == Telephony.Mms.MESSAGE_BOX_OUTBOX,
                            hasMedia = parts.otherMedia,
                            attachments = parts.attachments,
                            downloadable = notification,
                            sender = if (outgoing) null else mmsSender(id),
                        )
                }
            }
        return messages
    }

    private data class MmsParts(
        val text: String,
        val attachments: List<Attachment>,
        val otherMedia: Boolean,
    )

    /** The text and media of an MMS (the SMIL layout part is skipped). */
    private fun mmsParts(mmsId: Long): MmsParts {
        val text = StringBuilder()
        val attachments = mutableListOf<Attachment>()
        var other = false
        resolver
            .query("content://mms/part".toUri(), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(mmsId.toString()), null)
            ?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(1).orEmpty().lowercase()
                    when {
                        type == "text/plain" -> {
                            text.append(c.getString(2).orEmpty())
                        }

                        type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") -> {
                            attachments +=
                                Attachment("content://mms/part/${c.getLong(0)}", type)
                        }

                        type != "application/smil" -> {
                            other = true
                        }
                    }
                }
            }
        return MmsParts(text.toString(), attachments, other)
    }

    private fun mmsSender(mmsId: Long): String? =
        resolver
            .query("content://mms/$mmsId/addr".toUri(), arrayOf("address"), "type = ${MessageStore.FROM}", null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** The thread for [addresses], creating it if the system allows; null if unknown. */
    fun threadFor(addresses: List<String>): Long? =
        runCatching { Telephony.Threads.getOrCreateThreadId(context, addresses.toSet()) }.getOrNull()
            ?: conversations()
                .firstOrNull { c ->
                    c.addresses.size == addresses.size &&
                        addresses.all { a -> c.addresses.any { Addresses.same(it, a) } }
                }?.threadId

    fun name(address: String): String? =
        nameCache.getOrPut(address) {
            runCatching {
                resolver
                    .query(
                        Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
                        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
        }

    /** Contacts with numbers matching [query] by name or digits, for the new-message screen. */
    fun suggestions(query: String): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        runCatching {
            resolver
                .query(
                    Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query)),
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null,
                )?.use { c -> while (c.moveToNext() && out.size < 8) out += c.getString(0).orEmpty() to c.getString(1).orEmpty() }
        }
        return out
    }

    /**
     * Sends [text] (and [images]) to [addresses]: one SMS for a single person with text only,
     * otherwise one MMS (a group message when there are several people).
     */
    fun send(
        addresses: List<String>,
        text: String,
        images: List<Uri> = emptyList(),
    ) {
        if (addresses.size > 1 || images.isNotEmpty()) {
            Mms(context).send(addresses, text, images)
        } else {
            sms(addresses.single(), text)
        }
    }

    private fun sms(
        address: String,
        text: String,
    ) {
        val default = context.isDefaultSmsApp()
        val row = if (default) MessageStore(context).insertOutgoingSms(address, text) else null
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(text)
        val sent =
            ArrayList(
                parts.indices.map {
                    val intent =
                        Intent(context, SentReceiver::class.java).apply {
                            row?.let { r ->
                                putExtra(SentReceiver.EXTRA_ROW, r.toString())
                            }
                        }
                    PendingIntent.getBroadcast(
                        context,
                        (row?.lastPathSegment?.toIntOrNull() ?: 0) * PARTS + it,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                },
            )
        sms.sendMultipartTextMessage(address, null, parts, sent, null)
        if (!default) sentLog.add(address, text, System.currentTimeMillis())
    }

    private companion object {
        /** Request codes per sent message leave room for this many parts. */
        const val PARTS = 64
    }
}
