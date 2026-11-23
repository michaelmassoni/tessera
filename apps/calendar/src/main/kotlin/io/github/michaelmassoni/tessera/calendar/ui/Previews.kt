package io.github.michaelmassoni.tessera.calendar.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.calendar.CalendarState
import io.github.michaelmassoni.tessera.calendar.EditorState
import io.github.michaelmassoni.tessera.calendar.ViewMode
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.DayLayout
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.MonthGrid
import io.github.michaelmassoni.tessera.calendar.engine.Scope
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.flow.emptyFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

private val month = YearMonth.of(2026, 11)
private val today = LocalDate.of(2026, 11, 19)
private val utc = ZoneOffset.UTC
private val blue = 0xFF1A73A8.toInt()
private val green = 0xFF2E7D32.toInt()
private val yellow = 0xFFE6C619.toInt()
private val purple = 0xFF8E3A8E.toInt()

private fun allDay(
    id: Long,
    from: Int,
    days: Int,
    title: String,
    color: Int,
) = Instance(
    id,
    LocalDate
        .of(2026, 11, from)
        .atStartOfDay(utc)
        .toInstant()
        .toEpochMilli(),
    LocalDate
        .of(2026, 11, from + days)
        .atStartOfDay(utc)
        .toInstant()
        .toEpochMilli(),
    title,
    true,
    color,
    2,
)

private fun at(
    day: Int,
    hour: Int,
    minutes: Int = 60,
    title: String,
    color: Int = blue,
) = LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(utc).toEpochMilli().let {
    Instance(day * 100L + hour, it, it + minutes * 60_000L, title, false, color, 1)
}

private val instances =
    listOf(
        at(3, 9, title = "Dentist"),
        at(10, 18, title = "Book club", color = green),
        at(19, 9, 30, "Standup"),
        at(19, 14, 90, "Planning", color = purple),
        at(19, 12, title = "Lunch with Sam", color = green),
        at(19, 15, title = "Design review"),
        at(19, 19, title = "Climbing", color = green),
        at(24, 10, title = "Flight to Melbourne"),
        at(4, 10, title = "10am Shift", color = yellow),
        at(11, 10, title = "10am Shift", color = yellow),
        at(17, 10, title = "10am Shift", color = yellow),
        at(19, 8, title = "10am Shift", color = yellow),
        at(12, 19, title = "Robbie's birthday", color = purple),
        allDay(20, 6, 5, "Trip to Cairns", purple),
        Instance(
            9,
            LocalDate
                .of(2026, 11, 26)
                .atStartOfDay(utc)
                .toInstant()
                .toEpochMilli(),
            LocalDate
                .of(2026, 11, 29)
                .atStartOfDay(utc)
                .toInstant()
                .toEpochMilli(),
            "Holiday",
            true,
            green,
            1,
        ),
    )

private val state =
    CalendarState(
        loaded = true,
        month = month,
        selected = today,
        today = today,
        weeks = MonthGrid.weeks(month, DayOfWeek.MONDAY),
        byDay = DayLayout.byDay(instances, utc),
        schedule =
            DayLayout
                .byDay(instances, utc)
                .filterKeys { !it.isBefore(today) }
                .toSortedMap()
                .toList(),
        calendars = listOf(CalendarInfo(1, "Personal", "me", "LOCAL", blue, true, true)),
    )

private object NoActions : CalendarActions {
    override fun setMode(mode: ViewMode) = Unit

    override fun showMonth(month: YearMonth) = Unit

    override fun select(day: LocalDate) = Unit

    override fun openDay(day: LocalDate) = Unit

    override fun closeDay() = Unit

    override fun today() = Unit

    override fun open(instance: Instance) = Unit

    override fun closeDetail() = Unit

    override fun create() = Unit

    override fun createAt(start: Long) = Unit

    override fun edit(scope: Scope) = Unit

    override fun delete(scope: Scope) = Unit

    override fun map(location: String) = Unit

    override fun update(draft: EventDraft) = Unit

    override fun save() = Unit

    override fun cancelEdit() = Unit

    override fun loadMore() = Unit

    override fun setVisible(
        calendar: CalendarInfo,
        visible: Boolean,
    ) = Unit

    override fun setColor(
        calendar: CalendarInfo,
        color: Int,
    ) = Unit

    override fun import() = Unit

    override fun openSettings() = Unit
}

@TesseraPreviews
@Composable
private fun MonthPreview() {
    TesseraTheme(dynamicColor = false) { CalendarApp(state, null, null, emptyFlow(), NoActions) }
}

@TesseraPreviews
@Composable
private fun SchedulePreview() {
    TesseraTheme(dynamicColor = false) { CalendarApp(state.copy(mode = ViewMode.Schedule), null, null, emptyFlow(), NoActions) }
}

@TesseraPreviews
@Composable
private fun EditorPreview() {
    val start =
        LocalDate
            .of(2026, 11, 19)
            .atTime(15, 0)
            .toInstant(utc)
            .toEpochMilli()
    val draft =
        EventDraft(
            calendarId = 1,
            title = "Design review",
            start = start,
            end = start + 3_600_000,
            timeZone = "UTC",
            location = "Room 4",
            reminders = listOf(10),
            rrule = "FREQ=WEEKLY",
        )
    TesseraTheme(dynamicColor = false) {
        CalendarApp(state, null, EditorState(draft, draft, state.calendars), emptyFlow(), NoActions)
    }
}

// The week and day views, with overlapping events and the current-time line, in both themes.
@TesseraPreviews
@Composable
private fun WeekPreview() {
    TesseraTheme(dynamicColor = false) {
        CalendarApp(state.copy(mode = ViewMode.Week), null, null, emptyFlow(), NoActions, zone = utc, clock = { LocalTime.of(13, 30) })
    }
}

@TesseraPreviews
@Composable
private fun DayPreview() {
    TesseraTheme(dynamicColor = false) {
        CalendarApp(state.copy(mode = ViewMode.Day), null, null, emptyFlow(), NoActions, zone = utc, clock = { LocalTime.of(8, 30) })
    }
}
