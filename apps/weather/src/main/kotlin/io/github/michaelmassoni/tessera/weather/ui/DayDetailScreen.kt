package io.github.michaelmassoni.tessera.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.DayDetail
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastSource
import io.github.michaelmassoni.tessera.weather.engine.Hour
import io.github.michaelmassoni.tessera.weather.engine.Metric
import io.github.michaelmassoni.tessera.weather.engine.Units
import io.github.michaelmassoni.tessera.weather.engine.Uv
import io.github.michaelmassoni.tessera.weather.engine.WindUnit
import io.github.michaelmassoni.tessera.weather.engine.WindUnit.MilesPerHour
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt
import io.github.michaelmassoni.tessera.core.design.R as DesignR

fun Metric.label(): Int =
    when (this) {
        Metric.Temperature -> R.string.detail_temperature
        Metric.FeelsLike -> R.string.detail_feels_like
        Metric.Precipitation -> R.string.detail_precipitation
        Metric.Uv -> R.string.detail_uv
        Metric.Wind -> R.string.detail_wind
        Metric.Humidity -> R.string.detail_humidity
        Metric.Visibility -> R.string.detail_visibility
        Metric.Pressure -> R.string.detail_pressure
    }

fun Metric.icon(): Int =
    when (this) {
        Metric.Temperature, Metric.FeelsLike -> R.drawable.ic_thermostat
        Metric.Precipitation -> R.drawable.ic_water_drop
        Metric.Uv -> R.drawable.ic_sunny
        Metric.Wind -> R.drawable.ic_air
        Metric.Humidity -> R.drawable.ic_humidity_percentage
        Metric.Visibility -> R.drawable.ic_visibility
        Metric.Pressure -> R.drawable.ic_compress
    }

/** A value in the units the user picked (the chart and labels use these). */
private fun Metric.display(
    value: Double,
    units: Units,
): Double =
    when (this) {
        Metric.Temperature, Metric.FeelsLike -> Convert.temperature(value, units.temperature).toDouble()
        Metric.Wind -> Convert.wind(value, units.wind).toDouble()
        Metric.Visibility -> Convert.visibility(value, units.wind == MilesPerHour)
        else -> value
    }

/** A display value as text: "18°", "40%", "25 km/h", "1015 hPa". */
@Composable
private fun Metric.text(
    shown: Double,
    units: Units,
): String =
    when (this) {
        Metric.Temperature, Metric.FeelsLike -> {
            stringResource(R.string.degrees, shown.roundToInt())
        }

        Metric.Precipitation, Metric.Humidity -> {
            stringResource(R.string.percent, shown.roundToInt())
        }

        Metric.Uv -> {
            shown.roundToInt().toString()
        }

        Metric.Wind -> {
            stringResource(R.string.wind_value, shown.roundToInt(), stringResource(units.wind.short()))
        }

        Metric.Visibility -> {
            stringResource(
                R.string.visibility_value,
                if (shown >= 10) shown.roundToInt().toString() else shown.toString(),
                stringResource(if (units.wind == MilesPerHour) R.string.unit_mi else R.string.unit_km),
            )
        }

        Metric.Pressure -> {
            stringResource(R.string.pressure_value, shown.roundToInt())
        }
    }

/** The chart's short labels: no units beyond ° and %. */
@Composable
private fun Metric.short(shown: Double): String =
    when (this) {
        Metric.Temperature, Metric.FeelsLike -> stringResource(R.string.degrees, shown.roundToInt())
        Metric.Precipitation, Metric.Humidity -> stringResource(R.string.percent, shown.roundToInt())
        Metric.Visibility -> if (shown >= 10) shown.roundToInt().toString() else shown.toString()
        else -> shown.roundToInt().toString()
    }

