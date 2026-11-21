package io.github.michaelmassoni.tessera.calendar.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.calendar.MainActivity
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.data.CalendarRepository
import io.github.michaelmassoni.tessera.calendar.engine.DayLayout
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.RelativeDay
import io.github.michaelmassoni.tessera.calendar.engine.WidgetPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** What a calendar widget shows: the events in a range, or that it can't read the calendar yet. */
internal data class WidgetData(
    val allowed: Boolean,
    val instances: List<Instance> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Read when loading, not while drawing: widgets redraw on a locale change anyway. */
    val locale: Locale = Locale.getDefault(),
) {
    val today: LocalDate get() =
        java.time.Instant
            .ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
}

internal object CalendarWidgets {
    fun allowed(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the start of [first] to the end of [last], in visible calendars. */
    fun load(
        context: Context,
        first: LocalDate,
        last: LocalDate,
        /** Calendars a widget leaves out. */
        hidden: Set<Long> = emptySet(),
    ): WidgetData {
        if (!allowed(context)) return WidgetData(allowed = false)
        val zone = ZoneId.systemDefault()
        val (begin, end) = DayLayout.range(first, last, zone)
        val instances =
            runCatching { CalendarRepository(context.contentResolver).instances(begin, end) }
                .getOrDefault(emptyList())
                .filter { it.calendarId !in hidden }
        watch(context)
        return WidgetData(allowed = true, instances = instances, zone = zone).also { arm(context, it) }
    }

    /**
     * Redraws every calendar widget (each redraw also makes sure calendar changes are watched).
     * Returns whether there were any.
     */
    suspend fun refresh(context: Context): Boolean {
        val month = ids<MonthWidgetReceiver>(context)
        // A month widget that's running only reloads its events when its state changes.
        val stamp = System.currentTimeMillis()
        month.forEach { id -> updateAppWidgetState(context, id) { it[MonthWidgetSettings.STAMP] = stamp } }
        // Each widget by its own receiver's ids, not Glance's getGlanceIds/updateAll: those find ids
        // by a class name Glance saved on the device, which a release with merged widget classes
        // (0.5.0) left wrong, and updating an id with the wrong widget redraws it as that widget.
        val placed =
            listOf(
                AgendaWidget() to ids<AgendaWidgetReceiver>(context),
                MonthWidget() to month,
                DateWidget() to ids<DateWidgetReceiver>(context),
            )
        placed.forEach { (widget, ids) -> ids.forEach { widget.update(context, it) } }
        return placed.any { it.second.isNotEmpty() }
    }

    private inline fun <reified R : CalendarWidgetReceiver> ids(context: Context): List<GlanceId> {
        val manager = GlanceAppWidgetManager(context)
        return AppWidgetManager
            .getInstance(context)
            .getAppWidgetIds(ComponentName(context, R::class.java))
            .map(manager::getGlanceIdBy)
    }

    fun refreshSoon(context: Context) {
        CoroutineScope(Dispatchers.Default).launch { refresh(context) }
    }

    /**
     * A job that runs when anything in the calendar changes (an edit here, a sync from DAVx⁵ or
     * another app), even with Calendar closed. Content-triggered jobs fire once, so each run sets
     * the next one up: [CalendarChangeWorker] appends it behind itself, since replacing running
     * work would cancel it mid-redraw. Everywhere else an existing watch is kept.
     */
    fun watch(
        context: Context,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        if (!allowed(context)) return
        val request =
            OneTimeWorkRequestBuilder<CalendarChangeWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(Duration.ofSeconds(2))
                        .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                        .build(),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(CHANGE_WORK, policy, request)
    }

    private const val CHANGE_WORK = "calendar-widgets-watch"

    /**
     * Redraws when the content changes by itself: at midnight, or when an event ends. Not a
     * wake-up alarm: with the screen off nobody sees the widget, so it waits for the device to
     * wake.
     */
    private fun arm(
        context: Context,
        data: WidgetData,
    ) {
        val at = WidgetPlan.nextRedraw(data.instances, data.now, data.zone)
        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CalendarWidgetTick::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at + 1_000, intent)
    }

    // Opening the app.

    fun openApp(context: Context) =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun openDay(
        context: Context,
        day: LocalDate,
    ) = openApp(context).putExtra(MainActivity.EXTRA_DAY, day.toEpochDay()).setData("tessera-day:${day.toEpochDay()}".toUri())

    fun openEvent(
        context: Context,
        instance: Instance,
    ) = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, instance.eventId))
        .setClass(context, MainActivity::class.java)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, instance.begin)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** A new event on today (not whichever day the app last had selected). */
    fun newEvent(context: Context) =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .setClass(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DAY, LocalDate.now().toEpochDay())

    // Words.

    /** "Today", "Tomorrow", or "Mon, Nov 23" in the locale's order. */
    fun dayLabel(
        context: Context,
        day: LocalDate,
        today: LocalDate,
    ): String =
        when (WidgetPlan.relativeDay(day, today)) {
            RelativeDay.Today -> context.getString(R.string.widget_today)
            RelativeDay.Tomorrow -> context.getString(R.string.widget_tomorrow)
            null -> pattern("EEEMMMd").format(day)
        }

    /** "9:00 – 9:30 AM", or "All day". */
    fun timeLabel(
        context: Context,
        instance: Instance,
    ): String =
        if (instance.allDay) {
            context.getString(R.string.all_day)
        } else {
            DateUtils.formatDateRange(context, instance.begin, instance.end, DateUtils.FORMAT_SHOW_TIME)
        }

    fun pattern(skeleton: String): java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
}

/** Every calendar widget's receiver also redraws on time, time zone and locale changes. */
abstract class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        CalendarWidgets.refresh(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

class AgendaWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

class MonthWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

class DateWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateWidget()
}

/** The midnight (or event-end) redraw. */
class CalendarWidgetTick : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                CalendarWidgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Runs when the calendar changes, redraws the widgets, and watches again. */
class CalendarChangeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // With no widgets left, stop watching: the next widget placed starts it again.
        if (CalendarWidgets.refresh(applicationContext)) {
            CalendarWidgets.watch(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}

/** The month widget's arrows and today button: move the month ([Delta] 0 is back to this month). */
class MonthNavigation : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val delta = parameters[Delta] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MonthWidgetSettings.OFFSET] = if (delta == 0) 0 else (prefs[MonthWidgetSettings.OFFSET] ?: 0) + delta
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val Delta = ActionParameters.Key<Int>("delta")
    }
}
