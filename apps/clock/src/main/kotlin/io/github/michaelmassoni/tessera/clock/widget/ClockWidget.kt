package io.github.michaelmassoni.tessera.clock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import io.github.michaelmassoni.tessera.clock.MainActivity
import io.github.michaelmassoni.tessera.clock.R
import java.util.Locale

/**
 * The time, date and next alarm. TextClock keeps the time and date current on its own, so the
 * widget is redrawn only when the next alarm or the locale changes.
 */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        manager.updateAppWidget(ids, views(context))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED, Intent.ACTION_LOCALE_CHANGED -> update(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
            views.setCharSequence(R.id.date, "setFormat12Hour", datePattern)
            views.setCharSequence(R.id.date, "setFormat24Hour", datePattern)
            views.setOnClickPendingIntent(R.id.time, open(context, MainActivity.TAB_WORLD))
            views.setOnClickPendingIntent(R.id.date, open(context, MainActivity.TAB_WORLD))
            val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock
            if (next == null) {
                views.setViewVisibility(R.id.next_alarm, View.GONE)
            } else {
                val skeleton = if (DateFormat.is24HourFormat(context)) "EEEHHmm" else "EEEhmma"
                val text = DateFormat.format(DateFormat.getBestDateTimePattern(locale, skeleton), next.triggerTime)
                views.setViewVisibility(R.id.next_alarm, View.VISIBLE)
                views.setTextViewText(R.id.next_alarm, text)
                views.setContentDescription(R.id.next_alarm, context.getString(R.string.widget_next_alarm, text))
                views.setOnClickPendingIntent(R.id.next_alarm, open(context, MainActivity.TAB_ALARMS))
            }
            return views
        }

        private fun open(
            context: Context,
            tab: Int,
        ) = PendingIntent.getActivity(
            context,
            WIDGET_REQUEST_OFFSET + tab,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val WIDGET_REQUEST_OFFSET = 3_000_000
    }
}
