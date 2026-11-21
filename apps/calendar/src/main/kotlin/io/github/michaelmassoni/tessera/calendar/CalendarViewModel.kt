package io.github.michaelmassoni.tessera.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.calendar.data.CalendarRepository
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.DayLayout
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.MonthGrid
import io.github.michaelmassoni.tessera.calendar.engine.Scope
import io.github.michaelmassoni.tessera.calendar.engine.SeriesSplit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class ViewMode { Month, Week, Day, Schedule }

data class CalendarState(
    val loaded: Boolean = false,
    val mode: ViewMode = ViewMode.Month,
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    /** On a phone, the selected day's events are open in a sheet over the month. */
    val dayOpen: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val weeks: List<List<LocalDate>> = emptyList(),
    val byDay: Map<LocalDate, List<Instance>> = emptyMap(),
    /** Schedule: days from today with events, in order. */
    val schedule: List<Pair<LocalDate, List<Instance>>> = emptyList(),
    val calendars: List<CalendarInfo> = emptyList(),
)

/** The event shown in the detail sheet. */
data class Detail(
    val instance: Instance,
    val event: EventDraft,
    val calendar: CalendarInfo?,
)

data class EditorState(
    val original: EventDraft,
    val draft: EventDraft,
    val calendars: List<CalendarInfo>,
    /** For one occurrence of a repeating event: which events the change applies to. */
    val scope: Scope = Scope.All,
    /** The repeating event and the occurrence being edited, when [scope] isn't [Scope.All]. */
    val series: EventDraft? = null,
    val instanceBegin: Long = 0,
) {
    val isNew: Boolean get() = draft.id == null && scope != Scope.This
    val changed: Boolean get() = draft != original
}

sealed interface CalendarEvent {
    data object Saved : CalendarEvent

    data object Deleted : CalendarEvent

    data object Failed : CalendarEvent

    data class Imported(
        val count: Int,
    ) : CalendarEvent
}

