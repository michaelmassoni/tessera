package io.github.michaelmassoni.tessera.weather.widget

import android.content.Context
import android.text.format.DateFormat
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.Timeline
import io.github.michaelmassoni.tessera.weather.ui.icon
import io.github.michaelmassoni.tessera.weather.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Conditions now, and (as room allows and its settings say) the next hours and days, for its
 * place: the app's main place unless set to another. The background check refreshes it every 30
 * minutes while a widget is placed; the app redraws it whenever it fetches.
 */
class WeatherWidget : GlanceAppWidget() {
    // Exact: the days fill whatever height it has (Responsive rounds down to the nearest size).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Glance calls this on the main thread: read settings and the cached forecast off it.
        val (settings, place, forecast, unit) =
            withContext(Dispatchers.IO) {
                val settings = WeatherWidgets.settings(context, id, WeatherWidgetSettings())
                val place = WeatherWidgets.place(context, settings.place)
                val forecast = WeatherWidgets.forecast(context, place)
                val unit = context.weatherSettings().units.temperature
                Loaded(settings, place, forecast, unit)
            }
        val name = place?.name?.ifBlank { null } ?: context.getString(R.string.current_location)
        val (scheme, background) = settings.colors(context)
        provideContent { GlanceTheme(colors = scheme) { Content(context, name, forecast, unit, settings, background) } }
    }

    @Composable
    private fun Content(
        context: Context,
        name: String,
        forecast: Forecast?,
        unit: TemperatureUnit,
        settings: WeatherWidgetSettings,
        background: ColorProvider,
    ) {
        val size = LocalSize.current
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(background)
                .padding(16.dp)
                .clickable(actionStartActivity(WeatherWidgets.openApp(context))),
        ) {
            if (forecast == null) {
                Text(
                    context.getString(R.string.widget_open_app),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
                return@Column
            }

            fun deg(c: Double) = context.getString(R.string.degrees, Convert.temperature(c, unit))
            val current = forecast.current
            val today = forecast.days.firstOrNull()
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    if (settings.showName) {
                        Text(
                            name,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    Text(deg(current.temperature), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 40.sp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Image(
                        ImageProvider(current.condition.icon(current.isDay)),
                        contentDescription = context.getString(current.condition.label(current.isDay)),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                        modifier = GlanceModifier.size(40.dp),
                    )
                    if (today != null && settings.showHighLow) {
                        Text(
                            context.getString(R.string.high_low, deg(today.high), deg(today.low)),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
            if (settings.showHours && size.width >= WIDE.width) {
                Spacer(GlanceModifier.height(8.dp))
                val time = hourFormat(context)
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
                                deg(h.temperature),
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
            if (settings.showDays && size.height >= TALL.height) {
                Spacer(GlanceModifier.height(8.dp))
                // Rows as fit under what's above (about 28dp each), at most a week.
                val used = if (settings.showHours) 150 else 90
                val fit = ((size.height.value - used - 32) / 28).toInt().coerceIn(1, DAYS)
                val todayDate = LocalDate.now(forecast.zone)
                val dayName = dayFormat()
                // In their own column: a Glance column keeps only its first 10 children.
                Column(GlanceModifier.fillMaxWidth()) {
                    forecast.days.take(fit).forEach { d ->
                        Row(GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (d.date == todayDate) context.getString(R.string.today) else dayName.format(d.date),
                                maxLines = 1,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                            Image(
                                ImageProvider(d.condition.icon(true)),
                                contentDescription = context.getString(d.condition.label(true)),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                                modifier = GlanceModifier.size(20.dp),
                            )
                            Text(
                                deg(d.low),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.End),
                                modifier = GlanceModifier.width(44.dp),
                            )
                            Text(
                                deg(d.high),
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.End),
                                modifier = GlanceModifier.width(44.dp),
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
        private val TALL = DpSize(250.dp, 240.dp)
        private const val HOURS = 5
        private const val DAYS = 8

        /** "Mon". */
        fun dayFormat(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

        /** "15:00" or "3 PM", as the clock is set. */
        fun hourFormat(context: Context): DateTimeFormatter =
            DateTimeFormatter.ofPattern(
                DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(context)) "Hmm" else "ha"),
                Locale.getDefault(),
            )
    }
}
