package io.github.michaelmassoni.tessera.weather.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.michaelmassoni.tessera.weather.PlacePage
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.engine.AirQuality
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.Day
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastSource
import io.github.michaelmassoni.tessera.weather.engine.Hour
import io.github.michaelmassoni.tessera.weather.engine.Metric
import io.github.michaelmassoni.tessera.weather.engine.Ranges
import io.github.michaelmassoni.tessera.weather.engine.Timeline
import io.github.michaelmassoni.tessera.weather.engine.Units
import io.github.michaelmassoni.tessera.weather.engine.Uv
import io.github.michaelmassoni.tessera.weather.engine.Warning
import io.github.michaelmassoni.tessera.weather.engine.Wind
import io.github.michaelmassoni.tessera.weather.engine.WindUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** One place's forecast: the hero, the next 24 hours, 10 days and the details. Pull to refresh. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ForecastPage(
    page: PlacePage,
    placeName: String,
    units: Units,
    now: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opens a day's details on a measure (from a day, an hour or a tile). */
    onDetail: (LocalDate, Metric) -> Unit = { _, _ -> },
    /** Opens a BOM warning's text. */
    onWarning: (Warning) -> Unit = {},
) {
    val forecast = page.forecast
    if (forecast == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingIndicator()
                Text(
                    stringResource(if (page.failed) R.string.offline else R.string.no_forecast_yet),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    PullToRefreshBox(isRefreshing = page.loading, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero") { Hero(forecast, placeName, units) }
            items(page.warnings, key = { "w${it.id}" }) { WarningBanner(it) { onWarning(it) } }
            forecast.summary?.let { summary -> item(key = "summary") { Summary(summary) } }
            item(key = "hours") { Hours(Timeline.nextHours(forecast.hours, now), forecast.zone, units, onDetail) }
            item(key = "days") { Days(forecast, units, onDetail) }
            item(key = "details") { Details(forecast, units, now, page.air, onDetail) }
            item(key = "footer") { Footer(page.fetchedAt, now, forecast) }
        }
    }
}

private val ContentWidth = 640.dp

@Composable
private fun Hero(
    forecast: Forecast,
    placeName: String,
    units: Units,
) {
    val current = forecast.current
    val today = forecast.days.firstOrNull()
    val condition = stringResource(current.condition.label(current.isDay))
    val temperature = temp(current.temperature, units)
    val high = today?.let { temp(it.high, units) }.orEmpty()
    val low = today?.let { temp(it.low, units) }.orEmpty()
    val description = stringResource(R.string.hero_description, placeName, temperature, condition, high, low)
    Column(
        Modifier
            .widthIn(max = ContentWidth)
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                temperature,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, lineHeight = 120.sp, fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painterResource(current.condition.icon(current.isDay)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(72.dp),
            )
        }
        Text(condition, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (today != null) {
            Text(
                stringResource(R.string.high_low, high, low),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            stringResource(R.string.feels_like, temp(current.feelsLike, units)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A titled, rounded tonal card. */
@Composable
private fun Card(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier.widthIn(max = ContentWidth).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
internal fun hourFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "HH" else "ha"),
            locale,
        )
    }
}

@Composable
private fun timeFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hmm" else "hmma"),
            locale,
        )
    }
}

