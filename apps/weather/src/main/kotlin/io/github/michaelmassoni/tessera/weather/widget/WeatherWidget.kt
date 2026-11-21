package io.github.michaelmassoni.tessera.weather.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.michaelmassoni.tessera.weather.MainActivity
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.ForecastCache
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastCodec
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.Timeline
import io.github.michaelmassoni.tessera.weather.ui.icon
import io.github.michaelmassoni.tessera.weather.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Conditions now and the next hours for the background place (last location or first saved
 * place), from the cache the app keeps. The background check refreshes it every 30 minutes while
 * a widget is placed; the app redraws it whenever it fetches.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val place = WeatherJobs.primaryPlace(context)
        val forecast =
            withContext(Dispatchers.IO) {
                place?.let { ForecastCache(File(context.filesDir, "forecasts")).read(it.id) }?.let {
                    runCatching { ForecastCodec.decode(it.body) }.getOrNull()
                }
            }
        val unit = context.weatherSettings().units.temperature
        val name = place?.name?.ifBlank { null } ?: context.getString(R.string.current_location)
        provideContent { GlanceTheme { Content(context, name, forecast, unit) } }
    }

    @Composable
    private fun Content(
        context: Context,
        name: String,
        forecast: Forecast?,
        unit: TemperatureUnit,
    ) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(16.dp)
                .clickable(open),
        ) {
            if (forecast == null) {
                Text(
                    context.getString(R.string.widget_open_app),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
                return@Column
            }
            val current = forecast.current
            val today = forecast.days.firstOrNull()
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        name,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        context.getString(R.string.degrees, Convert.temperature(current.temperature, unit)),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 40.sp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Image(
                        ImageProvider(current.condition.icon(current.isDay)),
                        contentDescription = context.getString(current.condition.label(current.isDay)),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier.size(40.dp),
                    )
                    if (today != null) {
                        Text(
                            context.getString(
                                R.string.high_low,
                                context.getString(R.string.degrees, Convert.temperature(today.high, unit)),
                                context.getString(R.string.degrees, Convert.temperature(today.low, unit)),
                            ),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
            if (LocalSize.current.width >= WIDE.width) {
                Spacer(GlanceModifier.height(8.dp))
                val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                Row(GlanceModifier.fillMaxWidth()) {
                    Timeline.nextHours(forecast.hours, System.currentTimeMillis()).take(HOURS).forEach { h ->
                        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                time.format(Instant.ofEpochMilli(h.time).atZone(forecast.zone)),
                                maxLines = 1,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                    ),
                            )
                            Image(
                                ImageProvider(h.condition.icon(h.isDay)),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                                modifier = GlanceModifier.size(20.dp),
                            )
                            Text(
                                context.getString(R.string.degrees, Convert.temperature(h.temperature, unit)),
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 100.dp)
        private val WIDE = DpSize(250.dp, 100.dp)
        private const val HOURS = 5

        suspend fun placed(context: Context) = GlanceAppWidgetManager(context).getGlanceIds(WeatherWidget::class.java).isNotEmpty()

        suspend fun refresh(context: Context) {
            if (placed(context)) WeatherWidget().updateAll(context)
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    /** Placing the first widget starts the background refresh; removing the last stops it. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        reschedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        reschedule(context)
    }

    private fun reschedule(context: Context) {
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            try {
                WeatherJobs.schedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