/** Month and schedule data, the open event and the editor. Provider access runs on [io]. */
class CalendarViewModel(
    private val repository: CalendarRepository,
    private val io: CoroutineDispatcher,
    private val firstDayOfWeek: () -> DayOfWeek,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val localCalendarName: String,
    private val localCalendarColor: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private val _detail = MutableStateFlow<Detail?>(null)
    val detail: StateFlow<Detail?> = _detail.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val eventChannel = Channel<CalendarEvent>(Channel.BUFFERED)
    val events: Flow<CalendarEvent> = eventChannel.receiveAsFlow()

    private var loading: Job? = null

    // Once a writable calendar exists there is no need to check on every refresh.
    private var hasWritable = false
    private var scheduleDays = 60L

    fun start() = refresh()

    private fun ensureCalendar() {
        runCatching { repository.ensureWritableCalendar(localCalendarName, localCalendarColor) }
            .onFailure { android.util.Log.w("TesseraCalendar", "Couldn't create the device calendar", it) }
    }

    /**
     * Reloads the visible range. [after] delays it, so a burst of provider changes (a sync writes
     * row by row) becomes one reload: each call cancels the one before.
     */
    fun refresh(after: Long = 0) {
        val s = _state.value
        val today = LocalDate.now(zone())
        val weeks = MonthGrid.weeks(s.month, firstDayOfWeek())
        loading?.cancel()
        loading =
            viewModelScope.launch {
                if (after > 0) delay(after)
                val z = zone()
                // The months either side too, so swiping shows them with their events straight away.
                val (monthRange, scheduleRange) =
                    DayLayout.range(
                        MonthGrid.weeks(s.month.minusMonths(1), firstDayOfWeek()).first().first(),
                        MonthGrid.weeks(s.month.plusMonths(1), firstDayOfWeek()).last().last(),
                        z,
                    ) to
                        DayLayout.range(today, today.plusDays(scheduleDays), z)
                val (monthInstances, scheduleInstances, calendars) =
                    withContext(io) {
                        // Right after the permission is granted the provider can refuse the first insert;
                        // checking on each refresh until one exists makes sure a writable calendar appears.
                        if (!hasWritable) ensureCalendar()
                        Triple(
                            repository.instances(monthRange.first, monthRange.second),
                            repository.instances(scheduleRange.first, scheduleRange.second),
                            repository.calendars(),
                        )
                    }
                hasWritable = calendars.any { it.writable }
                val schedule =
                    DayLayout
                        .byDay(scheduleInstances, z)
                        .filterKeys { !it.isBefore(today) && !it.isAfter(today.plusDays(scheduleDays)) }
                        .toSortedMap()
                        .toList()
                _state.update {
                    it.copy(
                        loaded = true,
                        today = today,
                        weeks = weeks,
                        byDay = DayLayout.byDay(monthInstances, z),
                        schedule = schedule,
                        calendars = calendars,
                    )
                }
            }
    }

    fun setMode(mode: ViewMode) = _state.update { it.copy(mode = mode) }

    fun showMonth(month: YearMonth) {
        _state.update { it.copy(month = month, selected = if (YearMonth.from(it.selected) == month) it.selected else month.atDay(1)) }
        refresh()
    }

    fun select(day: LocalDate) {
        if (YearMonth.from(day) != _state.value.month) {
            _state.update { it.copy(month = YearMonth.from(day), selected = day) }
            refresh()
        } else {
            _state.update { it.copy(selected = day) }
        }
    }

    fun openDay(day: LocalDate) {
        select(day)
        _state.update { it.copy(dayOpen = true) }
    }

    fun closeDay() = _state.update { it.copy(dayOpen = false) }

    fun goToToday() = select(LocalDate.now(zone()))

    fun loadMoreSchedule() {
        scheduleDays += 60
        refresh()
    }

    fun setVisible(
        calendar: CalendarInfo,
        visible: Boolean,
    ) {
        viewModelScope.launch {
            withContext(io) { repository.setVisible(calendar.id, visible) }
            refresh()
        }
    }

    // ------------------------------------------------------------------ detail

    fun open(instance: Instance) {
        viewModelScope.launch {
            val event = withContext(io) { repository.event(instance.eventId) } ?: return@launch
            _detail.value = Detail(instance, event, _state.value.calendars.firstOrNull { it.id == event.calendarId })
        }
    }

    /** Opens an event by id (from a notification or another app). */
    fun openEvent(
        id: Long,
        begin: Long?,
    ) {
        viewModelScope.launch {
            val event = withContext(io) { repository.event(id) } ?: return@launch
            val calendars = withContext(io) { repository.calendars() }
            val start = begin ?: event.start
            val instance =
                Instance(
                    id,
                    start,
                    start + (event.end - event.start),
                    event.title,
                    event.allDay,
                    0,
                    event.calendarId,
                    event.location,
                    event.rrule,
                )
            _detail.value = Detail(instance, event, calendars.firstOrNull { it.id == event.calendarId })
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    /**
     * Deletes the open event. For a repeating event, [scope] says whether that's just this
     * occurrence, this and the ones after it, or the whole series.
     */
    fun delete(scope: Scope = Scope.All) {
        val d = _detail.value ?: return
        _detail.value = null
        viewModelScope.launch {
            val ok =
                withContext(io) {
                    runCatching {
                        val id = d.event.id ?: return@runCatching
                        val begin = d.instance.begin
                        when {
                            d.event.rrule == null || scope == Scope.All -> repository.delete(id)
                            scope == Scope.This -> repository.excludeOccurrence(d.event, begin)
                            begin <= d.event.start -> repository.delete(id)
                            else -> repository.endSeriesBefore(d.event, begin)
                        }
                    }.isSuccess
                }
            eventChannel.trySend(if (ok) CalendarEvent.Deleted else CalendarEvent.Failed)
            refresh()
        }
    }

    // ------------------------------------------------------------------ editor

    /** New event on the selected day at the next whole hour (or as given by another app). */
    fun create(prefill: EventDraft? = null) {
        viewModelScope.launch {
            // Another app can ask for the editor before the first load has finished.
            val calendars =
                _state.value.calendars.ifEmpty {
                    withContext(io) {
                        ensureCalendar()
                        repository.calendars()
                    }
                }
            openEditor(calendars.filter { it.writable }, prefill)
        }
    }

    private fun openEditor(
        writable: List<CalendarInfo>,
        prefill: EventDraft?,
    ) {
        val calendar = writable.firstOrNull { it.visible } ?: writable.firstOrNull() ?: return
        val z = zone()
        val now = java.time.ZonedDateTime.now(z)
        val day = _state.value.selected
        val startTime =
            if (day == now.toLocalDate()) {
                now
                    .plusHours(1)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
            } else {
                day.atTime(9, 0).atZone(z)
            }
        val start = startTime.toInstant().toEpochMilli()
        val base =
            EventDraft(calendarId = calendar.id, start = start, end = start + HOUR, timeZone = z.id, reminders = listOf(DEFAULT_REMINDER))
        val draft = prefill?.let { it.copy(calendarId = if (it.calendarId == 0L) calendar.id else it.calendarId) } ?: base
        _editor.value = EditorState(draft, draft, writable)
    }

    /**
     * Edits the open event. For a repeating event, [scope] picks this occurrence (edited at its own
     * time, without repeat), this and the following ones (starting at this occurrence), or all.
     */
    fun edit(scope: Scope = Scope.All) {
        val d = _detail.value ?: return
        _detail.value = null
        val writable = _state.value.calendars.filter { it.writable }
        val series = d.event
        val atInstance = series.copy(start = d.instance.begin, end = d.instance.begin + (series.end - series.start))
        _editor.value =
            when {
                series.rrule == null || scope == Scope.All -> {
                    EditorState(series, series, writable)
                }

                scope == Scope.This -> {
                    val one = atInstance.copy(rrule = null)
                    EditorState(one, one, writable, Scope.This, series, d.instance.begin)
                }

                else -> {
                    EditorState(atInstance, atInstance, writable, Scope.Following, series, d.instance.begin)
                }
            }
    }

    fun update(draft: EventDraft) = _editor.update { it?.copy(draft = draft) }

    fun cancelEdit() {
        _editor.value = null
    }

    fun save() {
        val e = _editor.value ?: return
        _editor.value = null
        viewModelScope.launch {
            val ok = withContext(io) { runCatching { saveScoped(e) }.isSuccess }
            eventChannel.trySend(if (ok) CalendarEvent.Saved else CalendarEvent.Failed)
            refresh()
        }
    }

    /** Saves the editor's draft for its scope. Runs on [io]. */
    private fun saveScoped(e: EditorState) {
        val series = e.series
        val seriesId = series?.id
        when {
            series == null || seriesId == null || e.scope == Scope.All -> {
                repository.save(e.draft)
            }

            // The series leaves this occurrence out and a one-off event takes its place.
            e.scope == Scope.This -> {
                repository.excludeOccurrence(series, e.instanceBegin)
                repository.save(e.draft.copy(id = null, rrule = null, exdate = null))
            }

            // "This and following" from the first occurrence is the whole series, moved.
            e.instanceBegin <= series.start -> {
                repository.save(e.draft.copy(id = seriesId))
            }

            else -> {
                val before = repository.occurrencesBefore(seriesId, series.start, e.instanceBegin)
                repository.endSeriesBefore(series, e.instanceBegin)
                val rule = e.draft.rrule?.let { if (it == series.rrule) SeriesSplit.continueFrom(it, before) else it }
                repository.save(e.draft.copy(id = null, rrule = rule, exdate = null))
            }
        }
    }

    /** Imports parsed events into the first writable visible calendar. */
    fun import(events: List<EventDraft>) {
        viewModelScope.launch {
            val count =
                withContext(io) {
                    val calendar =
                        repository.calendars().filter { it.writable }.let { w ->
                            w.firstOrNull { it.visible } ?: w.firstOrNull()
                        }
                    if (calendar == null) {
                        0
                    } else {
                        events.count { runCatching { repository.save(it.copy(calendarId = calendar.id)) }.isSuccess }
                    }
                }
            eventChannel.trySend(if (count > 0) CalendarEvent.Imported(count) else CalendarEvent.Failed)
            refresh()
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DEFAULT_REMINDER = 10
    }
}
