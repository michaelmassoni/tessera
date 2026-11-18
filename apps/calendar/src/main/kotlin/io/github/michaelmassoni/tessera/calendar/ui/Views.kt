package io.github.michaelmassoni.tessera.calendar.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** The month grid. Each cell shows the day number and up to [maxChips] event titles. */
@Composable
fun MonthGrid(
    weeks: List<List<LocalDate>>,
    month: java.time.YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<Instance>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxChips: Int = 3,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            weeks.firstOrNull()?.forEach { day ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp).clearAndSetSemantics { },
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = day.month == month.month,
                        isToday = day == today,
                        isSelected = day == selected,
                        events = byDay[day].orEmpty(),
                        maxChips = maxChips,
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<Instance>,
    maxChips: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val description = pluralStringResource(R.plurals.day_events, events.size, dateText, events.size)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) scheme.secondaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = isSelected
            }.padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isToday) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        isToday -> scheme.onPrimary
                        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> scheme.onSurface
                    },
            )
        }
        events.take(maxChips).forEach { EventChip(it) }
        if (events.size > maxChips) {
            Text(
                stringResource(R.string.more_events, events.size - maxChips),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** A one-line event label: a dot in the event's (calendar's) colour, then the title. */
@Composable
private fun EventChip(event: Instance) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(event.color)))
        Text(
            event.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = 2.dp),
        )
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
