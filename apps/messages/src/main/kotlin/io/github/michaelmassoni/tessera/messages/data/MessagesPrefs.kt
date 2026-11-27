package io.github.michaelmassoni.tessera.messages.data

import android.content.Context
import androidx.core.content.edit

/** Messages' own settings (Settings > Sending). */
class MessagesPrefs(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("messages_settings", Context.MODE_PRIVATE)

    /** Ask the carrier to report when a text reaches the other phone. Off by default, as in most apps. */
    var deliveryReports: Boolean
        get() = prefs.getBoolean(KEY_DELIVERY, false)
        set(value) = prefs.edit { putBoolean(KEY_DELIVERY, value) }

    private companion object {
        const val KEY_DELIVERY = "delivery_reports"
    }
}
