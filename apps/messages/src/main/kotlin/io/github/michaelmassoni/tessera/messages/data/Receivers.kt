package io.github.michaelmassoni.tessera.messages.data

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import java.io.File
import java.util.concurrent.Executors

internal const val TAG = "TesseraMessages"

/** Database and network work from receivers runs here, off the main thread. */
private val background = Executors.newSingleThreadExecutor()

private fun BroadcastReceiver.inBackground(block: () -> Unit) {
    val pending = goAsync()
    background.execute {
        try {
            runCatching(block).onFailure { Log.w(TAG, "Background work failed", it) }
        } finally {
            pending.finish()
        }
    }
}

private fun Intent.subscription(): Int = getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, getIntExtra("subscription", -1))

/** SMS_DELIVER: only the default SMS app gets it, and must record the message itself. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val subscription = intent.subscription()
        inBackground {
            val store = MessageStore(context)
            // One intent can hold the parts of a long message (and, rarely, several senders).
            val threads =
                parts.filterNotNull().groupBy { it.displayOriginatingAddress.orEmpty() }.map { (address, messages) ->
                    store.insertIncomingSms(
                        address,
                        messages.joinToString("") {
                            it.displayMessageBody.orEmpty()
                        },
                        messages.first().timestampMillis,
                        subscription,
                    )
                }
            threads.distinct().forEach { thread ->
                LocalThreads(context).archive(listOf(thread), false)
                Notifier(context).notify(thread)
            }
        }
    }
}

/** WAP_PUSH_DELIVER with an MMS notification: download the message. */
class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val subscription = intent.subscription()
        inBackground {
            val thread = Mms(context).onNotification(pdu, subscription) ?: return@inBackground
            LocalThreads(context).archive(listOf(thread), false)
            Notifier(context).notify(thread)
        }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val ok = resultCode == Activity.RESULT_OK
        val id = intent.getLongExtra(Mms.EXTRA_ROW, -1)
        val file = File(intent.getStringExtra(Mms.EXTRA_FILE) ?: return)
        val transaction = intent.getStringExtra(Mms.EXTRA_TRANSACTION).orEmpty()
        val subscription = intent.getIntExtra(Mms.EXTRA_SUBSCRIPTION, -1)
        inBackground {
            val thread = Mms(context).onDownloaded(ok, id, file, transaction, subscription) ?: return@inBackground
            Notifier(context).notify(thread)
        }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val ok = resultCode == Activity.RESULT_OK
        val file = File(intent.getStringExtra(Mms.EXTRA_FILE) ?: return)
        val row = intent.getStringExtra(Mms.EXTRA_ROW_URI)?.toUri()
        inBackground { Mms(context).onSent(ok, file, row) }
        if (!ok) SentReceiver.failed(context)
    }
}

/** Reply and Mark as read from a message notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val thread = intent.getLongExtra(EXTRA_THREAD, -1)
        if (thread < 0) return
        val reply =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(KEY_REPLY)
                ?.toString()
                ?.trim()
        inBackground {
            if (intent.action == ACTION_REPLY && !reply.isNullOrEmpty()) {
                val repository = MessagesRepository(context)
                val addresses = repository.addresses(thread)
                if (addresses.isNotEmpty()) repository.send(addresses, reply)
            }
            if (context.isDefaultSmsApp()) MessageStore(context).markRead(listOf(thread))
            // Android keeps a notification that was just replied to visible, ignoring a cancel, and
            // expects it to be updated with the reply instead.
            if (intent.action == ACTION_REPLY &&
                !reply.isNullOrEmpty()
            ) {
                Notifier(context).notify(thread, afterReply = true)
            } else {
                Notifier(context).cancel(thread)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "io.github.michaelmassoni.tessera.messages.REPLY"
        const val ACTION_MARK_READ = "io.github.michaelmassoni.tessera.messages.MARK_READ"
        const val EXTRA_THREAD = "thread"
        const val KEY_REPLY = "reply"
    }
}

/**
 * "Respond via message" from the phone app's incoming-call screen. A default SMS app must
 * provide it: send the text without showing any UI.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val recipients = intent?.data?.let(::recipients).orEmpty()
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE && !text.isNullOrEmpty() && recipients.isNotEmpty()) {
            background.execute {
                runCatching { MessagesRepository(this).send(recipients, text) }
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    companion object {
        /** The numbers in an sms:/smsto:/mms:/mmsto: URI ("smsto:123,456?body=..."). */
        fun recipients(uri: Uri): List<String> =
            Uri
                .decode(uri.schemeSpecificPart)
                .substringBefore('?')
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
