package io.github.michaelmassoni.tessera.calendar.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.calendar.CalendarState
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.ViewMode
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.Timeline
import io.github.michaelmassoni.tessera.calendar.engine.TimelineBlock
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

private val HourHeight = 56.dp
private val Gutter = 48.dp
private val ANCHOR: LocalDate = LocalDate.of(1900, 1, 1)
private const val PAGES = 400 * 366

/**
 * The week (7 days) or day view: a pager of time grids that follows the finger and settles on the
 * next or previous week or day. Settling selects a day there; selecting elsewhere (the arrows,
 * Today) animates. Tapping an empty slot starts a new event at that half hour.
 */
@Composable
fun TimelinePager(
    state: CalendarState,
    days: Int,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    clock: () -> LocalTime = LocalTime::now,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { firstDayOfWeek(locale) }

    fun startOf(day: LocalDate) = if (days == 7) Timeline.week(day, firstDay).first() else day

    fun pageOf(day: LocalDate) = (ChronoUnit.DAYS.between(startOf(ANCHOR), startOf(day)) / days).toInt()

    fun datesOf(page: Int) = (0 until days).map { startOf(ANCHOR).plusDays(page.toLong() * days + it) }
    val pager = rememberPagerState(initialPage = pageOf(state.selected)) { PAGES }
    val selected by rememberUpdatedState(state.selected)
    val select by rememberUpdatedState(actions::select)
    LaunchedEffect(pager, days) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val dates = datesOf(page)
            if (selected !in dates) select(dates.firstOrNull { it.dayOfWeek == selected.dayOfWeek } ?: dates.first())
        }
    }
    LaunchedEffect(state.selected, days) {
        val page = pageOf(state.selected)
        if (pager.currentPage != page && !pager.isScrollInProgress) pager.animateScrollToPage(page)
    }
    // One scroll position for every page, starting an hour before now (or at 7:00 on other days).
    val density = LocalDensity.current
    val scroll =
        rememberScrollState(
            with(density) { (HourHeight * (clock().hour - 1).coerceIn(0, 16)).roundToPx() },
        )
    HorizontalPager(pager, modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
        TimelinePage(datesOf(page), state, actions, scroll, zone, clock)
    }
}

@Composable
private fun TimelinePage(
    dates: List<LocalDate>,
    state: CalendarState,
    actions: CalendarActions,
    scroll: androidx.compose.foundation.ScrollState,
    zone: ZoneId,
    clock: () -> LocalTime,
) {
    val layouts = remember(dates, state.byDay) { dates.map { Timeline.day(it, state.byDay[it].orEmpty(), zone) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Box(Modifier.width(Gutter))
            dates.forEach { day ->
                DayHeader(day, day == state.today, Modifier.weight(1f)) {
                    // From the week, a day's heading opens that day.
                    actions.select(day)
                    if (dates.size > 1) actions.setMode(ViewMode.Day)
                }
            }
        }
        if (layouts.any { it.allDay.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Box(Modifier.width(Gutter))
                layouts.forEach { layout ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        val shown = if (dates.size == 1) layout.allDay else layout.allDay.take(2)
                        shown.forEach { AllDayChip(it) { actions.open(it) } }
                        if (shown.size < layout.allDay.size) {
                            Text(
                                "+${layout.allDay.size - shown.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            HourGutter()
            dates.forEachIndexed { i, day ->
                DayColumn(day, layouts[i].blocks, day == state.today, zone, clock, actions, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: LocalDate,
    today: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.clickable(onClick = onClick).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
            style = MaterialTheme.typography.labelMedium,
            color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HourGutter() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    // "13:00" or "1 PM", in the phone's language.
    val formatter =
        remember(locale, is24) {
            java.time.format.DateTimeFormatter
                .ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "HHmm" else "ha"), locale)
        }
    Column(Modifier.width(Gutter)) {
        (0 until 24).forEach { hour ->
            Box(Modifier.height(HourHeight).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (hour > 0) {
                    val label = LocalTime.of(hour, 0).format(formatter)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp).offset(y = (-7).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    blocks: List<TimelineBlock>,
    today: Boolean,
    zone: ZoneId,
    clock: () -> LocalTime,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val now = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val nowMinute by produceState(clock().let { it.hour * 60 + it.minute }, today) {
        while (today) {
            delay(60_000)
            value = clock().let { it.hour * 60 + it.minute }
        }
    }
    val newEventLabel = stringResource(R.string.new_event)
    BoxWithConstraints(
        modifier
            .height(HourHeight * 24)
            .drawBehind {
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height))
                for (h in 1 until 24) drawLine(line, Offset(0f, h * hourPx), Offset(size.width, h * hourPx))
            }.pointerInput(day) {
                detectTapGestures { offset ->
                    // To the half hour that was tapped.
                    val minute = ((offset.y / hourPx * 60).toInt() / 30 * 30).coerceIn(0, 23 * 60 + 30)
                    actions.createAt(
                        day
                            .atStartOfDay(zone)
                            .plusMinutes(minute.toLong())
                            .toInstant()
                            .toEpochMilli(),
                    )
                }
            }.semantics { contentDescription = newEventLabel },
    ) {
        val width = maxWidth
        blocks.forEach { b ->
            val top = HourHeight * (b.startMinute / 60f)
            val height = HourHeight * ((b.endMinute - b.startMinute) / 60f)
            EventBlock(
                b.instance,
                showTime = b.endMinute - b.startMinute >= 45,
                modifier =
                    Modifier
                        .offset(x = width * b.column / b.columns, y = top)
                        .width(width / b.columns)
                        .height(height)
                        .padding(horizontal = 1.dp, vertical = 1.dp),
            ) { actions.open(b.instance) }
        }
        if (today) {
            val y = HourHeight * (nowMinute / 60f)
            Box(
                Modifier
                    .offset(y = y - 1.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(now),
            )
            Box(
                Modifier
                    .offset(x = (-4).dp, y = y - 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(now),
            )
        }
    }
}

@Composable
private fun EventBlock(
    event: Instance,
    showTime: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val fill = Color(event.color).copy(alpha = 1f)
    // Event colours are the user's data, not theme colours, so the text colour is picked by contrast.
    val text = if (fill.luminance() > 0.4f) Color.Black else Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            event.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTime) {
            Text(
                DateFormat.getTimeFormat(context).format(java.util.Date(event.begin)),
                style = MaterialTheme.typography.labelSmall,
                color = text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AllDayChip(
    event: Instance,
    onClick: () -> Unit,
) {
    val fill = Color(event.color).copy(alpha = 1f)
    val text = if (fill.luminance() > 0.4f) Color.Black else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 22.dp)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            event.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.labelMedium,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
