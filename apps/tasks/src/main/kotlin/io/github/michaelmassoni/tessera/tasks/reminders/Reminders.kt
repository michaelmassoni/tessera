package io.github.michaelmassoni.tessera.tasks.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.tasks.MainActivity
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import io.github.michaelmassoni.tessera.tasks.tasks
import java.time.ZoneId
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Reminders at each open task's due time (or its snooze). One exact alarm is set, for the next
 * reminder; when it goes off every reminder that's due is shown and the next alarm set. Each
 * reminder ("task@time") is shown once, and only if it was expected (see [schedule]).
 */
object Reminders {
    const val CHANNEL = "reminders"
    private const val PREFS = "reminders"

    /** When [task] should remind, in ms, or null. */
    fun time(
        task: Task,
        zone: ZoneId,
    ): Long? {
        if (task.completed || task.deleted) return null
        task.snoozedUntil?.let { return it }
        val due = task.due ?: return null
        if (due.time == null) return null
        return due.dateTime
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun key(
        task: Task,
        at: Long,
    ) = "${task.id}@$at"

    /**
     * Sets the alarm for the next reminder still to come (or none), and notes every reminder that's
     * still to come as expected. Only expected reminders are ever shown: a time set in the past
     * (today's 9:00 entered at 10 pm) doesn't remind, but one missed while the phone was off does.
     */
    fun schedule(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val tasks = context.tasks.data.value.tasks
        val upcoming = tasks.mapNotNull { t -> time(t, zone)?.takeIf { it > now }?.let { key(t, it) to it } }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Expected reminders: those still to come, and earlier ones not yet shown (from the last day).
        val expected =
            expected(context).filter { (it.substringAfterLast('@').toLongOrNull() ?: 0) > now - LATE_MS }.toSet() +
                upcoming.map { it.first }
        prefs.edit { putStringSet(EXPECTED, expected) }
        val next = upcoming.minOfOrNull { it.second }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (next == null) {
            alarms.cancel(pending)
            return
        }
        // Exact when allowed (reminders for medications should be on time); otherwise Android may deliver it up to an hour late, which the list warns about.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    /** Shows every expected reminder that's due (including ones missed while the phone was off), then sets the next. */
    fun showDue(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val expected = expected(context).toMutableSet()
        val data = context.tasks.data.value
        data.tasks.forEach { t ->
            val at = time(t, zone) ?: return@forEach
            val key = key(t, at)
            // Never early: alarms don't go off early, and a reminder shown before its time would be
            // expected again by [schedule] and shown twice (as after a clock change just before it).
            if (at <= now && key in expected) {
                notify(context, t, data.list(t.listId)?.name.orEmpty())
                expected -= key
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(EXPECTED, expected) }
        schedule(context)
    }

    private fun expected(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(EXPECTED, emptySet()).orEmpty()

    private const val EXPECTED = "expected"

    private fun alarmIntent(context: Context) =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_reminders_description)
            },
        )
    }

    private fun notify(
        context: Context,
        task: Task,
        list: String,
    ) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val id = task.id.hashCode()
        val open =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_TASK, task.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun action(name: String) =
            PendingIntent.getBroadcast(
                context,
                id * 31 + name.hashCode(),
                Intent(context, ReminderReceiver::class.java).setAction(name).putExtra(ReminderReceiver.EXTRA_TASK, task.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(DesignR.drawable.ic_check_circle)
                .setContentTitle(task.title.ifBlank { context.getString(R.string.untitled) })
                .setContentText(
                    task.notes
                        .lineSequence()
                        .firstOrNull()
                        ?.ifBlank { null },
                ).setSubText(list)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(DesignR.drawable.ic_check, context.getString(R.string.done), action(ReminderReceiver.ACTION_DONE))
                .addAction(DesignR.drawable.ic_snooze, context.getString(R.string.snooze), action(ReminderReceiver.ACTION_SNOOZE))
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications turned off between the check and here.
        }
    }

    fun cancel(
        context: Context,
        taskId: String,
    ) = NotificationManagerCompat.from(context).cancel(taskId.hashCode())

    /** Shown up to a minute early (alarms may fire a little early), and up to a day late. */
    private const val LATE_MS = 24 * 60 * 60 * 1000L
    const val SNOOZE_MS = 10 * 60 * 1000L
}

/** The alarm, a reminder's Done and Snooze, and re-arming after a restart or time change. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val id = intent.getStringExtra(EXTRA_TASK)
        when (intent.action) {
            ACTION_DONE -> {
                if (id != null) {
                    val now = System.currentTimeMillis()
                    context.tasks.update { d ->
                        d.copy(tasks = d.tasks.map { if (it.id == id) TaskOps.complete(it, now, ZoneId.systemDefault()) else it })
                    }
                    Reminders.cancel(context, id)
                }
            }

            ACTION_SNOOZE -> {
                if (id != null) {
                    val until = System.currentTimeMillis() + Reminders.SNOOZE_MS
                    context.tasks.update { d -> d.copy(tasks = d.tasks.map { if (it.id == id) it.copy(snoozedUntil = until) else it }) }
                    Reminders.cancel(context, id)
                }
            }

            else -> {
                Reminders.showDue(context)
            }
        }
        Reminders.schedule(context)
    }

    companion object {
        const val ACTION_ALARM = "io.github.michaelmassoni.tessera.tasks.REMINDER"
        const val ACTION_DONE = "io.github.michaelmassoni.tessera.tasks.DONE"
        const val ACTION_SNOOZE = "io.github.michaelmassoni.tessera.tasks.SNOOZE"
        const val EXTRA_TASK = "task"
    }
}

/** After a restart, an app update or a change of time or time zone: show missed reminders and set the next. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in ACTIONS) return
        Reminders.showDue(context)
    }

    private companion object {
        val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
    }
}
