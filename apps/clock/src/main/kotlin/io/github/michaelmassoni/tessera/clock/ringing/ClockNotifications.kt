package io.github.michaelmassoni.tessera.clock.ringing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import io.github.michaelmassoni.tessera.clock.MainActivity
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.stopwatch.Stopwatch
import io.github.michaelmassoni.tessera.clock.timer.ClockTimer
import io.github.michaelmassoni.tessera.clock.ui.DurationFormat
import java.util.Calendar

/** All notifications the clock posts. Channels are created at app start. */
class ClockNotifications(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RINGING,
                    context.getString(R.string.channel_ringing),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    // The service plays sound and vibration itself, on the alarm stream.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    CHANNEL_TIMERS,
                    context.getString(R.string.channel_timers),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_STOPWATCH,
                    context.getString(R.string.channel_stopwatch),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_MISSED,
                    context.getString(R.string.channel_missed),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun canPost(): Boolean = manager.areNotificationsEnabled()

    /** Android 14+: whether the ringing screen may appear over the lock screen. */
    fun canUseFullScreenIntent(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager.canUseFullScreenIntent()

    /** The ringing notification; [ringing] is null only for the brief placeholder shown while stopping. */
    fun ringing(ringing: Ringing?): Notification {
        val fullScreen =
            PendingIntent.getActivity(
                context,
                0,
                Intent(
                    context,
                    RingingActivity::class.java,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            Notification
                .Builder(context, CHANNEL_RINGING)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
        when (ringing) {
            is Ringing.Alarm -> {
                builder
                    .setContentTitle(ringing.label.ifEmpty { context.getString(R.string.alarm_default_label) })
                    .setContentText(formatTime(ringing.hour, ringing.minute))
                    .addAction(action(R.string.action_snooze, RingingService.ACTION_SNOOZE))
                    .addAction(action(R.string.action_dismiss, RingingService.ACTION_DISMISS))
            }

            is Ringing.Timer -> {
                builder
                    .setContentTitle(context.getString(R.string.timer_times_up))
                    .setContentText(ringing.label.ifEmpty { DurationFormat.timer(ringing.durationMillis) })
                    .addAction(action(R.string.action_add_minute, RingingService.ACTION_ADD_MINUTE))
                    .addAction(action(R.string.action_stop, RingingService.ACTION_DISMISS))
            }

            null -> {
                builder.setContentTitle(context.getString(R.string.app_name))
            }
        }
        return builder.build()
    }

    fun showMissed(ringing: Ringing) {
        if (ringing !is Ringing.Alarm || !canPost()) return
        val notification =
            Notification
                .Builder(context, CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentTitle(context.getString(R.string.missed_alarm))
                .setContentText(
                    listOf(
                        formatTime(ringing.hour, ringing.minute),
                        ringing.label,
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                ).setContentIntent(openTab(MainActivity.TAB_ALARMS))
                .setAutoCancel(true)
                .build()
        manager.notify(MISSED_NOTIFICATION_BASE + ringing.id, notification)
    }

    /** One ongoing notification per running or paused timer, with a live countdown. */
    fun updateTimers(
        timers: List<ClockTimer>,
        elapsedNow: Long,
    ) {
        val shown = timers.filter { it.state == ClockTimer.State.Running || it.state == ClockTimer.State.Paused }
        manager.activeNotifications
            .filter { it.id in TIMER_NOTIFICATION_BASE until TIMER_NOTIFICATION_BASE + ID_RANGE }
            .filter { n -> shown.none { TIMER_NOTIFICATION_BASE + it.id == n.id } }
            .forEach { manager.cancel(it.id) }
        if (!canPost()) return
        shown.forEach { timer ->
            val running = timer.state == ClockTimer.State.Running
            val builder =
                Notification
                    .Builder(context, CHANNEL_TIMERS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setCategory(Notification.CATEGORY_PROGRESS)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentTitle(timer.label.ifEmpty { context.getString(R.string.tab_timer) })
                    .setContentIntent(openTab(MainActivity.TAB_TIMER))
            if (running) {
                builder
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(System.currentTimeMillis() + timer.remaining(elapsedNow))
                    .setShowWhen(true)
                    .addAction(broadcast(R.string.action_pause, ClockReceiver.ACTION_TIMER_PAUSE, timer.id))
            } else {
                builder
                    .setContentText(
                        context.getString(R.string.timer_paused, DurationFormat.timer(timer.remaining(elapsedNow))),
                    ).setShowWhen(false)
                    .addAction(broadcast(R.string.action_resume, ClockReceiver.ACTION_TIMER_RESUME, timer.id))
            }
            builder.addAction(broadcast(R.string.action_stop, ClockReceiver.ACTION_TIMER_STOP, timer.id))
            manager.notify(TIMER_NOTIFICATION_BASE + timer.id, builder.build())
        }
    }

    fun updateStopwatch(
        stopwatch: Stopwatch,
        elapsedNow: Long,
        wallNow: Long,
    ) {
        if (stopwatch.isReset || !canPost()) {
            manager.cancel(STOPWATCH_NOTIFICATION_ID)
            return
        }
        val builder =
            Notification
                .Builder(context, CHANNEL_STOPWATCH)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setOngoing(stopwatch.running)
                .setOnlyAlertOnce(true)
                .setContentTitle(context.getString(R.string.tab_stopwatch))
                .setContentIntent(openTab(MainActivity.TAB_STOPWATCH))
        if (stopwatch.running) {
            builder
                .setUsesChronometer(true)
                .setWhen(wallNow - stopwatch.elapsed(elapsedNow))
                .setShowWhen(true)
                .addAction(broadcast(R.string.action_pause, ClockReceiver.ACTION_STOPWATCH_PAUSE, 0))
                .addAction(broadcast(R.string.action_lap, ClockReceiver.ACTION_STOPWATCH_LAP, 0))
        } else {
            builder
                .setContentText(DurationFormat.stopwatch(stopwatch.elapsed(elapsedNow)))
                .setShowWhen(false)
                .addAction(broadcast(R.string.action_resume, ClockReceiver.ACTION_STOPWATCH_RESUME, 0))
                .addAction(broadcast(R.string.action_reset, ClockReceiver.ACTION_STOPWATCH_RESET, 0))
        }
        manager.notify(STOPWATCH_NOTIFICATION_ID, builder.build())
    }

    private fun formatTime(
        hour: Int,
        minute: Int,
    ): String {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    private fun action(
        title: Int,
        serviceAction: String,
    ): Notification.Action {
        val intent =
            PendingIntent.getService(
                context,
                serviceAction.hashCode(),
                RingingService.command(context, serviceAction),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Action.Builder(null, context.getString(title), intent).build()
    }

    private fun broadcast(
        title: Int,
        action: String,
        id: Int,
    ): Notification.Action {
        val intent =
            PendingIntent.getBroadcast(
                context,
                action.hashCode() + id,
                Intent(context, ClockReceiver::class.java).setAction(action).putExtra(ClockReceiver.EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Action.Builder(null, context.getString(title), intent).build()
    }

    private fun openTab(tab: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            tab,
            Intent(
                context,
                MainActivity::class.java,
            ).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_TIMERS = "timers"
        const val CHANNEL_STOPWATCH = "stopwatch"
        const val CHANNEL_MISSED = "missed"

        const val RINGING_NOTIFICATION_ID = 1
        const val STOPWATCH_NOTIFICATION_ID = 2
        const val TIMER_NOTIFICATION_BASE = 10_000
        const val MISSED_NOTIFICATION_BASE = 20_000
        private const val ID_RANGE = 10_000
    }
}
