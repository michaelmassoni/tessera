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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.ui.icon
import io.github.michaelmassoni.tessera.weather.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Today's date in large type with the weather now under it, like the At a Glance line on Pixel
 * home screens. The date opens the calendar app, the weather opens Weather. Redraws at midnight.
 */
class DateWeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Glance calls this on the main thread: read settings and the cached forecast off it.
        val (settings, place, forecast, unit) =
            withContext(Dispatchers.IO) {
                val settings = WeatherWidgets.settings(context, id, WeatherWidgetSettings.DATE_DEFAULTS)
                val place = WeatherWidgets.place(context, settings.place)
                val forecast = WeatherWidgets.forecast(context, place)
                val unit = context.weatherSettings().units.temperature
                Loaded(settings, place, forecast, unit)
            }
        val name = place?.name?.ifBlank { null } ?: context.getString(R.string.current_location)
        val (scheme, background) = settings.colors(context)
        val locale = Locale.getDefault()
        val skeleton = if (settings.dateStyle == DateStyle.Long) "EEEEMMMMd" else "EEEMMMd"
        val date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(LocalDate.now())
        provideContent { GlanceTheme(colors = scheme) { Content(context, date, name, forecast, unit, settings, background) } }
    }

    @Composable
    private fun Content(
        context: Context,
        date: String,
        name: String,
        forecast: Forecast?,
        unit: TemperatureUnit,
        settings: WeatherWidgetSettings,
        background: ColorProvider,
    ) {
        val large = LocalSize.current.width >= LARGE.width && LocalSize.current.height >= LARGE.height
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = dateSize(date, LocalSize.current.width.value, large).sp),
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(WeatherWidgets.openCalendar(context))),
            )
            Row(
                GlanceModifier.fillMaxWidth().padding(top = 4.dp).clickable(actionStartActivity(WeatherWidgets.openApp(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (forecast == null) {
                    Text(
                        context.getString(R.string.widget_open_app),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                    return@Row
                }

                fun deg(c: Double) = context.getString(R.string.degrees, Convert.temperature(c, unit))
                val current = forecast.current
                val today = forecast.days.firstOrNull()
                val condition = context.getString(current.condition.label(current.isDay))
                Image(
                    ImageProvider(current.condition.icon(current.isDay)),
                    contentDescription = condition,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(if (large) 28.dp else 22.dp),
                )
                val parts =
                    listOfNotNull(
                        condition.takeIf { settings.showCondition },
                        today?.takeIf { settings.showHighLow }?.let { context.getString(R.string.high_low, deg(it.high), deg(it.low)) },
                        name.takeIf { settings.showName },
                    )
                Text(
                    deg(current.temperature),
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = if (large) 20.sp else 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    modifier = GlanceModifier.padding(start = 6.dp),
                )
                if (parts.isNotEmpty()) {
                    Text(
                        " · " + parts.joinToString(" · "),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (large) 16.sp else 14.sp),
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Glance can't measure text, so the date's size comes from the room it has: about 0.6em
         * a character, between 16 and 30sp (22 when the widget is small).
         */
        internal fun dateSize(
            date: String,
            widthDp: Float,
            large: Boolean,
        ): Float = ((widthDp - 32) / (date.length * 0.6f)).coerceIn(16f, if (large) 30f else 22f)

        private val SMALL = DpSize(180.dp, 60.dp)
        private val LARGE = DpSize(250.dp, 100.dp)
    }
}
