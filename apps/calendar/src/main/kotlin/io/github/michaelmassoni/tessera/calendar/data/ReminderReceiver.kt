package io.github.michaelmassoni.tessera.calendar.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarAlerts
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.michaelmassoni.tessera.calendar.MainActivity
import io.github.michaelmassoni.tessera.calendar.R
import kotlin.concurrent.thread
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Posts event reminders. The calendar provider broadcasts EVENT_REMINDER when alerts are due; on
 * a phone without Google Calendar nothing else turns those into notifications. Each due,
 * scheduled alert is shown once and marked fired. Snoozing re-posts it later with an alarm.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        thread(name = "calendar-reminders") {
            try {
                when (intent.action) {
                    ACTION_SNOOZED -> {
                        notify(
                            context,
                            Reminder(
                                intent.getLongExtra(EXTRA_EVENT, 0),
                                intent.getLongExtra(EXTRA_BEGIN, 0),
                                intent.getLongExtra(EXTRA_END, 0),
                                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                                intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
                                intent.getBooleanExtra(EXTRA_ALL_DAY, false),
                            ),
                        )
                    }

                    ACTION_SNOOZE -> {
                        snooze(context, intent)
                    }

                    else -> {
                        fireDueAlerts(context)
                    }
                }
            } catch (_: SecurityException) {
                // Calendar access was revoked: nothing to show.
            } finally {
                pending.finish()
            }
        }
    }

    private data class Reminder(
        val eventId: Long,
        val begin: Long,
        val end: Long,
        val title: String,
        val location: String,
        val allDay: Boolean,
    )

    private fun fireDueAlerts(context: Context) {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val due = mutableListOf<Pair<Long, Reminder>>()
        resolver
            .query(
                CalendarAlerts.CONTENT_URI,
                arrayOf(
                    CalendarAlerts._ID,
                    CalendarAlerts.EVENT_ID,
                    CalendarAlerts.BEGIN,
                    CalendarAlerts.END,
                    CalendarAlerts.TITLE,
                    CalendarAlerts.EVENT_LOCATION,
                    CalendarAlerts.ALL_DAY,
                ),
                "${CalendarAlerts.STATE} = ? AND ${CalendarAlerts.ALARM_TIME} <= ?",
                arrayOf(CalendarAlerts.STATE_SCHEDULED.toString(), (now + GRACE_MILLIS).toString()),
                "${CalendarAlerts.ALARM_TIME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    due +=
                        c.getLong(0) to
                        Reminder(
                            c.getLong(1),
                            c.getLong(2),
                            c.getLong(3),
                            c.getString(4).orEmpty(),
                            c.getString(5).orEmpty(),
                            c.getInt(6) != 0,
                        )
                }
            }
        for ((alertId, reminder) in due) {
            notify(context, reminder)
            resolver.update(
                ContentUris.withAppendedId(CalendarAlerts.CONTENT_URI, alertId),
                ContentValues().apply { put(CalendarAlerts.STATE, CalendarAlerts.STATE_FIRED) },
                null,
                null,
            )
        }
    }

    private fun notify(
        context: Context,
        r: Reminder,
    ) {
        // Covers both the Android 13+ permission and notifications turned off in settings.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val id = r.eventId.toInt() xor r.begin.toInt()
        val open =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, r.eventId))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, r.begin)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val snooze =
            PendingIntent.getBroadcast(
                context,
                id,
                reminderIntent(context, ACTION_SNOOZE, r),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val time =
            if (r.allDay) {
                context.getString(R.string.all_day)
            } else {
                DateUtils.formatDateRange(context, r.begin, r.end, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY)
            }
        val text = listOf(time, r.location).filter { it.isNotBlank() }.joinToString(" · ")
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(DesignR.drawable.ic_calendar_month)
                .setContentTitle(r.title.ifBlank { context.getString(R.string.untitled) })
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.snooze), snooze)
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun snooze(
        context: Context,
        intent: Intent,
    ) {
        val r =
            Reminder(
                intent.getLongExtra(EXTRA_EVENT, 0),
                intent.getLongExtra(EXTRA_BEGIN, 0),
                intent.getLongExtra(EXTRA_END, 0),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_LOCATION).orEmpty(),
                intent.getBooleanExtra(EXTRA_ALL_DAY, false),
            )
        val id = r.eventId.toInt() xor r.begin.toInt()
        NotificationManagerCompat.from(context).cancel(id)
        val later =
            PendingIntent.getBroadcast(
                context,
                id,
                reminderIntent(context, ACTION_SNOOZED, r),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        // Inexact is fine for a 5-minute snooze and needs no exact-alarm permission.
        context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + SNOOZE_MILLIS,
            later,
        )
    }

    private fun reminderIntent(
        context: Context,
        action: String,
        r: Reminder,
    ) = Intent(context, ReminderReceiver::class.java)
        .setAction(action)
        .putExtra(EXTRA_EVENT, r.eventId)
        .putExtra(EXTRA_BEGIN, r.begin)
        .putExtra(EXTRA_END, r.end)
        .putExtra(EXTRA_TITLE, r.title)
        .putExtra(EXTRA_LOCATION, r.location)
        .putExtra(EXTRA_ALL_DAY, r.allDay)

    companion object {
        const val CHANNEL = "reminders"
        private const val ACTION_SNOOZE = "io.github.michaelmassoni.tessera.calendar.SNOOZE"
        private const val ACTION_SNOOZED = "io.github.michaelmassoni.tessera.calendar.SNOOZED"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_END = "end"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_ALL_DAY = "all_day"
        private const val SNOOZE_MILLIS = 5 * 60_000L
        private const val GRACE_MILLIS = 60_000L

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
