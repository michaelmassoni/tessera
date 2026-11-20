package io.github.michaelmassoni.tessera.clock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.michaelmassoni.tessera.clock.MainActivity
import io.github.michaelmassoni.tessera.clock.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * The time and date, with the next alarm underneath, as on Pixel. The time and date are
 * TextClocks (they tick without waking the app); the alarm line updates when the system says the
 * next alarm changed.
 */
class ClockWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val next = nextAlarm(context)
        provideContent { GlanceTheme { Content(context, next) } }
    }

    @Composable
    private fun Content(
        context: Context,
        next: String?,
    ) {
        Box(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity(tab(context, MainActivity.TAB_WORLD))),
        ) {
            AndroidRemoteViews(views(context, next), modifier = GlanceModifier.fillMaxSize())
        }
    }

    private fun tab(
        context: Context,
        tab: Int,
    ) = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /**
     * The time and date (TextClocks, in the locale's own order: "Fri, Nov 20", "Fr., 20. Nov.")
     * and, when one is set, the next alarm, which opens the Alarm tab.
     */
    private fun views(
        context: Context,
        next: String?,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_clock_time).apply {
            val date = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
            setCharSequence(R.id.widget_date, "setFormat12Hour", date)
            setCharSequence(R.id.widget_date, "setFormat24Hour", date)
            if (next != null) {
                setViewVisibility(R.id.widget_alarm, View.VISIBLE)
                setTextViewText(R.id.widget_alarm, next)
                setContentDescription(R.id.widget_alarm, context.getString(R.string.widget_next_alarm, next))
                setOnClickPendingIntent(
                    R.id.widget_alarm,
                    PendingIntent.getActivity(
                        context,
                        MainActivity.TAB_ALARMS,
                        tab(context, MainActivity.TAB_ALARMS),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }

    companion object {
        /** The next alarm set by any app, as "Fri 6:45 AM", or null. */
        fun nextAlarm(context: Context): String? {
            val next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
            val skeleton = if (DateFormat.is24HourFormat(context)) "EEEHHmm" else "EEEhmma"
            return DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), next.triggerTime).toString()
        }
    }
}

/** Redraws the widget when the next alarm changes (from this app or any other). */
class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_LOCALE_CHANGED
        ) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (GlanceAppWidgetManager(context).getGlanceIds(ClockWidget::class.java).isNotEmpty()) {
                        ClockWidget().updateAll(context)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
