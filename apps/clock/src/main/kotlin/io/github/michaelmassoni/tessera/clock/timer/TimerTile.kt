package io.github.michaelmassoni.tessera.clock.timer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.service.quicksettings.TileService
import io.github.michaelmassoni.tessera.clock.MainActivity

/** Quick Settings "Timer": opens Clock on the Timer tab. */
class TimerTile : TileService() {
    override fun onClick() {
        super.onClick()
        val intent =
            Intent(this, MainActivity::class.java)
                .setAction(AlarmClock.ACTION_SHOW_TIMERS)
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
