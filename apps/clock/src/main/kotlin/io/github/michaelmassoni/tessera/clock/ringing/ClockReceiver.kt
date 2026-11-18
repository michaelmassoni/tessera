package io.github.michaelmassoni.tessera.clock.ringing

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.michaelmassoni.tessera.clock.graph

/**
 * Every broadcast the clock reacts to. Direct-boot aware, so it runs after a reboot before the
 * first unlock (LOCKED_BOOT_COMPLETED) and alarms are back in AlarmManager straight away.
 */
class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val graph = context.graph
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (intent.action) {
            ACTION_ALARM_FIRED -> {
                val alarm = graph.alarms.onFired(id) ?: return
                startRinging(context, RingingService.ringAlarm(context, alarm.id))
            }

            ACTION_TIMER_EXPIRED -> {
                graph.timers.expire(id) ?: return
                startRinging(context, RingingService.ringTimer(context, id))
            }

            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> {
                graph.alarms.rescheduleAll()
                graph.timers.restoreAfterBoot()
                // The elapsed clock restarted, so a running stopwatch can't be recovered.
                if (graph.stopwatch.stopwatch.value.running) graph.stopwatch.reset()
            }

            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                graph.alarms.rescheduleAll()
                graph.timers.rescheduleRunning()
            }

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                graph.alarms.rescheduleAll()
                graph.timers.onWallClockChanged()
            }

            ACTION_TIMER_PAUSE -> {
                graph.timers.pause(id)
            }

            ACTION_TIMER_RESUME -> {
                graph.timers.start(id)
            }

            ACTION_TIMER_STOP -> {
                graph.timers.reset(id)
            }

            ACTION_STOPWATCH_PAUSE -> {
                graph.stopwatch.pause()
            }

            ACTION_STOPWATCH_RESUME -> {
                graph.stopwatch.start()
            }

            ACTION_STOPWATCH_LAP -> {
                graph.stopwatch.lap()
            }

            ACTION_STOPWATCH_RESET -> {
                graph.stopwatch.reset()
            }
        }
    }

    private fun startRinging(
        context: Context,
        intent: Intent,
    ) {
        // Allowed from the background: we are handling an exact alarm.
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        const val ACTION_ALARM_FIRED = "io.github.michaelmassoni.tessera.clock.ALARM_FIRED"
        const val ACTION_TIMER_EXPIRED = "io.github.michaelmassoni.tessera.clock.TIMER_EXPIRED"
        const val ACTION_TIMER_PAUSE = "io.github.michaelmassoni.tessera.clock.TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = "io.github.michaelmassoni.tessera.clock.TIMER_RESUME"
        const val ACTION_TIMER_STOP = "io.github.michaelmassoni.tessera.clock.TIMER_STOP"
        const val ACTION_STOPWATCH_PAUSE = "io.github.michaelmassoni.tessera.clock.STOPWATCH_PAUSE"
        const val ACTION_STOPWATCH_RESUME = "io.github.michaelmassoni.tessera.clock.STOPWATCH_RESUME"
        const val ACTION_STOPWATCH_LAP = "io.github.michaelmassoni.tessera.clock.STOPWATCH_LAP"
        const val ACTION_STOPWATCH_RESET = "io.github.michaelmassoni.tessera.clock.STOPWATCH_RESET"
        const val EXTRA_ID = "id"
    }
}
