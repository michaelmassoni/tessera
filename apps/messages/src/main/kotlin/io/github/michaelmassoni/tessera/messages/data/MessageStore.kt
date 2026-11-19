package io.github.michaelmassoni.tessera.messages.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.Telephony
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.messages.engine.Pdu

/**
 * Writes to the system SMS/MMS database. Android only allows this for the default SMS app,
 * which has to record every message it receives and sends itself. Calls block; run them off the
 * main thread.
 */
class MessageStore(
    private val context: Context,
) {
    private val resolver = context.contentResolver

    fun threadId(addresses: Collection<String>): Long = Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    // ---- SMS ----

    /** Records a received SMS and returns its thread. */
    fun insertIncomingSms(
        address: String,
        body: String,
        sentAt: Long,
        subscription: Int,
    ): Long {
        val thread = threadId(listOf(address))
        resolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, sentAt)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, thread)
                if (subscription >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscription)
            },
        )
        return thread
    }

    /** Records an SMS about to be sent (in the outbox) and returns its row. */
    fun insertOutgoingSms(
        address: String,
        body: String,
    ): Uri? =
        resolver.insert(
            Telephony.Sms.Outbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.THREAD_ID, threadId(listOf(address)))
            },
        )

    /** Marks a sent SMS as sent or failed. A failure sticks even if a later part succeeds. */
    fun setSmsSent(
        uri: Uri,
        ok: Boolean,
    ) {
        val current =
            resolver
                .query(
                    uri,
                    arrayOf(Telephony.Sms.TYPE),
                    null,
                    null,
                    null,
                )?.use { if (it.moveToFirst()) it.getInt(0) else null }
        if (ok && current == Telephony.Sms.MESSAGE_TYPE_FAILED) return
        resolver.update(
            uri,
            ContentValues().apply {
                put(
                    Telephony.Sms.TYPE,
                    if (ok) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED,
                )
            },
            null,
            null,
        )
    }

    // ---- MMS ----

    /** What's known about an MMS before its parts: who, when and the MMSC's bookkeeping. */
    data class MmsInfo(
        val box: Int,
        val type: Int,
        val from: String?,
        val to: List<String>,
        val cc: List<String> = emptyList(),
        /** Epoch seconds. */
        val date: Long = System.currentTimeMillis() / 1000,
        val subject: String? = null,
        val transactionId: String? = null,
        val messageId: String? = null,
        val contentLocation: String? = null,
        val expiry: Long? = null,
        val size: Long? = null,
        val subscription: Int = -1,
        val read: Boolean = box != Telephony.Mms.MESSAGE_BOX_INBOX,
    )

    /** Records an MMS with its addresses and parts in [thread]; returns its row. */
    fun insertMms(
        thread: Long,
        info: MmsInfo,
        parts: List<Pdu.Part>,
    ): Uri? {
        val values =
            ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, thread)
                put(Telephony.Mms.DATE, info.date)
                put(Telephony.Mms.DATE_SENT, info.date)
                put(Telephony.Mms.MESSAGE_BOX, info.box)
                put(Telephony.Mms.READ, if (info.read) 1 else 0)
                put(Telephony.Mms.SEEN, if (info.read) 1 else 0)
                put(Telephony.Mms.MESSAGE_TYPE, info.type)
                put(Telephony.Mms.MMS_VERSION, 0x12)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                put(Telephony.Mms.MESSAGE_CLASS, "personal")
                put(Telephony.Mms.TEXT_ONLY, if (parts.all { it.isText || it.contentType == "application/smil" }) 1 else 0)
                info.subject?.let {
                    put(Telephony.Mms.SUBJECT, it)
                    put(Telephony.Mms.SUBJECT_CHARSET, Pdu.UTF_8)
                }
                info.transactionId?.let { put(Telephony.Mms.TRANSACTION_ID, it) }
                info.messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
                info.contentLocation?.let { put(Telephony.Mms.CONTENT_LOCATION, it) }
                info.expiry?.let { put(Telephony.Mms.EXPIRY, it) }
                info.size?.let { put(Telephony.Mms.MESSAGE_SIZE, it) }
                if (info.subscription >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, info.subscription)
            }
        val uri = resolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return null
        val id = uri.lastPathSegment ?: return uri
        val addresses =
            listOfNotNull(info.from?.let { it to FROM }) + info.to.map { it to TO } + info.cc.map { it to CC } +
                // A sent message is from us; the MMSC fills in the number.
                if (info.from == null &&
                    info.box != Telephony.Mms.MESSAGE_BOX_INBOX
                ) {
                    listOf("insert-address-token" to FROM)
                } else {
                    emptyList()
                }
        addresses.forEach { (address, type) ->
            resolver.insert(
                "content://mms/$id/addr".toUri(),
                ContentValues().apply {
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, type)
                    put(Telephony.Mms.Addr.CHARSET, Pdu.UTF_8)
                    put(Telephony.Mms.Addr.MSG_ID, id)
                },
            )
        }
        parts.forEach { part -> insertPart(id, part) }
        return uri
    }

    private fun insertPart(
        mmsId: String,
        part: Pdu.Part,
    ) {
        val text = part.isText || part.contentType == "application/smil"
        val values =
            ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, mmsId)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, it) }
                part.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, it) }
                part.name?.let {
                    put(Telephony.Mms.Part.NAME, it)
                    put(Telephony.Mms.Part.FILENAME, it)
                }
                if (text) {
                    put(Telephony.Mms.Part.CHARSET, Pdu.UTF_8)
                    put(Telephony.Mms.Part.TEXT, part.text())
                }
            }
        val uri = resolver.insert("content://mms/$mmsId/part".toUri(), values) ?: return
        if (!text) resolver.openOutputStream(uri)?.use { it.write(part.data) }
    }

    fun setMmsBox(
        uri: Uri,
        box: Int,
        messageId: String? = null,
    ) {
        resolver.update(
            uri,
            ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, box)
                messageId?.let { put(Telephony.Mms.MESSAGE_ID, it) }
            },
            null,
            null,
        )
    }

    fun deleteMms(id: Long) = resolver.delete("content://mms/$id".toUri(), null, null)

    // ---- Conversations ----

    fun markRead(threads: Collection<Long>) {
        val read =
            ContentValues().apply {
                put("read", 1)
                put("seen", 1)
            }
        for (t in threads) {
            resolver.update(
                Telephony.Sms.CONTENT_URI,
                read,
                "${Telephony.Sms.THREAD_ID} = ? AND (read = 0 OR seen = 0)",
                arrayOf(t.toString()),
            )
            resolver.update(
                Telephony.Mms.CONTENT_URI,
                read,
                "${Telephony.Mms.THREAD_ID} = ? AND (read = 0 OR seen = 0)",
                arrayOf(t.toString()),
            )
        }
    }

    fun deleteThreads(threads: Collection<Long>) {
        for (t in threads) resolver.delete("content://mms-sms/conversations/$t".toUri(), null, null)
    }

    /** Deletes messages by their ids as the repository makes them ("s12" SMS, "m3" MMS). */
    fun deleteMessages(ids: Collection<String>) {
        for (id in ids) {
            val n = id.drop(1).toLongOrNull() ?: continue
            when (id.firstOrNull()) {
                's' -> resolver.delete("content://sms/$n".toUri(), null, null)
                'm' -> deleteMms(n)
            }
        }
    }

    /** Adds [number] to the system block list (calls and texts). */
    fun block(number: String): Boolean =
        BlockedNumberContract.canCurrentUserBlockNumbers(context) &&
            resolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
            ) != null

    companion object {
        // MMS address types (PduHeaders).
        const val FROM = 137
        const val TO = 151
        const val CC = 130

        // MMS message types.
        const val TYPE_SEND_REQ = 128
        const val TYPE_NOTIFICATION = 130
        const val TYPE_RETRIEVE_CONF = 132
    }
}
