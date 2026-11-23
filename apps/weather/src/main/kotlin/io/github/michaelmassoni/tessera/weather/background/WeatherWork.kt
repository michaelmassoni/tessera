package io.github.michaelmassoni.tessera.weather.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.weather.MainActivity
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.data.ForecastCache
import io.github.michaelmassoni.tessera.weather.data.ForecastLoader
import io.github.michaelmassoni.tessera.weather.data.PlaceStore
import io.github.michaelmassoni.tessera.weather.data.WeatherApi
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Alerts
import io.github.michaelmassoni.tessera.weather.engine.Bom
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastCodec
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.Warnings
import io.github.michaelmassoni.tessera.weather.ui.label
import io.github.michaelmassoni.tessera.weather.widget.WeatherWidgets
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Background work, only while something needs it: a check every 30 minutes for the widget, rain
 * alerts and warning alerts, and a morning forecast at 7:00. Uses the last known location (no
 * background location permission) or the first saved place.
 */
object WeatherJobs {
    private const val CHECK = "weather-check"
    private const val MORNING = "weather-morning"
    const val CHANNEL_FORECAST = "forecast"
    const val CHANNEL_RAIN = "rain"
    const val CHANNEL_WARNINGS = "warnings"
    private const val ID_MORNING = 1
    private const val ID_RAIN = 2
    private const val ID_WARNING_BASE = 100

    /** Brings the scheduled work in line with the settings and whether a widget is placed. */
    suspend fun schedule(
        context: Context,
        /** How a pending morning notification is treated: kept, replaced (settings changed), or followed (from itself). */
        morning: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val settings = context.weatherSettings()
        val work = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        if (settings.rainAlerts || settings.warningAlerts || WeatherWidgets.placed(context)) {
            work.enqueueUniquePeriodicWork(
                CHECK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CheckWorker>(30, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        } else {
            work.cancelUniqueWork(CHECK)
        }
        if (settings.morningForecast) {
            scheduleMorning(context, morning)
        } else {
            work.cancelUniqueWork(MORNING)
        }
    }

    private fun scheduleMorning(
        context: Context,
        policy: ExistingWorkPolicy,
    ) {
        val now = ZonedDateTime.now()
        var next = now.with(LocalTime.of(7, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request =
            OneTimeWorkRequestBuilder<MorningWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MORNING, policy, request)
    }

    /** The place background work is about: where you were last, or the first saved place. */
    fun primaryPlace(context: Context): Place? {
        val settings = context.weatherSettings()
        val here = settings.lastFix?.takeIf { settings.useLocation == true }
        return here ?: PlaceStore(File(context.filesDir, "places.json")).load().firstOrNull()
    }

    /** Fetches [place]'s forecast into the cache the app and widget read, and returns it. */
    suspend fun refresh(
        context: Context,
        place: Place,
    ): Forecast {
        val stored = ForecastLoader(WeatherApi()).load(place)
        ForecastCache(File(context.filesDir, "forecasts")).write(place.id, stored, System.currentTimeMillis())
        return ForecastCodec.decode(stored)
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FORECAST, context.getString(R.string.channel_forecast), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_RAIN, context.getString(R.string.channel_rain), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_WARNINGS, context.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannels(context)
        val open =
            PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_partly_cloudy_day)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    internal fun morningId() = ID_MORNING

    internal fun rainId() = ID_RAIN

    internal fun warningId(id: String) = ID_WARNING_BASE + Math.floorMod(id.hashCode(), 10_000)
}

/** Every 30 minutes, while needed: refreshes the forecast and widget, and tells of rain and new warnings. */
class CheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = context.weatherSettings()
        val place = WeatherJobs.primaryPlace(context) ?: return Result.success()
        val forecast = runCatching { WeatherJobs.refresh(context, place) }.getOrElse { return Result.retry() }
        // Places only a widget shows (set in its settings) are kept fresh too.
        WeatherWidgets.otherPlaces(context).forEach { runCatching { WeatherJobs.refresh(context, it) } }
        WeatherWidgets.refresh(context)
        val now = System.currentTimeMillis()
        if (settings.rainAlerts) {
            Alerts.rainSoon(forecast.hours, now)?.let { hour ->
                if (hour.time != settings.rainNotifiedFor) {
                    settings.rainNotifiedFor = hour.time
                    val minutes = ((hour.time - now) / 60_000).coerceAtLeast(0)
                    WeatherJobs.notify(
                        context,
                        WeatherJobs.rainId(),
                        WeatherJobs.CHANNEL_RAIN,
                        context.getString(R.string.rain_soon_title, place.name.ifBlank { context.getString(R.string.current_location) }),
                        context.resources.getQuantityString(
                            R.plurals.rain_soon_text,
                            minutes.toInt(),
                            hour.rainChance ?: 0,
                            minutes.toInt(),
                        ),
                    )
                }
            }
        }
        if (settings.warningAlerts && Bom.covers(place.latitude, place.longitude)) {
            runCatching {
                val warnings = Warnings.parse(WeatherApi().get(Warnings.listUrl(Bom.geohash(place.latitude, place.longitude))), now)
                Alerts.newWarnings(warnings, settings.seenWarnings).forEach { w ->
                    WeatherJobs.notify(context, WeatherJobs.warningId(w.id), WeatherJobs.CHANNEL_WARNINGS, w.shortTitle, w.title)
                }
                // Remember what's in force now; expired ones drop out, so the set stays small.
                settings.seenWarnings = warnings.map { it.id }.toSet()
            }
        }
        return Result.success()
    }
}

/** At 7:00: the day's forecast as a notification, then the next morning's is scheduled. */
class MorningWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = context.weatherSettings()
        if (!settings.morningForecast) return Result.success()
        val place = WeatherJobs.primaryPlace(context)
        if (place != null) {
            val forecast = runCatching { WeatherJobs.refresh(context, place) }.getOrElse { return Result.retry() }
            WeatherWidgets.refresh(context)
            val today = forecast.days.firstOrNull()
            if (today != null) {
                val unit = settings.units.temperature
                val name = place.name.ifBlank { context.getString(R.string.current_location) }
                val text =
                    buildString {
                        append(
                            context.getString(
                                R.string.morning_text,
                                context.getString(today.condition.label(true)),
                                Convert.temperature(today.high, unit),
                                Convert.temperature(today.low, unit),
                            ),
                        )
                        today.rainChance?.takeIf { it >= 20 }?.let { append(" ").append(context.getString(R.string.morning_rain, it)) }
                        forecast.summary?.let { append("\n").append(it) }
                    }
                WeatherJobs.notify(
                    context,
                    WeatherJobs.morningId(),
                    WeatherJobs.CHANNEL_FORECAST,
                    context.getString(R.string.morning_title, name),
                    text,
                )
            }
        }
        // Appended, so it runs after this one finishes instead of cancelling it.
        WeatherJobs.schedule(context, morning = ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
