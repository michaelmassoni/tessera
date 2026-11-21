package io.github.michaelmassoni.tessera.calendar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.WeekLayout
import io.github.michaelmassoni.tessera.calendar.engine.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * The month grid: rounded day cells, and each week's events as pills in their calendar's colour.
 * An event over several days is one bar across them. As many lanes show as the row has room for;
 * a day with more events ends in "+N". [selected] is outlined (null: no selection shown).
 */
@Composable
fun MonthGrid(
    weeks: List<List<LocalDate>>,
    month: java.time.YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    byDay: Map<LocalDate, List<Instance>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weeks.firstOrNull()?.forEach { day ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp).clearAndSetSemantics { },
                )
            }
        }
        weeks.forEach { week ->
            WeekRow(week, month, today, selected, byDay, onSelect, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private val CellGap = 2.dp
private val DayNumberSize = 26.dp

@Composable
private fun WeekRow(
    week: List<LocalDate>,
    month: java.time.YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    byDay: Map<LocalDate, List<Instance>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A lane is one line of text plus a little room; it grows with the font size.
    val laneHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.labelMedium.lineHeight
                .toDp()
        } + 4.dp
    BoxWithConstraints(modifier) {
        val top = CellGap + DayNumberSize + 4.dp
        val lanes = ((maxHeight - top - CellGap * 2) / laneHeight).toInt().coerceAtLeast(0)
        val plan = remember(week, byDay, lanes) { WeekLayout.plan(week, byDay, lanes) }
        Row(Modifier.fillMaxSize()) {
            week.forEach { day ->
                DayCell(
                    day = day,
                    inMonth = day.month == month.month,
                    isToday = day == today,
                    isSelected = day == selected,
                    events = byDay[day].orEmpty(),
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        // The pills don't take touches: a tap anywhere on a day opens that day. TalkBack reads the
        // events from each day cell instead.
        Column(Modifier.fillMaxWidth().padding(top = top).clearAndSetSemantics { }) {
            repeat(lanes) { lane -> Lane(plan, lane, last = lane == lanes - 1, Modifier.fillMaxWidth().height(laneHeight)) }
            // Too short for a pill (a phone in landscape): a dot per event, up to three.
            if (lanes == 0) {
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                            byDay[day].orEmpty().take(3).forEach {
                                Box(
                                    Modifier
                                        .padding(
                                            horizontal = 1.dp,
                                        ).size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(it.color).copy(alpha = 1f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Lane(
    plan: WeekPlan,
    lane: Int,
    last: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        var col = 0
        while (col < plan.more.size) {
            val bar = plan.bars.firstOrNull { it.lane == lane && it.start == col }
            when {
                bar != null -> {
                    EventPill(bar.instance, Modifier.weight(bar.span.toFloat()))
                    col += bar.span
                }

                last && plan.more[col] > 0 -> {
                    Text(
                        stringResource(R.string.more_events, plan.more[col]),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    col++
                }

                else -> {
                    Spacer(Modifier.weight(1f))
                    col++
                }
            }
        }
    }
}

/** An event as a pill filled with its calendar's colour; the text is black or white, whichever reads. */
@Composable
private fun EventPill(
    event: Instance,
    modifier: Modifier = Modifier,
) {
    val fill = Color(event.color).copy(alpha = 1f)
    // Event colours are the user's data, not theme colours, so the text colour is picked by contrast.
    val text = if (fill.luminance() > 0.4f) Color.Black else Color.White
    Box(
        modifier
            .fillMaxHeight()
            .padding(horizontal = CellGap + 1.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            event.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.labelMedium,
            color = text,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<Instance>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val count = pluralStringResource(R.plurals.day_events, events.size, dateText, events.size)
    val untitled = stringResource(R.string.untitled)
    val description =
        if (events.isEmpty()) {
            count
        } else {
            stringResource(
                R.string.day_events_titles,
                count,
                events.joinToString { it.title.ifBlank { untitled } },
            )
        }
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .padding(CellGap)
            .clip(shape)
            .background(
                when {
                    isToday -> scheme.secondaryContainer
                    inMonth -> scheme.surfaceContainerHigh
                    else -> scheme.surfaceContainerLow
                },
            ).then(if (isSelected) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(DayNumberSize)
                .clip(CircleShape)
                .background(if (isToday) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        isToday -> scheme.onPrimary
                        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> scheme.onSurface
                    },
            )
        }
    }
}

/** A list of events for one day, or several days with headings. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventList(
    days: List<Pair<LocalDate, List<Instance>>>,
    today: LocalDate,
    onOpen: (Instance) -> Unit,
    modifier: Modifier = Modifier,
    showHeadings: Boolean = true,
    empty: String = stringResource(R.string.no_events),
    onLoadMore: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    val locale = LocalConfiguration.current.locales[0]
    if (days.all { it.second.isEmpty() }) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
            Text(empty, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        days.forEach { (day, events) ->
            if (showHeadings) {
                stickyHeader(key = "h$day") {
                    val label =
                        when (day) {
                            today -> {
                                stringResource(R.string.today)
                            }

                            else -> {
                                day.format(
                                    DateTimeFormatter.ofPattern(
                                        android.text.format.DateFormat
                                            .getBestDateTimePattern(locale, "EEEEMMMMd"),
                                        locale,
                                    ),
                                )
                            }
                        }
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                    )
                }
            }
            items(events, key = { "e$day-${it.eventId}-${it.begin}" }) { EventRow(it, onOpen) }
        }
        if (onLoadMore != null) {
            item(key = "more") {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(stringResource(R.string.load_more)) }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: Instance,
    onOpen: (Instance) -> Unit,
) {
    val context = LocalContext.current
    val time =
        if (event.allDay) {
            stringResource(R.string.all_day)
        } else {
            DateUtils.formatDateRange(context, event.begin, event.end, DateUtils.FORMAT_SHOW_TIME)
        }
    ListItem(
        onClick = { onOpen(event) },
        // Transparent, so the row sits on whatever it's in (the screen, or the day sheet).
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.color)),
            )
        },
        supportingContent = {
            Text(
                listOf(time, event.location).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(event.title.ifBlank { stringResource(R.string.untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "November 2026". */
fun monthTitle(
    month: java.time.YearMonth,
    locale: java.util.Locale,
    short: Boolean = false,
): String =
    month.format(
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat
                .getBestDateTimePattern(locale, if (short) "MMMyyyy" else "MMMMyyyy"),
            locale,
        ),
    )

/** The first day of the week for [locale]. */
fun firstDayOfWeek(locale: java.util.Locale): DayOfWeek =
    DayOfWeek.of(
        java.util.Calendar
            .getInstance(locale)
            .firstDayOfWeek
            .let { if (it == java.util.Calendar.SUNDAY) 7 else it - 1 },
    )
