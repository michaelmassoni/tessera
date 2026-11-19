package io.github.michaelmassoni.tessera.messages.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import io.github.michaelmassoni.tessera.messages.engine.Addresses
import io.github.michaelmassoni.tessera.messages.engine.Pdu
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Picture and group messages. Android moves the bytes (`SmsManager` downloads and sends PDUs over
 * the carrier's MMS connection); the app builds and reads the PDUs and records the messages.
 * Calls block; run them off the main thread.
 */
class Mms(
    private val context: Context,
) {
    private val store = MessageStore(context)

    private fun dir() = File(context.cacheDir, "mms").apply { mkdirs() }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.mms", file)

    private fun smsManager(subscription: Int): SmsManager {
        val default = context.getSystemService(SmsManager::class.java)
        return if (subscription >= 0) default.createForSubscriptionId(subscription) else default
    }

    // ---- Receiving ----

    /**
     * A WAP push announced a new MMS. Records it straight away (so it shows even if the download
     * fails, with a Download button), then downloads it.
     */
    fun onNotification(
        pdu: ByteArray,
        subscription: Int,
    ): Long? {
        val n = Pdu.parseNotification(pdu)
        val from = n.from ?: UNKNOWN
        val thread = store.threadId(listOf(from))
        val row =
            store.insertMms(
                thread,
                MessageStore.MmsInfo(
                    box = Telephony.Mms.MESSAGE_BOX_INBOX,
                    type = MessageStore.TYPE_NOTIFICATION,
                    from = from,
                    to = emptyList(),
                    subject = n.subject,
                    transactionId = n.transactionId,
                    contentLocation = n.contentLocation,
                    expiry = n.expiry,
                    size = n.size,
                    subscription = subscription,
                ),
                emptyList(),
            ) ?: return null
        val id = row.lastPathSegment?.toLongOrNull() ?: return thread
        download(id, n.contentLocation, n.transactionId, subscription)
        return thread
    }

    /** Downloads (again) the MMS recorded as notification row [id]. */
    fun retry(id: Long) {
        context.contentResolver
            .query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.CONTENT_LOCATION, Telephony.Mms.TRANSACTION_ID, Telephony.Mms.SUBSCRIPTION_ID),
                "${Telephony.Mms._ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { c -> if (c.moveToFirst()) download(id, c.getString(0) ?: return, c.getString(1).orEmpty(), c.getInt(2)) }
    }

    private fun download(
        id: Long,
        location: String,
        transactionId: String,
        subscription: Int,
    ) {
        val file = File(dir(), "download-$id.pdu").apply { delete() }
        val done =
            Intent(context, MmsDownloadedReceiver::class.java)
                .putExtra(EXTRA_ROW, id)
                .putExtra(EXTRA_FILE, file.path)
                .putExtra(EXTRA_TRANSACTION, transactionId)
                .putExtra(EXTRA_SUBSCRIPTION, subscription)
        val pending =
            PendingIntent.getBroadcast(
                context,
                id.toInt(),
                done,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        smsManager(subscription).downloadMultimediaMessage(context, location, uriFor(file), null, pending)
    }

    /** The download finished; on success, records the full message and acknowledges it. */
    fun onDownloaded(
        ok: Boolean,
        id: Long,
        file: File,
        transactionId: String,
        subscription: Int,
    ): Long? {
        val bytes = if (ok && file.exists()) file.readBytes() else null
        file.delete()
        val retrieved = bytes?.let { runCatching { Pdu.parseRetrieved(it) }.getOrNull() } ?: return null
        val own = ownNumbers()
        val from = retrieved.from ?: UNKNOWN
        val others = (retrieved.to + retrieved.cc).filterNot { a -> own.any { Addresses.same(it, a) } }
        // A group conversation is everyone but us; a message only to us is a one-to-one thread.
        val participants =
            (
                listOf(from) +
                    if (retrieved.to.size + retrieved.cc.size >
                        1
                    ) {
                        others
                    } else {
                        emptyList()
                    }
            ).distinctBy(Addresses::normalize)
        val thread = store.threadId(participants)
        store.insertMms(
            thread,
            MessageStore.MmsInfo(
                box = Telephony.Mms.MESSAGE_BOX_INBOX,
                type = MessageStore.TYPE_RETRIEVE_CONF,
                from = from,
                to = retrieved.to,
                cc = retrieved.cc,
                date = retrieved.date ?: (System.currentTimeMillis() / 1000),
                subject = retrieved.subject,
                transactionId = retrieved.transactionId ?: transactionId,
                messageId = retrieved.messageId,
                subscription = subscription,
            ),
            retrieved.parts,
        )
        store.deleteMms(id)
        // Tell the MMSC we have it, or it may send the notification again.
        val ack = retrieved.transactionId?.let(Pdu::acknowledge) ?: Pdu.notifyResponse(transactionId)
        runCatching { sendPdu(ack, subscription, null) }
        return thread
    }

    // ---- Sending ----

    /** Sends [text] and [images] to [recipients] as one MMS (a group message if several). */
    fun send(
        recipients: List<String>,
        text: String,
        images: List<Uri>,
    ) {
        val manager = smsManager(-1)
        val limit =
            runCatching { manager.carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT_LIMIT) }
                .getOrDefault(DEFAULT_LIMIT)
                .takeIf { it > 0 } ?: DEFAULT_LIMIT
        val perImage = if (images.isEmpty()) 0 else (limit - OVERHEAD) / images.size
        val parts =
            buildList {
                images.forEachIndexed { i, uri -> compress(uri, perImage)?.let { add(Pdu.Part("image/jpeg", it, name = "image_$i.jpg")) } }
                if (text.isNotBlank()) add(Pdu.Part("text/plain", text.toByteArray(), charset = Pdu.UTF_8))
            }
        if (parts.isEmpty()) return
        val pdu = Pdu.sendRequest(recipients, parts, transactionId = UUID.randomUUID().toString().take(TRANSACTION_LENGTH))
        val row =
            if (context.isDefaultSmsApp()) {
                store.insertMms(
                    store.threadId(recipients),
                    MessageStore.MmsInfo(
                        box = Telephony.Mms.MESSAGE_BOX_OUTBOX,
                        type = MessageStore.TYPE_SEND_REQ,
                        from = null,
                        to = recipients,
                    ),
                    parts,
                )
            } else {
                null
            }
        sendPdu(pdu, -1, row)
    }

    private fun sendPdu(
        pdu: ByteArray,
        subscription: Int,
        row: Uri?,
    ) {
        val file = File(dir(), "send-${UUID.randomUUID()}.pdu").apply { writeBytes(pdu) }
        val sent =
            Intent(context, MmsSentReceiver::class.java)
                .putExtra(EXTRA_FILE, file.path)
                .apply { row?.let { putExtra(EXTRA_ROW_URI, it.toString()) } }
        val pending =
            PendingIntent.getBroadcast(
                context,
                file.name.hashCode(),
                sent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        smsManager(subscription).sendMultimediaMessage(context, uriFor(file), null, null, pending)
    }

    fun onSent(
        ok: Boolean,
        file: File,
        row: Uri?,
    ) {
        file.delete()
        if (row != null) store.setMmsBox(row, if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
    }

    /** Scales and re-encodes a picture as JPEG to fit in [maxBytes]. */
    private fun compress(
        uri: Uri,
        maxBytes: Int,
    ): ByteArray? {
        var bitmap =
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_SIDE) decoder.setTargetSampleSize((longest + MAX_SIDE - 1) / MAX_SIDE)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull() ?: return null
        repeat(SHRINK_STEPS) {
            for (quality in QUALITIES) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) return out.toByteArray()
            }
            bitmap = bitmap.scale((bitmap.width * SHRINK).toInt().coerceAtLeast(1), (bitmap.height * SHRINK).toInt().coerceAtLeast(1))
        }
        return null
    }

    /** Our own numbers, to leave out of group conversations; empty if Android won't say. */
    private fun ownNumbers(): Set<String> {
        // The default SMS role usually grants these; nothing asks for them separately.
        val granted =
            listOf(Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        if (!granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptySet()
        return try {
            val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            subscriptions.activeSubscriptionInfoList
                .orEmpty()
                .map {
                    subscriptions.getPhoneNumber(it.subscriptionId)
                }.filter { it.isNotBlank() }
                .toSet()
        } catch (_: SecurityException) {
            emptySet()
        }
    }

    companion object {
        const val EXTRA_ROW = "row"
        const val EXTRA_ROW_URI = "row_uri"
        const val EXTRA_FILE = "file"
        const val EXTRA_TRANSACTION = "transaction"
        const val EXTRA_SUBSCRIPTION = "subscription"

        private const val UNKNOWN = "Unknown"
        private const val DEFAULT_LIMIT = 300 * 1024
        private const val OVERHEAD = 8 * 1024
        private const val MAX_SIDE = 1600
        private const val SHRINK = 0.75f
        private const val SHRINK_STEPS = 6
        private const val TRANSACTION_LENGTH = 20
        private val QUALITIES = listOf(85, 70, 55)
    }
}
