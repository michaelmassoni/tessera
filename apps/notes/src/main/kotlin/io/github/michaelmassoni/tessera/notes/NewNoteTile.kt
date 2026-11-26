package io.github.michaelmassoni.tessera.notes

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Quick Settings "New note": opens a new note. */
class NewNoteTile : TileService() {
    override fun onClick() {
        super.onClick()
        val intent =
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NEW_NOTE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startOnOlderAndroid(intent)
        }
    }

    // Android 12 and 13 have only the Intent version; the PendingIntent one is 14+.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startOnOlderAndroid(intent: Intent) = startActivityAndCollapse(intent)
}
