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
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message

/**
 * Reads the system SMS/MMS database and sends SMS. Tessera Messages isn't the default SMS app,
 * so it never writes to the database: Android records sent messages itself. Blocking calls run
 * off the main thread.
 */
class MessagesRepository(
    private val context: Context,
) {
    private val resolver = context.contentResolver
    private val addressCache = mutableMapOf<String, String>()
    private val nameCache = mutableMapOf<String, String?>()
    private val sentLog = SentLog(context)

    fun conversations(): List<Conversation> {
        val list = mutableListOf<Conversation>()
        resolver
            .query(
                "content://mms-sms/conversations?simple=true".toUri(),
                arrayOf("_id", "date", "recipient_ids", "snippet", "read"),
                null,
                null,
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

    private fun address(recipientId: String): String? =
        addressCache
            .getOrPut(recipientId) {
                resolver
                    .query("content://mms-sms/canonical-address/$recipientId".toUri(), arrayOf("address"), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }
                    .orEmpty()
            }.takeIf { it.isNotEmpty() }

    /** Messages in [threadId], plus anything this app sent to [address] (see [SentLog]). */
    fun messages(
        threadId: Long?,
        address: String,
    ): List<Message> {
        val messages = if (threadId == null) mutableListOf() else threadMessages(threadId)
        // The database copy, if this app can see it, wins over the local one.
        sentLog
            .messages(address)
            .filter { local ->
                messages.none {
                    it.outgoing && it.body == local.body &&
                        kotlin.math.abs(it.date - local.date) < 60_000
                }
            }.let(messages::addAll)
        return messages.sortedBy { it.date }
    }

    private fun threadMessages(threadId: Long): MutableList<Message> {
        val messages = mutableListOf<Message>()
        resolver
            .query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(3)
                    messages +=
                        Message(
                            id = "s${c.getLong(0)}",
                            body = c.getString(1).orEmpty(),
                            date = c.getLong(2),
                            outgoing = type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                            failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                            pending = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                        )
                }
            }
        resolver
            .query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val (text, media) = mmsParts(id)
                    messages +=
                        Message(
                            id = "m$id",
                            body = text,
                            // MMS dates are in seconds.
                            date = c.getLong(1) * 1000,
                            outgoing = c.getInt(2) != Telephony.Mms.MESSAGE_BOX_INBOX,
                            hasMedia = media,
                        )
                }
            }
        return messages
    }

    /** The text of an MMS and whether it has media parts. */
    private fun mmsParts(mmsId: Long): Pair<String, Boolean> {
        val text = StringBuilder()
        var media = false
        resolver
            .query("content://mms/part".toUri(), arrayOf("ct", "text"), "mid = ?", arrayOf(mmsId.toString()), null)
            ?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(0).orEmpty()
                    when {
                        type == "text/plain" -> text.append(c.getString(1).orEmpty())
                        type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") -> media = true
                    }
                }
            }
        return text.toString() to media
    }

    /** The thread for [address], creating its id if the system allows; null if unknown. */
    fun threadFor(address: String): Long? =
        runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull()
            ?: conversations().firstOrNull { c -> c.addresses.any { Addresses.same(it, address) } }?.threadId

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

    /** Sends [text] to [address], split into parts when needed. */
    fun send(
        address: String,
        text: String,
    ) {
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(text)
        val sent =
            ArrayList(
                parts.indices.map {
                    PendingIntent.getBroadcast(context, it, Intent(context, SentReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
                },
            )
        sms.sendMultipartTextMessage(address, null, parts, sent, null)
        sentLog.add(address, text, System.currentTimeMillis())
    }
}
