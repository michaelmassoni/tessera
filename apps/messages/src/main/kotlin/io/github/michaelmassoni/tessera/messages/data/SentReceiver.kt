package io.github.michaelmassoni.tessera.messages.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.messages.R

/**
 * The result of sending one SMS part. As the default SMS app, Messages records the outcome on the
 * message; otherwise Android already has. Either way a failure is reported.
 */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val ok = resultCode == Activity.RESULT_OK
        intent.getStringExtra(EXTRA_ROW)?.toUri()?.let { row ->
            val pending = goAsync()
            Thread {
                runCatching { MessageStore(context).setSmsSent(row, ok) }
                pending.finish()
            }.start()
        }
        if (!ok) failed(context)
    }

    companion object {
        const val EXTRA_ROW = "row"

        fun failed(context: Context) = Toast.makeText(context, R.string.send_failed, Toast.LENGTH_SHORT).show()
    }
}
