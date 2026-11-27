package io.github.michaelmassoni.tessera.messages.data

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.core.net.toUri

/**
 * A delivery report for a sent SMS (Settings > Sending > Delivery reports): the carrier says whether
 * the other phone got it. Recorded on the message's STATUS, which the conversation shows as
 * "Delivered".
 */
class DeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val row = intent.getStringExtra(SentReceiver.EXTRA_ROW)?.toUri() ?: return
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val status = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"))?.status ?: return
        val pending = goAsync()
        Thread {
            runCatching {
                context.contentResolver.update(row, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null)
            }
            pending.finish()
        }.start()
    }
}
