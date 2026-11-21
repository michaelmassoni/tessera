package io.github.michaelmassoni.tessera.calendar.widget

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What the launcher's widget picker shows: each widget drawn with a few made-up events. */
internal object WidgetPreview {
    /** A morning with a few events today and in the coming days, in the theme's colour. */
    fun data(context: Context): WidgetData {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        fun at(
            day: Long,
            hour: Int,
            minute: Int = 0,
        ) = today
            .plusDays(day)
            .atTime(LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        fun event(
            id: Long,
            title: Int,
            begin: Long,
            end: Long,
            location: Int? = null,
        ) = Instance(
            eventId = id,
            begin = begin,
            end = end,
            title = context.getString(title),
            allDay = false,
            color = 0,
            calendarId = 0,
            location = location?.let { context.getString(it) }.orEmpty(),
        )
        val instances =
            listOf(
                event(1, R.string.widget_preview_standup, at(0, 9), at(0, 9, 30)),
                event(2, R.string.widget_preview_lunch, at(0, 12, 30), at(0, 13, 30), R.string.widget_preview_lunch_place),
                event(3, R.string.widget_preview_gym, at(1, 18), at(1, 19)),
                event(4, R.string.widget_preview_dentist, at(3, 10), at(3, 11)),
            )
        return WidgetData(allowed = true, instances = instances, now = at(0, 8), zone = zone)
    }

    /**
     * Hands the picker previews of all three widgets (Android 15 and later; older launchers show
     * the app icon). The system rate-limits this, so it's done once per app version, and tried
     * again next time if it didn't get through.
     */
    suspend fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        if (prefs.getLong(KEY_VERSION, -1) == version) return
        val manager = GlanceAppWidgetManager(context)
        val results =
            listOf(AgendaWidgetReceiver::class, MonthWidgetReceiver::class, DateWidgetReceiver::class).map {
                runCatching { manager.setWidgetPreviews(it) }.getOrDefault(GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED)
            }
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs.edit { putLong(KEY_VERSION, version) }
        }
    }

    private const val PREFS = "widgets"
    private const val KEY_VERSION = "previews_version"
}