/**
 * One day of a place's forecast, a measure at a time: the days to pick from, the measures, the
 * day's summary and a chart of the hours. Opened from a day, an hour or a detail tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    forecast: Forecast,
    placeName: String,
    units: Units,
    now: Long,
    date: LocalDate,
    metric: Metric,
    onSelect: (LocalDate, Metric) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val hours = remember(forecast, date) { DayDetail.hours(forecast, date) }
    val metrics = remember(hours) { DayDetail.metrics(hours) }
    // A measure the day doesn't have (BOM has no hourly pressure) falls back to temperature.
    val shown = if (metric in metrics) metric else Metric.Temperature
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(placeName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "days") { DayStrip(forecast, units, date, onPick = { onSelect(it, shown) }) }
            item(key = "metrics") {
                MetricChips(metrics.ifEmpty { listOf(Metric.Temperature) }, shown, onPick = { onSelect(date, it) })
            }
            if (hours.size < 2) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.no_hourly),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                item(key = "summary") { Summary(forecast, hours, shown, units, date) }
                item(key = "chart") {
                    DetailCard(Modifier.padding(horizontal = 16.dp)) {
                        HourChart(hours, shown, units, forecast.zone, now, Modifier.padding(horizontal = 12.dp))
                    }
                }
                about(shown)?.let { text ->
                    item(key = "about") {
                        Text(
                            stringResource(text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp),
                        )
                    }
                }
            }
            if (forecast.source == ForecastSource.Bom && forecast.hoursFromOpenMeteo) {
                item(key = "source") {
                    Text(
                        stringResource(R.string.hours_from_open_meteo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
}

private fun about(metric: Metric): Int? =
    when (metric) {
        Metric.Uv -> R.string.about_uv
        Metric.FeelsLike -> R.string.about_feels_like
        Metric.Humidity -> R.string.about_humidity
        Metric.Precipitation -> R.string.about_precipitation
        else -> null
    }

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) { Column(Modifier.padding(vertical = 16.dp)) { content() } }
}

/** The forecast's days: name, icon and high/low; the picked one is filled. */
@Composable
private fun DayStrip(
    forecast: Forecast,
    units: Units,
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = forecast.days.indexOfFirst { it.date == selected }
        if (i > 1) list.scrollToItem(i - 1)
    }
    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(forecast.days, key = { _, d -> d.date.toEpochDay() }) { i, day ->
            val picked = day.date == selected
            val name = if (i == 0) stringResource(R.string.today) else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            Surface(
                onClick = { onPick(day.date) },
                selected = picked,
                shape = MaterialTheme.shapes.large,
                color = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.semantics { role = Role.Tab },
            ) {
                Column(
                    Modifier.widthIn(min = 64.dp).padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val on = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(name, style = MaterialTheme.typography.labelLarge, color = on, maxLines = 1)
                    Icon(painterResource(day.condition.icon()), contentDescription = null, tint = on, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.high_low_value, temp(day.high, units), temp(day.low, units)),
                        style = MaterialTheme.typography.labelMedium,
                        color = on,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChips(
    metrics: List<Metric>,
    selected: Metric,
    onPick: (Metric) -> Unit,
) {
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = metrics.indexOf(selected)
        if (i > 1) list.scrollToItem(i - 1)
    }
    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(metrics, key = { _, m -> m.name }) { _, m ->
            FilterChip(
                selected = m == selected,
                onClick = { onPick(m) },
                label = { Text(stringResource(m.label())) },
                leadingIcon = { Icon(painterResource(m.icon()), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** The day at a glance for the measure: a big number and a line under it. */
@Composable
private fun Summary(
    forecast: Forecast,
    hours: List<Hour>,
    metric: Metric,
    units: Units,
    date: LocalDate,
) {
    val summary = DayDetail.summary(hours, metric) ?: return
    val day = forecast.days.firstOrNull { it.date == date }
    val time = timeFormat()

    @Composable
    fun v(raw: Double) = metric.text(metric.display(raw, units), units)
    val (label, value, note) =
        when (metric) {
            Metric.Temperature -> {
                Triple(
                    stringResource(R.string.label_high_low),
                    stringResource(
                        R.string.high_low_value,
                        temp(day?.high ?: summary.max, units),
                        temp(day?.low ?: summary.min, units),
                    ),
                    day?.let { stringResource(it.condition.label()) },
                )
            }

            Metric.FeelsLike -> {
                Triple(
                    stringResource(R.string.label_feels_like_high),
                    v(summary.max),
                    stringResource(R.string.label_lowest, v(summary.min)),
                )
            }

            Metric.Precipitation -> {
                val total = summary.total?.takeIf { it > 0 }
                Triple(
                    stringResource(R.string.label_rain_chance),
                    v(summary.max),
                    if (total != null) {
                        stringResource(
                            R.string.label_rain_total,
                            stringResource(
                                R.string.precipitation_value,
                                Convert.precipitation(total, units.precipitation).toString(),
                                stringResource(units.precipitation.short()),
                            ),
                        )
                    } else {
                        stringResource(R.string.label_no_rain)
                    },
                )
            }

            Metric.Uv -> {
                val peak = hours.filter { it.uv != null }.maxByOrNull { it.uv!! }
                Triple(
                    stringResource(R.string.label_uv_max),
                    v(summary.max),
                    peak?.let {
                        stringResource(
                            R.string.label_uv_peak,
                            stringResource(Uv.band(summary.max).label()),
                            time.format(Instant.ofEpochMilli(it.time).atZone(forecast.zone)),
                        )
                    },
                )
            }

            Metric.Wind -> {
                Triple(
                    stringResource(R.string.label_wind_max),
                    v(summary.max),
                    summary.gust?.let { stringResource(R.string.label_gusts, v(it)) },
                )
            }

            Metric.Humidity, Metric.Pressure -> {
                Triple(
                    stringResource(R.string.label_average),
                    v(summary.average),
                    stringResource(R.string.label_range, v(summary.min), v(summary.max)),
                )
            }

            Metric.Visibility -> {
                Triple(
                    stringResource(R.string.label_lowest_visibility),
                    v(summary.min),
                    stringResource(R.string.label_range, v(summary.min), v(summary.max)),
                )
            }
        }
    Column(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        if (note != null) Text(note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The day's hours as a line (bars for chance of rain and UV), labelled every three hours, with a
 * line at the current hour on today. Tap or drag to read any hour.
 */
@Composable
private fun HourChart(
    hours: List<Hour>,
    metric: Metric,
    units: Units,
    zone: ZoneId,
    now: Long,
    modifier: Modifier = Modifier,
) {
    val format = hourFormat()
    val points =
        hours.mapNotNull { h ->
            DayDetail.value(h, metric)?.let { v -> Triple(Instant.ofEpochMilli(h.time).atZone(zone).hour, metric.display(v, units), h) }
        }
    if (points.isEmpty()) return
    val range = DayDetail.range(points.map { it.second }, metric, minSpan = if (units.wind == WindUnit.MilesPerHour) 10.0 else 6.0)
    val bars = metric == Metric.Precipitation || metric == Metric.Uv
    val nowHour =
        Instant.ofEpochMilli(now).atZone(zone).let {
            if (it.toLocalDate() ==
                Instant.ofEpochMilli(hours.first().time).atZone(zone).toLocalDate()
            ) {
                it.hour
            } else {
                null
            }
        }
    var picked by remember(hours, metric) { mutableStateOf<Int?>(null) }
    val labels = points.associate { (h, v, _) -> h to metric.short(v) }
    val times = (0..23).associateWith { h -> format.format(Instant.ofEpochMilli(hours.first().time).atZone(zone).withHour(h)) }
    val pickedPoint = picked?.let { p -> points.minByOrNull { kotlin.math.abs(it.first - p) } }
    val readout = pickedPoint?.let { (h, v, _) -> stringResource(R.string.chart_point, times.getValue(h), metric.text(v, units)) }
    val description =
        stringResource(
            R.string.chart_description,
            stringResource(metric.label()),
            points.filter { it.first % 3 == 0 }.joinToString(", ") { (h, v, _) -> "${times.getValue(h)} ${labels.getValue(h)}" },
        )
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.tertiary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val card = MaterialTheme.colorScheme.surfaceContainer
    val strong = MaterialTheme.colorScheme.onSurface
    val small = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    Column(modifier) {
        // The picked hour, or the hint of what the chart is.
        Text(
            readout ?: stringResource(metric.label()),
            style = MaterialTheme.typography.titleSmall,
            color = if (readout != null) strong else textColor,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        if (metric == Metric.Temperature) ConditionRow(hours, zone)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = description }
                .pointerInput(hours, metric) {
                    detectTapGestures { picked = (it.x / size.width * 24).toInt().coerceIn(0, 23) }
                }.pointerInput(hours, metric) {
                    detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
                        picked = (change.position.x / size.width * 24).toInt().coerceIn(0, 23)
                    }
                },
        ) {
            val slot = size.width / 24
            val top = 22.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val arrows = metric == Metric.Wind
            val plotBottom = if (arrows) bottom - 18.dp.toPx() else bottom

            fun x(h: Int) = slot * (h + 0.5f)

            fun y(v: Double) = plotBottom - (plotBottom - top) * range.fraction(v)
            // Baseline and quarter lines.
            for (q in 0..2) {
                val gy = plotBottom - (plotBottom - top) * q / 2f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            if (nowHour != null) {
                drawLine(marker, Offset(x(nowHour), top - 8.dp.toPx()), Offset(x(nowHour), plotBottom), strokeWidth = 2.dp.toPx())
            }
            if (bars) {
                points.forEach { (h, v, _) ->
                    val barTop = y(v)
                    drawRoundRect(
                        primary.copy(alpha = if (pickedPoint == null || pickedPoint.first == h) 1f else 0.5f),
                        topLeft = Offset(x(h) - slot * 0.3f, barTop),
                        size = Size(slot * 0.6f, (plotBottom - barTop).coerceAtLeast(2.dp.toPx())),
                        cornerRadius = CornerRadius(slot * 0.3f),
                    )
                }
            } else {
                val line =
                    Path().apply {
                        points.forEachIndexed { i, (h, v, _) -> if (i == 0) moveTo(x(h), y(v)) else lineTo(x(h), y(v)) }
                    }
                val area =
                    Path().apply {
                        addPath(line)
                        lineTo(x(points.last().first), plotBottom)
                        lineTo(x(points.first().first), plotBottom)
                        close()
                    }
                drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = 0.3f), primary.copy(alpha = 0f)), top, plotBottom))
                drawPath(line, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach { (h, v, hour) ->
                val show = pickedPoint?.first == h || (pickedPoint == null && h % 3 == 0)
                if (show) {
                    if (!bars) drawCircle(primary, radius = 4.dp.toPx(), center = Offset(x(h), y(v)))
                    val text = measurer.measure(labels.getValue(h), small.copy(color = if (pickedPoint?.first == h) strong else textColor))
                    val at = Offset(x(h) - text.size.width / 2f, (y(v) - text.size.height - 4.dp.toPx()).coerceAtLeast(0f))
                    // A backing in the card's colour keeps the label clear of the now line.
                    drawRoundRect(
                        card,
                        topLeft = at - Offset(3.dp.toPx(), 0f),
                        size = Size(text.size.width + 6.dp.toPx(), text.size.height.toFloat()),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawText(text, topLeft = at)
                }
                // Wind: an arrow every three hours pointing where it blows.
                val direction = hour.windDirection
                if (arrows && h % 3 == 0 && direction != null) {
                    val c = Offset(x(h), bottom - 9.dp.toPx())
                    rotate(direction + 180f, pivot = c) {
                        val s = 6.dp.toPx()
                        val arrow =
                            Path().apply {
                                moveTo(c.x, c.y - s)
                                lineTo(c.x + s * 0.7f, c.y + s)
                                lineTo(c.x, c.y + s * 0.5f)
                                lineTo(c.x - s * 0.7f, c.y + s)
                                close()
                            }
                        drawPath(arrow, textColor)
                    }
                }
            }
            // Hours along the bottom.
            for (h in 0..18 step 6) {
                val text = measurer.measure(times.getValue(h), small.copy(color = textColor))
                drawText(text, topLeft = Offset((x(h) - text.size.width / 2f).coerceAtLeast(0f), bottom + 4.dp.toPx()))
            }
        }
    }
}

/** The sky every three hours, above the temperature line. */
@Composable
private fun ConditionRow(
    hours: List<Hour>,
    zone: ZoneId,
) {
    val byHour = hours.associateBy { Instant.ofEpochMilli(it.time).atZone(zone).hour }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        for (h in 0..23) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val hour = byHour[h]
                if (h % 3 == 0 && hour != null) {
                    // Wider than its slot: the box doesn't clip, so the icon centres on the hour.
                    Icon(
                        painterResource(hour.condition.icon(hour.isDay)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.requiredSize(20.dp),
                    )
                }
            }
        }
    }
}
