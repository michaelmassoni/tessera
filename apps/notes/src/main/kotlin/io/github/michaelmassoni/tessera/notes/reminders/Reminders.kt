package io.github.michaelmassoni.tessera.notes.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.notes.MainActivity
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.ReminderPlan
import io.github.michaelmassoni.tessera.notes.notes
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Note reminders (docs/notes.md, Reminders): one alarm for the next one; when it goes off, every
 * expected reminder that's due is shown ([ReminderPlan]) and the next alarm set.
 */
object Reminders {
    const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val EXPECTED = "expected"

    private fun expected(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(EXPECTED, emptySet()).orEmpty()

    private fun saveExpected(
        context: Context,
        expected: Set<String>,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(EXPECTED, expected) }

    /** Sets the alarm for the next reminder (or none). Call after any note's reminder changes. */
    fun schedule(context: Context) {
        val repository = context.notes
        repository.load()
        val notes = repository.notes.value.values
        val now = System.currentTimeMillis()
        saveExpected(context, ReminderPlan.expected(notes, expected(context), now))
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        val next = ReminderPlan.nextAlarm(notes, now)
        if (next == null) {
            alarms.cancel(pending)
        } else if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            // Without "Alarms & reminders" Android may deliver it a little late; the editor says so.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    /** Shows every expected reminder that's due (including ones missed while off), then sets the next. */
    fun showDue(context: Context) {
        val repository = context.notes
        repository.load()
        val (due, left) = ReminderPlan.due(repository.notes.value.values, expected(context), System.currentTimeMillis())
        due.forEach { notify(context, it) }
        saveExpected(context, left)
        schedule(context)
    }

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
        note: Note,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val id = note.id.hashCode()
        val open =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_NOTE)
                    .putExtra(MainActivity.EXTRA_NOTE, note.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val done =
            PendingIntent.getBroadcast(
                context,
                id,
                Intent(
                    context,
                    ReminderReceiver::class.java,
                ).setAction(ReminderReceiver.ACTION_DONE).putExtra(ReminderReceiver.EXTRA_NOTE, note.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text =
            note.checklist
                ?.filterNot { it.checked }
                ?.joinToString(", ") { it.text }
                ?: note.body
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(DesignR.drawable.ic_notifications)
                .setContentTitle(
                    note.title.ifBlank { text.lineSequence().firstOrNull().orEmpty() }.ifBlank { context.getString(R.string.reminder) },
                ).setContentText(if (note.title.isBlank()) null else text.lineSequence().firstOrNull())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(MAX_TEXT)))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(DesignR.drawable.ic_check, context.getString(R.string.reminder_done), done)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notifications turned off between the check and here.
        }
    }

    fun cancelNotification(
        context: Context,
        noteId: String,
    ) = NotificationManagerCompat.from(context).cancel(noteId.hashCode())

    private fun alarmIntent(context: Context) =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val MAX_TEXT = 500
}

/** The alarm, and a reminder's Done (which clears the note's reminder). Only this app sends them. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_DONE -> {
                val id = intent.getStringExtra(EXTRA_NOTE) ?: return
                context.notes.load()
                context.notes.update(listOf(id)) { it.copy(reminderAt = null) }
                Reminders.cancelNotification(context, id)
                Reminders.schedule(context)
            }

            ACTION_ALARM -> {
                Reminders.showDue(context)
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "io.github.michaelmassoni.tessera.notes.REMINDER"
        const val ACTION_DONE = "io.github.michaelmassoni.tessera.notes.REMINDER_DONE"
        const val EXTRA_NOTE = "note"
    }
}

/** After a restart, an app update or a change of time or time zone: show missed reminders, set the next. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action in ACTIONS) Reminders.showDue(context)
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
