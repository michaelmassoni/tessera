package io.github.michaelmassoni.tessera.messages.data

import android.app.PendingIntent
import android.content.ContentUris
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
    private val senderCache get() = SENDERS
    private var contactNumbers: List<ContactRow>? = null
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
     * Messages in [threadId] in date order: the newest [limit] of them, or all when null. When not
     * the default SMS app, adds what this app sent to [addresses] from the [SentLog].
     */
    fun messages(
        threadId: Long?,
        addresses: List<String>,
        limit: Int? = null,
    ): List<Message> {
        val messages = if (threadId == null) mutableListOf() else threadMessages(threadId, unreadOnly = false, limit = limit)
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
        val sorted = messages.sortedBy { it.date }
        return if (limit == null) sorted else sorted.takeLast(limit)
    }

    /** Unread received messages in [thread], oldest first, for its notification. */
    fun unreadIncoming(thread: Long): List<Message> =
        threadMessages(thread, unreadOnly = true, limit = null).filter { !it.outgoing }.sortedBy { it.date }

    /**
     * SMS and MMS in a thread. With a [limit], each table gives its newest [limit] rows (the caller
     * keeps the newest overall). MMS parts and senders are read in batches, not one query per MMS,
     * which is what made long threads slow to open.
     */
    private fun threadMessages(
        threadId: Long,
        unreadOnly: Boolean,
        limit: Int?,
    ): MutableList<Message> {
        val messages = mutableListOf<Message>()
        val unread = if (unreadOnly) " AND read = 0" else ""
        // The telephony provider is SQLite and passes the sort order through, so LIMIT works here.
        val newest = if (limit == null) null else "date DESC LIMIT $limit"
        resolver
            .query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.STATUS,
                ),
                "${Telephony.Sms.THREAD_ID} = ?$unread",
                arrayOf(threadId.toString()),
                newest,
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
                            delivered = outgoing && c.getInt(5) == Telephony.Sms.STATUS_COMPLETE,
                            pending = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                            sender = if (outgoing) null else c.getString(4),
                        )
                }
            }

        data class MmsRow(
            val id: Long,
            val date: Long,
            val box: Int,
            val notification: Boolean,
            val subject: String?,
        )
        val rows = mutableListOf<MmsRow>()
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
                newest,
            )?.use { c ->
                while (c.moveToNext()) {
                    rows +=
                        MmsRow(
                            id = c.getLong(0),
                            // MMS dates are in seconds.
                            date = c.getLong(1) * 1000,
                            box = c.getInt(2),
                            notification = c.getInt(3) == MessageStore.TYPE_NOTIFICATION,
                            subject = c.getString(4)?.takeIf { it.isNotBlank() },
                        )
                }
            }
        val parts = mmsParts(rows.filterNot { it.notification }.map { it.id })
        val senders = mmsSenders(rows.filter { it.box == Telephony.Mms.MESSAGE_BOX_INBOX }.map { it.id })
        rows.forEach { row ->
            val outgoing = row.box != Telephony.Mms.MESSAGE_BOX_INBOX
            val p = parts[row.id] ?: MmsParts("", emptyList(), false)
            messages +=
                Message(
                    id = "m${row.id}",
                    body = listOfNotNull(row.subject, p.text.ifEmpty { null }).joinToString("\n"),
                    date = row.date,
                    outgoing = outgoing,
                    failed = row.box == Telephony.Mms.MESSAGE_BOX_FAILED,
                    pending = row.box == Telephony.Mms.MESSAGE_BOX_OUTBOX,
                    hasMedia = p.otherMedia,
                    attachments = p.attachments,
                    downloadable = row.notification,
                    sender = if (outgoing) null else senders[row.id],
                )
        }
        return messages
    }

    private data class MmsParts(
        val text: String,
        val attachments: List<Attachment>,
        val otherMedia: Boolean,
    )

    /** The text and media of each MMS in [ids] (the SMIL layout part is skipped), a few queries in all. */
    private fun mmsParts(ids: List<Long>): Map<Long, MmsParts> {
        val text = mutableMapOf<Long, StringBuilder>()
        val attachments = mutableMapOf<Long, MutableList<Attachment>>()
        val other = mutableSetOf<Long>()
        ids.chunked(BATCH).forEach { chunk ->
            resolver
                .query("content://mms/part".toUri(), arrayOf("_id", "ct", "text", "mid"), "mid IN (${chunk.joinToString(",")})", null, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val mid = c.getLong(3)
                        val type = c.getString(1).orEmpty().lowercase()
                        when {
                            type == "text/plain" -> {
                                text.getOrPut(mid) { StringBuilder() }.append(c.getString(2).orEmpty())
                            }

                            type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") -> {
                                attachments.getOrPut(mid) { mutableListOf() } += Attachment("content://mms/part/${c.getLong(0)}", type)
                            }

                            type != "application/smil" -> {
                                other += mid
                            }
                        }
                    }
                }
        }
        return ids.associateWith { MmsParts(text[it]?.toString().orEmpty(), attachments[it].orEmpty(), it in other) }
    }

    /**
     * Who sent each received MMS in [ids]. The provider only exposes addresses per message (one
     * query each), and a message's sender never changes, so they're remembered for the process:
     * reopening a busy group chat queries only messages it hasn't seen.
     */
    private fun mmsSenders(ids: List<Long>): Map<Long, String> =
        ids.mapNotNull { id -> (senderCache[id] ?: mmsSender(id)?.also { senderCache[id] = it })?.let { id to it } }.toMap()

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

    fun name(address: String): String? = names(listOf(address))[address]

    /**
     * Contact names for [addresses]. Every contact number is read in one query and kept until
     * [forgetContacts] (called when Contacts change), rather than one lookup per conversation.
     */
    @Synchronized
    fun names(addresses: Collection<String>): Map<String, String> {
        val numbers = contactRows().map { it.number to it.name }
        return Addresses.names(addresses, numbers)
    }

    /** Contact photos (thumbnail URIs) for [addresses], matched the same way as names. */
    @Synchronized
    fun photos(addresses: Collection<String>): Map<String, String> =
        Addresses.names(addresses, contactRows().mapNotNull { r -> r.photo?.let { r.number to it } })

    private data class ContactRow(
        val number: String,
        val name: String,
        val photo: String?,
    )

    /** Every contact number, with its name and photo: read once, until [forgetContacts]. */
    private fun contactRows(): List<ContactRow> =
        contactNumbers ?: runCatching {
            val list = mutableListOf<ContactRow>()
            resolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ),
                    null,
                    null,
                    null,
                )?.use { c ->
                    while (c.moveToNext()) list += ContactRow(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2))
                }
            list.toList()
        }.getOrNull()?.also { contactNumbers = it } ?: emptyList()

    /**
     * Conversations with a message containing [query] (SMS bodies and MMS text), each with its
     * newest matching message's text, for the list's search.
     */
    fun searchMessages(query: String): Map<Long, String> {
        val q = query.trim()
        if (q.length < 2) return emptyMap()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val hits = mutableMapOf<Long, Pair<Long, String>>()

        fun hit(
            thread: Long,
            date: Long,
            text: String,
        ) {
            if ((hits[thread]?.first ?: Long.MIN_VALUE) < date) hits[thread] = date to text
        }
        runCatching {
            resolver
                .query("content://sms".toUri(), arrayOf("thread_id", "date", "body"), "body LIKE ? ESCAPE '\\'", arrayOf(like), null)
                ?.use { c -> while (c.moveToNext()) hit(c.getLong(0), c.getLong(1), c.getString(2).orEmpty()) }
        }
        runCatching {
            val parts = mutableMapOf<Long, String>()
            resolver
                .query(
                    "content://mms/part".toUri(),
                    arrayOf("mid", "text"),
                    "ct = 'text/plain' AND text LIKE ? ESCAPE '\\'",
                    arrayOf(like),
                    null,
                )?.use { c -> while (c.moveToNext()) parts.putIfAbsent(c.getLong(0), c.getString(1).orEmpty()) }
            parts.keys.chunked(500).forEach { chunk ->
                resolver
                    .query("content://mms".toUri(), arrayOf("_id", "thread_id", "date"), "_id IN (${chunk.joinToString(",")})", null, null)
                    // MMS dates are in seconds.
                    ?.use { c -> while (c.moveToNext()) hit(c.getLong(1), c.getLong(2) * 1000, parts[c.getLong(0)].orEmpty()) }
            }
        }
        return hits.mapValues { it.value.second }
    }

    /** Contacts changed: read names again next time. */
    @Synchronized
    fun forgetContacts() {
        contactNumbers = null
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
        // Delivery reports (Settings > Sending): asked for on the last part only, so a long text reports once.
        val delivered =
            if (row != null && MessagesPrefs(context).deliveryReports) {
                ArrayList(
                    parts.indices.map { i ->
                        if (i < parts.lastIndex) {
                            null
                        } else {
                            PendingIntent.getBroadcast(
                                context,
                                DELIVERY_REQUEST + (row.lastPathSegment?.toIntOrNull() ?: 0),
                                Intent(context, DeliveredReceiver::class.java).putExtra(SentReceiver.EXTRA_ROW, row.toString()),
                                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            )
                        }
                    },
                )
            } else {
                null
            }
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
        sms.sendMultipartTextMessage(address, null, parts, sent, delivered)
        if (!default) sentLog.add(address, text, System.currentTimeMillis())
    }

    /**
     * Sends a failed SMS again: the failed copy is removed and the text goes out as a new message
     * (docs/messages.md, Retrying). [id] is the provider's SMS id.
     */
    fun retrySms(id: Long) {
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
        val (address, body) =
            resolver
                .query(uri, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() to c.getString(1).orEmpty() else null }
                ?: return
        if (address.isBlank() || body.isBlank()) return
        resolver.delete(uri, null, null)
        sms(address, body)
    }

    private companion object {
        /** MMS ids per `IN (...)` query. */
        const val BATCH = 200

        /** Request codes per sent message leave room for this many parts. */
        const val PARTS = 64

        /** Delivery-report request codes start here, clear of the sent ones. */
        const val DELIVERY_REQUEST = 1 shl 28
    }
}

/** MMS senders by message id, shared by every repository in the process. */
private val SENDERS = java.util.concurrent.ConcurrentHashMap<Long, String>()
