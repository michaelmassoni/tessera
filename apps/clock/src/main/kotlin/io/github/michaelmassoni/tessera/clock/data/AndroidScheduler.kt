package io.github.michaelmassoni.tessera.clock.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.michaelmassoni.tessera.clock.MainActivity
import io.github.michaelmassoni.tessera.clock.alarm.AlarmSchedule
import io.github.michaelmassoni.tessera.clock.ringing.ClockReceiver
import io.github.michaelmassoni.tessera.clock.timer.TimerSchedule

/**
 * AlarmManager scheduling (docs/clock.md, "How alarms are delivered").
 *
 * Alarms use setAlarmClock: exact, exempt from Doze and App Standby, and shown to the user as the
 * next alarm. Timers use setExactAndAllowWhileIdle on the elapsed clock, so changing the wall
 * clock can't move them and they don't masquerade as the next alarm on the lock screen.
 */
class AndroidScheduler(
    private val context: Context,
) : AlarmSchedule,
    TimerSchedule {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun scheduleAlarm(
        id: Int,
        triggerAtMillis: Long,
    ) {
        val operation = alarmIntent(id)
        if (canScheduleExactAlarms()) {
            val show =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ALARMS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), operation)
        } else {
            // Only reachable on API 31-32 if the user revoked the permission: the best we can do
            // is an inexact alarm that still runs in Doze. The app shows a warning banner.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    override fun cancelAlarm(id: Int) = alarmManager.cancel(alarmIntent(id))

    override fun scheduleTimer(
        id: Int,
        endElapsed: Long,
    ) {
        val operation = timerIntent(id)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, operation)
        }
    }

    override fun cancelTimer(id: Int) = alarmManager.cancel(timerIntent(id))

    private fun alarmIntent(id: Int) =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(
                context,
                ClockReceiver::class.java,
            ).setAction(ClockReceiver.ACTION_ALARM_FIRED).putExtra(ClockReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun timerIntent(id: Int) =
        PendingIntent.getBroadcast(
            context,
            TIMER_REQUEST_OFFSET + id,
            Intent(
                context,
                ClockReceiver::class.java,
            ).setAction(ClockReceiver.ACTION_TIMER_EXPIRED).putExtra(ClockReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** Keeps timer PendingIntent request codes apart from alarm ids. */
        const val TIMER_REQUEST_OFFSET = 1_000_000
    }
}
