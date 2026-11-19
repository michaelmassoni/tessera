package io.github.michaelmassoni.tessera.messages.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.michaelmassoni.tessera.messages.R

/** Reports a failed send. Android has already recorded the message as failed in the database. */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (resultCode != Activity.RESULT_OK) Toast.makeText(context, R.string.send_failed, Toast.LENGTH_SHORT).show()
    }
}
