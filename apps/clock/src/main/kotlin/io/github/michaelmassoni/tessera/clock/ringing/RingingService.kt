package io.github.michaelmassoni.tessera.clock.ringing

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import io.github.michaelmassoni.tessera.clock.graph

/**
 * Keeps an alarm or timer ringing: sound, vibration, the full-screen notification and the
 * auto-silence timeout. Foreground service of type systemExempted, which Android allows for apps
 * holding exact-alarm permission that use it to keep alarms ringing.
 */
class RingingService : Service() {
    private lateinit var player: AlarmPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val silence = Runnable { onSilenced() }

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val graph = graph
        val id = intent?.getIntExtra(ClockReceiver.EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING_ALARM -> {
                val alarm = graph.alarms.get(id)
                if (alarm == null) {
                    // Must still satisfy startForegroundService's contract before stopping.
                    goForeground(null)
                    finish()
                } else {
                    ring(Ringing.Alarm(alarm.id, alarm.label, alarm.hour, alarm.minute, alarm.ringtone, alarm.vibrate))
                }
            }

            ACTION_RING_TIMER -> {
                val timer = graph.timers.get(id)
                if (timer == null) {
                    goForeground(null)
                    finish()
                } else {
                    ring(Ringing.Timer(timer.id, timer.label, timer.durationMillis))
                }
            }

            ACTION_DISMISS -> {
                when (val current = graph.ringing.value) {
                    is Ringing.Alarm -> graph.alarms.dismiss(current.id)
                    is Ringing.Timer -> graph.timers.reset(current.id)
                    null -> Unit
                }
                finish()
            }

            ACTION_SNOOZE -> {
                (graph.ringing.value as? Ringing.Alarm)?.let { graph.alarms.snooze(it.id) }
                finish()
            }

            ACTION_ADD_MINUTE -> {
                (graph.ringing.value as? Ringing.Timer)?.let { graph.timers.addMinute(it.id) }
                finish()
            }

            else -> {
                // Restarted after the process was killed while ringing: carry on.
                val current = graph.ringing.value
                if (current != null) ring(current) else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun ring(ringing: Ringing) {
        // A newer alarm or timer replaces whatever was ringing; the old one was already
        // rescheduled (alarms) or stays expired and counting up (timers).
        handler.removeCallbacks(silence)
        player.stop()
        graph.ringing.update { ringing }
        if (!goForeground(ringing)) {
            graph.notifications.showMissed(ringing)
            finish()
            return
        }
        acquireWakeLock()
        when (ringing) {
            is Ringing.Alarm -> player.start(ringing.sound, ringing.vibrate, graph.settings.value.crescendoSeconds * 1_000L)
            is Ringing.Timer -> player.start(sound = null, vibrate = true)
        }
        val minutes = graph.settings.value.silenceAfterMinutes
        if (minutes > 0) handler.postDelayed(silence, minutes * 60_000L)
    }

    /** @return false if Android refused to let the service run in the foreground. */
    private fun goForeground(ringing: Ringing?): Boolean =
        try {
            val notification = graph.notifications.ringing(ringing)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    ClockNotifications.RINGING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                )
            } else {
                // Foreground service types are only required (and systemExempted only exists) from API 34.
                startForeground(ClockNotifications.RINGING_NOTIFICATION_ID, notification)
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.e(TAG, "Not allowed to ring in the foreground", e)
            false
        }

    private fun onSilenced() {
        val current = graph.ringing.value
        if (current is Ringing.Alarm) graph.notifications.showMissed(current)
        finish()
    }

    private fun finish() {
        handler.removeCallbacks(silence)
        player.stop()
        graph.ringing.update { null }
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:ringing")
                .apply { acquire(MAX_RING_MILLIS) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(silence)
        player.stop()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RingingService"
        private const val MAX_RING_MILLIS = 60 * 60_000L

        const val ACTION_RING_ALARM = "io.github.michaelmassoni.tessera.clock.RING_ALARM"
        const val ACTION_RING_TIMER = "io.github.michaelmassoni.tessera.clock.RING_TIMER"
        const val ACTION_DISMISS = "io.github.michaelmassoni.tessera.clock.DISMISS"
        const val ACTION_SNOOZE = "io.github.michaelmassoni.tessera.clock.SNOOZE"
        const val ACTION_ADD_MINUTE = "io.github.michaelmassoni.tessera.clock.ADD_MINUTE"

        fun ringAlarm(
            context: Context,
            id: Int,
        ) = Intent(
            context,
            RingingService::class.java,
        ).setAction(ACTION_RING_ALARM).putExtra(ClockReceiver.EXTRA_ID, id)

        fun ringTimer(
            context: Context,
            id: Int,
        ) = Intent(
            context,
            RingingService::class.java,
        ).setAction(ACTION_RING_TIMER).putExtra(ClockReceiver.EXTRA_ID, id)

        fun command(
            context: Context,
            action: String,
        ) = Intent(context, RingingService::class.java).setAction(action)
    }
}
