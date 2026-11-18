package io.github.michaelmassoni.tessera.clock.ringing

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Exported receiver for system broadcasts only (boot, time and zone changes, app update, exact
 * alarm permission). Our own actions go to the non-exported [ClockReceiver], so other apps can't
 * spoof "alarm fired". Every action accepted here is a protected broadcast only the system sends.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in SYSTEM_ACTIONS) ClockReceiver().onReceive(context, intent)
    }

    private companion object {
        val SYSTEM_ACTIONS =
            setOf(
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
    }
}