@Composable
private fun Hours(
    hours: List<Hour>,
    zone: ZoneId,
    units: Units,
    onDetail: (LocalDate, Metric) -> Unit,
) {
    val format = hourFormat()
    // Only make room for rain chances when some hour has one worth showing.
    val anyRain = hours.any { (it.rainChance ?: 0) >= 20 }
    Card(stringResource(R.string.next_24_hours)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hours, key = { it.time }) { hour ->
                val first = hour == hours.first()
                val label = if (first) stringResource(R.string.now) else format.format(Instant.ofEpochMilli(hour.time).atZone(zone))
                val condition = stringResource(hour.condition.label(hour.isDay))
                val temperature = temp(hour.temperature, units)
                val rain = hour.rainChance?.takeIf { it >= 20 }
                val description =
                    if (rain != null) {
                        stringResource(
                            R.string.hour_rain_description,
                            label,
                            temperature,
                            condition,
                            stringResource(R.string.percent, rain),
                        )
                    } else {
                        stringResource(R.string.hour_description, label, temperature, condition)
                    }
                Column(
                    Modifier
                        .widthIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(if (first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onDetail(Instant.ofEpochMilli(hour.time).atZone(zone).toLocalDate(), Metric.Temperature) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val onColor = if (first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(label, style = MaterialTheme.typography.labelMedium, color = onColor, maxLines = 1)
                    Icon(
                        painterResource(hour.condition.icon(hour.isDay)),
                        contentDescription = null,
                        tint = onColor,
                        modifier = Modifier.size(28.dp),
                    )
                    // The chance of rain sits under the icon, so hours without one don't leave a gap
                    // below the temperature.
                    if (anyRain) {
                        Text(
                            if (rain != null) stringResource(R.string.percent, rain) else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 12.sp),
                            color = if (first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(12.dp),
                        )
                    }
                    Text(
                        temperature,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Days(
    forecast: Forecast,
    units: Units,
    onDetail: (LocalDate, Metric) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val bars = remember(forecast) { Ranges.bars(forecast.days) }
    val nowAt = remember(forecast) { Ranges.position(forecast.current.temperature, forecast.days) }
    Card(pluralStringResource(R.plurals.forecast_days, forecast.days.size, forecast.days.size)) {
        forecast.days.forEachIndexed { i, day ->
            DayRow(
                day = day,
                name = if (i == 0) stringResource(R.string.today) else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                bar = bars[i],
                now = nowAt.takeIf { i == 0 },
                units = units,
                onClick = { onDetail(day.date, Metric.Temperature) },
            )
        }
    }
}

@Composable
private fun DayRow(
    day: Day,
    name: String,
    bar: io.github.michaelmassoni.tessera.weather.engine.RangeBar,
    now: Float?,
    units: Units,
    onClick: () -> Unit,
) {
    val condition = stringResource(day.condition.label())
    val low = temp(day.low, units)
    val high = temp(day.high, units)
    val rain = day.rainChance?.takeIf { it >= 20 }
    val description =
        if (rain != null) {
            stringResource(R.string.day_rain_description, name, condition, low, high, stringResource(R.string.percent, rain))
        } else {
            stringResource(R.string.day_description, name, condition, low, high)
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp), maxLines = 1)
        Icon(
            painterResource(day.condition.icon()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            if (rain != null) stringResource(R.string.percent, rain) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(44.dp).padding(start = 6.dp),
        )
        Text(
            low,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        RangeBarView(bar.start, bar.end, now, Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(high, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}

/** The day's low–high on the 10 days' scale; today also shows where it is now. */
@Composable
private fun RangeBarView(
    start: Float,
    end: Float,
    now: Float?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.height(8.dp)) {
        val width = maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Box(
            Modifier
                .offset(x = width * start)
                .width((width * (end - start)).coerceAtLeast(8.dp))
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        if (now != null) {
            Box(
                Modifier
                    .offset(x = (width * now - 4.dp).coerceIn(0.dp, width - 8.dp))
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary),
            )
        }
    }
}

private data class Tile(
    /** What the tile opens in the day details (nothing for air quality and pollen). */
    val metric: Metric?,
    val icon: Int,
    val label: String,
    val value: String,
    val note: String? = null,
)

@Composable
private fun Details(
    forecast: Forecast,
    units: Units,
    now: Long,
    air: AirQuality?,
    onDetail: (LocalDate, Metric) -> Unit,
) {
    val current = forecast.current
    val hour = Timeline.currentHour(forecast.hours, now)
    val today = forecast.days.firstOrNull()
    val time = timeFormat()
    val windUnit = stringResource(units.wind.short())
    val miles = units.wind == WindUnit.MilesPerHour

    fun at(ms: Long?) = ms?.let { time.format(Instant.ofEpochMilli(it).atZone(forecast.zone)) }.orEmpty()
    val feelsNote =
        when {
            current.feelsLike - current.temperature >= 2 -> R.string.feels_like_warmer
            current.temperature - current.feelsLike >= 2 -> R.string.feels_like_cooler
            else -> R.string.feels_like_same
        }
    val tiles =
        buildList {
            hour?.uv?.let { uv ->
                add(
                    Tile(
                        Metric.Uv,
                        R.drawable.ic_sunny,
                        stringResource(R.string.detail_uv),
                        uv.roundToInt().toString(),
                        stringResource(Uv.band(uv).label()),
                    ),
                )
            }
            add(
                Tile(
                    Metric.Wind,
                    R.drawable.ic_air,
                    stringResource(R.string.detail_wind),
                    stringResource(R.string.wind_value, Convert.wind(current.windSpeed, units.wind), windUnit),
                    stringResource(
                        R.string.wind_detail,
                        Convert.wind(current.windGusts, units.wind),
                        stringResource(Wind.compass(current.windDirection).label()),
                        windUnit,
                    ),
                ),
            )
            add(
                Tile(
                    Metric.Humidity,
                    R.drawable.ic_humidity_percentage,
                    stringResource(R.string.detail_humidity),
                    stringResource(R.string.percent, current.humidity),
                    hour?.dewPoint?.let { stringResource(R.string.dew_point, temp(it, units)) },
                ),
            )
            add(
                Tile(
                    Metric.FeelsLike,
                    R.drawable.ic_thermostat,
                    stringResource(R.string.detail_feels_like),
                    temp(current.feelsLike, units),
                    stringResource(feelsNote),
                ),
            )
            today?.precipitation?.let { mm ->
                val value = Convert.precipitation(mm, units.precipitation)
                add(
                    Tile(
                        Metric.Precipitation,
                        R.drawable.ic_water_drop,
                        stringResource(R.string.detail_precipitation),
                        stringResource(R.string.precipitation_value, formatNumber(value), stringResource(units.precipitation.short())),
                        stringResource(R.string.precipitation_today),
                    ),
                )
            }
            if (current.pressure > 0) {
                add(
                    Tile(
                        Metric.Pressure,
                        R.drawable.ic_compress,
                        stringResource(R.string.detail_pressure),
                        stringResource(R.string.pressure_value, current.pressure.roundToInt()),
                    ),
                )
            }
            hour?.visibility?.let { metres ->
                add(
                    Tile(
                        Metric.Visibility,
                        R.drawable.ic_visibility,
                        stringResource(R.string.detail_visibility),
                        stringResource(
                            R.string.visibility_value,
                            formatNumber(Convert.visibility(metres, miles)),
                            stringResource(if (miles) R.string.unit_mi else R.string.unit_km),
                        ),
                    ),
                )
            }
            air?.usAqi?.let { aqi ->
                add(
                    Tile(
                        null,
                        R.drawable.ic_aqi,
                        stringResource(R.string.detail_air_quality),
                        aqi.toString(),
                        air.band?.let { stringResource(it.label()) },
                    ),
                )
            }
            air?.worstPollen?.let { (type, level) ->
                add(
                    Tile(
                        null,
                        R.drawable.ic_pollen,
                        stringResource(R.string.detail_pollen),
                        stringResource(level.label()),
                        stringResource(pollenLabel(type)),
                    ),
                )
            }
            if (today?.sunrise != null && today.sunset != null) {
                // Before sunset: the next event is sunset (show sunrise as the note); after it, tomorrow's sunrise.
                val tomorrow = forecast.days.getOrNull(1)
                if (now < today.sunset) {
                    add(
                        Tile(
                            Metric.Temperature,
                            R.drawable.ic_wb_twilight,
                            stringResource(R.string.detail_sunset),
                            at(today.sunset),
                            stringResource(R.string.sunrise_at, at(today.sunrise)),
                        ),
                    )
                } else {
                    add(
                        Tile(
                            Metric.Temperature,
                            R.drawable.ic_wb_twilight,
                            stringResource(R.string.detail_sunrise),
                            at(tomorrow?.sunrise ?: today.sunrise),
                            stringResource(R.string.sunset_at, at(tomorrow?.sunset ?: today.sunset)),
                        ),
                    )
                }
            }
        }
    Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    TileView(
                        tile,
                        onClick = tile.metric?.let { metric -> { onDetail(today?.date ?: LocalDate.now(forecast.zone), metric) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value ==
        value.roundToInt().toDouble()
    ) {
        value.roundToInt().toString()
    } else {
        value.toString()
    }

@Composable
private fun TileView(
    tile: Tile,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 128.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(tile.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(tile.value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (tile.note != null) {
                Text(tile.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Footer(
    fetchedAt: Long,
    now: Long,
    forecast: Forecast,
) {
    Column(Modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (fetchedAt > 0) {
            Text(
                if (now - fetchedAt < DateUtils.MINUTE_IN_MILLIS) {
                    stringResource(R.string.updated_just_now)
                } else {
                    stringResource(
                        R.string.updated,
                        DateUtils.getRelativeTimeSpanString(fetchedAt, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Credit whoever made this forecast (both sources ask for it).
        Text(
            stringResource(if (forecast.source == ForecastSource.Bom) R.string.credit_bom else R.string.credit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        forecast.station?.let {
            Text(
                stringResource(R.string.observed_at, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The forecaster's own words for today (BOM). */
@Composable
private fun Summary(text: String) {
    Card(stringResource(R.string.todays_forecast)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
