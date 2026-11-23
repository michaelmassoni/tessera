package io.github.michaelmassoni.tessera.weather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import io.github.michaelmassoni.tessera.weather.MainActivity
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.ForecastCache
import io.github.michaelmassoni.tessera.weather.data.PlaceStore
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastCodec
import io.github.michaelmassoni.tessera.weather.engine.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** What both weather widgets share: their places, forecasts, redraws and taps. */
object WeatherWidgets {
    private val kinds: List<Class<out GlanceAppWidget>> = listOf(WeatherWidget::class.java, DateWeatherWidget::class.java)

    private fun create(kind: Class<out GlanceAppWidget>): GlanceAppWidget =
        if (kind == DateWeatherWidget::class.java) DateWeatherWidget() else WeatherWidget()

    suspend fun placed(context: Context): Boolean = kinds.any { GlanceAppWidgetManager(context).getGlanceIds(it).isNotEmpty() }

    /** Redraws every placed weather widget, and sets the midnight redraw (the date widget's day). */
    suspend fun refresh(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        kinds.forEach { kind ->
            val ids = manager.getGlanceIds(kind)
            if (ids.isNotEmpty()) {
                val widget = create(kind)
                ids.forEach { widget.update(context, it) }
            }
        }
        if (manager.getGlanceIds(DateWeatherWidget::class.java).isNotEmpty()) armMidnight(context)
    }

    suspend fun settings(
        context: Context,
        id: GlanceId,
        defaults: WeatherWidgetSettings,
    ): WeatherWidgetSettings =
        WeatherWidgetSettings.from(
            runCatching {
                getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)
            }.getOrNull(),
            defaults,
        )

    /** The places the widgets show other than the app's main place (the background check fetches those too). */
    suspend fun otherPlaces(context: Context): List<Place> {
        val manager = GlanceAppWidgetManager(context)
        val ids =
            kinds.flatMap { kind -> manager.getGlanceIds(kind) }.mapNotNull { id ->
                runCatching { getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id) }
                    .getOrNull()
                    ?.let { WeatherWidgetSettings.from(it).place }
            }
        val main = WeatherJobs.primaryPlace(context)?.id
        return ids.distinct().filter { it != main }.mapNotNull { place(context, it) }
    }

    /** [id]'s place: a saved one, the current location, or (null, or one since removed) the app's main place. */
    fun place(
        context: Context,
        id: Long?,
    ): Place? {
        if (id == null) return WeatherJobs.primaryPlace(context)
        if (id == Place.HERE) return context.weatherSettings().lastFix ?: WeatherJobs.primaryPlace(context)
        return PlaceStore(File(context.filesDir, "places.json")).load().firstOrNull { it.id == id } ?: WeatherJobs.primaryPlace(context)
    }

    suspend fun forecast(
        context: Context,
        place: Place?,
    ): Forecast? =
        withContext(Dispatchers.IO) {
            place?.let { ForecastCache(File(context.filesDir, "forecasts")).read(it.id) }?.let {
                runCatching { ForecastCodec.decode(it.body) }.getOrNull()
            }
        }

    fun openApp(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The calendar app (Tessera Calendar or whichever is set), or Weather when there's none. */
    fun openCalendar(context: Context): Intent {
        val calendar =
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (calendar.resolveActivity(context.packageManager) != null) calendar else openApp(context)
    }

    /** Not a wake-up alarm: with the screen off nobody sees the widget, so it waits for the device to wake. */
    private fun armMidnight(context: Context) {
        val zone = ZoneId.systemDefault()
        val at =
            LocalDate
                .now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val intent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WeatherWidgetTick::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at + 1_000, intent)
    }

    internal fun async(
        receiver: BroadcastReceiver,
        block: suspend () -> Unit,
    ) {
        val pending = receiver.goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Both widgets' receivers: placing the first widget starts the background refresh (removing the
 * last stops it), and they redraw on time, time zone and locale changes.
 */
abstract class WeatherWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgets.async(this) {
            WeatherJobs.schedule(context.applicationContext)
            WeatherWidgets.refresh(context.applicationContext)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWidgets.async(this) { WeatherJobs.schedule(context.applicationContext) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                WeatherWidgets.async(this) { WeatherWidgets.refresh(context.applicationContext) }
            }
        }
    }
}

class WeatherWidgetReceiver : WeatherWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

class DateWeatherWidgetReceiver : WeatherWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = DateWeatherWidget()
}

/** The midnight redraw, so the date widget shows the new day. */
class WeatherWidgetTick : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        WeatherWidgets.async(this) { WeatherWidgets.refresh(context.applicationContext) }
    }
}
