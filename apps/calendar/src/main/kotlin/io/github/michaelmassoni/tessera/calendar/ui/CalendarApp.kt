package io.github.michaelmassoni.tessera.calendar.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.calendar.CalendarEvent
import io.github.michaelmassoni.tessera.calendar.CalendarState
import io.github.michaelmassoni.tessera.calendar.Detail
import io.github.michaelmassoni.tessera.calendar.EditorState
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.ViewMode
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.calendar.engine.Scope
import io.github.michaelmassoni.tessera.core.settings.ColorDialog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import io.github.michaelmassoni.tessera.calendar.engine.MonthGrid as MonthWeeks
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Everything the screen can ask for. */
interface CalendarActions {
    fun setMode(mode: ViewMode)

    fun showMonth(month: YearMonth)

    fun select(day: LocalDate)

    /** Selects [day] and shows its events (in a sheet on a phone). */
    fun openDay(day: LocalDate)

    fun closeDay()

    fun today()

    fun open(instance: Instance)

    fun closeDetail()

    fun create()

    /** A new event starting at [start] (epoch ms), from tapping the week or day grid. */
    fun createAt(start: Long)

    fun edit(scope: Scope)

    fun delete(scope: Scope)

    fun map(location: String)

    fun update(draft: EventDraft)

    fun save()

    fun cancelEdit()

    fun loadMore()

    fun setVisible(
        calendar: CalendarInfo,
        visible: Boolean,
    )

    /** Changes a calendar's colour (any calendar: docs/sync.md). */
    fun setColor(
        calendar: CalendarInfo,
        color: Int,
    )

    fun import()

    fun openSettings()
}

private val WideWidth = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(
    state: CalendarState,
    detail: Detail?,
    editor: EditorState?,
    events: Flow<CalendarEvent>,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
    // The week and day views' zone and clock; previews fix them so screenshots don't change.
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    clock: () -> java.time.LocalTime = java.time.LocalTime::now,
) {
    val snackbar = remember { SnackbarHostState() }
    var discard by rememberSaveable { mutableStateOf(false) }
    var calendarsDialog by rememberSaveable { mutableStateOf(false) }
    val close = { if (editor?.changed == true) discard = true else actions.cancelEdit() }
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { e ->
            snackbar.showSnackbar(
                when (e) {
                    CalendarEvent.Saved -> resources.getString(R.string.saved)
                    CalendarEvent.Deleted -> resources.getString(R.string.deleted)
                    CalendarEvent.Failed -> resources.getString(R.string.failed)
                    is CalendarEvent.Imported -> resources.getQuantityString(R.plurals.imported, e.count, e.count)
                },
            )
        }
    }
    BackHandler(editor != null) { close() }

    // The views leave composition while the editor is shown: this keeps their scroll and page.
    val saveable = rememberSaveableStateHolder()
    if (editor != null) {
        // Saved or cancelled: the next event opens a fresh editor.
        DisposableEffect(Unit) { onDispose { saveable.removeState(EDITOR_KEY) } }
        saveable.SaveableStateProvider(EDITOR_KEY) { EventEditor(editor, actions::update, actions::save, close, modifier) }
    } else {
        saveable.SaveableStateProvider(VIEWS_KEY) {
            Scaffold(
                modifier = modifier,
                topBar = { CalendarTopBar(state, actions) { calendarsDialog = true } },
                floatingActionButton = {
                    FloatingActionButton(onClick = actions::create) {
                        Icon(painterResource(DesignR.drawable.ic_add), contentDescription = stringResource(R.string.new_event))
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (state.mode) {
                        ViewMode.Month -> {
                            MonthScreen(state, actions)
                        }

                        ViewMode.Week -> {
                            TimelinePager(state, days = 7, actions, zone = zone, clock = clock)
                        }

                        ViewMode.Day -> {
                            TimelinePager(state, days = 1, actions, zone = zone, clock = clock)
                        }

                        ViewMode.Schedule -> {
                            EventList(
                                state.schedule,
                                state.today,
                                actions::open,
                                empty = stringResource(R.string.no_upcoming),
                                onLoadMore = actions::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }
    detail?.let { EventDetailSheet(it, actions::edit, actions::delete, actions::map, actions::closeDetail) }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    discard = false
                    actions.cancelEdit()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
    if (calendarsDialog) CalendarsDialog(state.calendars, actions::setVisible, actions::setColor) { calendarsDialog = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    state: CalendarState,
    actions: CalendarActions,
    onCalendars: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            // Narrow windows have room for "Nov 2026" next to the actions, not "November 2026".
            val short =
                with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.width
                        .toDp()
                } < 600.dp
            Text(
                when (state.mode) {
                    ViewMode.Month -> monthTitle(state.month, locale, short)
                    ViewMode.Week -> monthTitle(YearMonth.from(state.selected), locale, short)
                    ViewMode.Day -> dayTitle(state.selected, locale)
                    ViewMode.Schedule -> stringResource(R.string.view_schedule)
                },
                maxLines = 1,
            )
        },
        actions = {
            when (state.mode) {
                ViewMode.Month -> {
                    IconButton(onClick = { actions.showMonth(state.month.minusMonths(1)) }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.previous_month),
                        )
                    }
                    IconButton(onClick = { actions.showMonth(state.month.plusMonths(1)) }) {
                        Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_month))
                    }
                }

                ViewMode.Week, ViewMode.Day -> {
                    val step = if (state.mode == ViewMode.Week) 7L else 1L
                    IconButton(onClick = { actions.select(state.selected.minusDays(step)) }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_chevron_left),
                            contentDescription = stringResource(if (step == 7L) R.string.previous_week else R.string.previous_day),
                        )
                    }
                    IconButton(onClick = { actions.select(state.selected.plusDays(step)) }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_chevron_right),
                            contentDescription = stringResource(if (step == 7L) R.string.next_week else R.string.next_day),
                        )
                    }
                }

                ViewMode.Schedule -> {}
            }
            IconButton(onClick = actions::today) {
                Icon(painterResource(DesignR.drawable.ic_today), contentDescription = stringResource(R.string.today))
            }
            ViewPicker(state.mode, actions::setMode)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    val item: @Composable (Int, Int, () -> Unit) -> Unit = { text, icon, onClick ->
                        DropdownMenuItem(
                            text = { Text(stringResource(text)) },
                            leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                            onClick = {
                                menu = false
                                onClick()
                            },
                        )
                    }
                    item(R.string.calendars, DesignR.drawable.ic_calendar_month, onCalendars)
                    item(R.string.action_import, DesignR.drawable.ic_download, actions::import)
                    HorizontalDivider()
                    item(R.string.settings, DesignR.drawable.ic_settings, actions::openSettings)
                }
            }
        },
    )
}

@Composable
private fun MonthScreen(
    state: CalendarState,
    actions: CalendarActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideWidth
        val day = state.selected
        val dayEvents = listOf(day to state.byDay[day].orEmpty())
        if (wide) {
            // Room for the grid and the selected day's events side by side.
            Row(Modifier.fillMaxSize()) {
                MonthPager(
                    state,
                    day,
                    actions::select,
                    Modifier.weight(1f).fillMaxHeight(),
                    PaddingValues(horizontal = 4.dp),
                    actions::showMonth,
                )
                VerticalDivider()
                Column(Modifier.widthIn(max = 420.dp).weight(0.6f)) {
                    DayHeading(day, state.today)
                    EventList(dayEvents, state.today, actions::open, showHeadings = false)
                }
            }
        } else {
            // The month takes the whole screen; a day's events open in a sheet.
            MonthPager(
                state,
                null,
                actions::openDay,
                Modifier.fillMaxSize(),
                PaddingValues(start = 4.dp, end = 4.dp, bottom = 4.dp),
                actions::showMonth,
            )
            if (state.dayOpen) DaySheet(day, state.today, dayEvents, actions)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    day: LocalDate,
    today: LocalDate,
    events: List<Pair<LocalDate, List<Instance>>>,
    actions: CalendarActions,
) {
    ModalBottomSheet(onDismissRequest = actions::closeDay) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DayHeading(day, today) }
            FilledTonalButton(onClick = {
                actions.closeDay()
                actions.create()
            }) {
                Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.new_event))
            }
        }
        EventList(
            events,
            today,
            actions::open,
            modifier = Modifier.heightIn(min = 120.dp),
            showHeadings = false,
            contentPadding = PaddingValues(bottom = 24.dp),
        )
    }
}

@Composable
private fun DayHeading(
    day: LocalDate,
    today: LocalDate,
) {
    val locale = LocalConfiguration.current.locales[0]
    val text =
        day.format(
            java.time.format.DateTimeFormatter
                .ofPattern(
                    android.text.format.DateFormat
                        .getBestDateTimePattern(locale, "EEEEMMMMd"),
                    locale,
                ),
        )
    Text(
        if (day == today) "${stringResource(R.string.today)} · $text" else text,
        style = MaterialTheme.typography.titleSmall,
        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CalendarsDialog(
    calendars: List<CalendarInfo>,
    onVisible: (CalendarInfo, Boolean) -> Unit,
    onColor: (CalendarInfo, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    var colouring by remember { mutableStateOf<CalendarInfo?>(null) }
    colouring?.let { cal ->
        ColorDialog(
            title = stringResource(R.string.calendar_colour_title, calendarName(cal, resources)),
            initial = cal.color,
            onApply = {
                onColor(cal, it)
                colouring = null
            },
            onDismiss = { colouring = null },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendars)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        text = {
            Column {
                calendars.forEach { cal ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onVisible(cal, !cal.visible) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = cal.visible, onCheckedChange = { onVisible(cal, it) })
                        Column(Modifier.weight(1f)) {
                            Text(calendarName(cal, resources))
                            if (cal.accountName.isNotBlank() && cal.accountName != cal.name) {
                                Text(
                                    cal.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        val colourLabel = stringResource(R.string.calendar_colour, calendarName(cal, resources))
                        IconButton(onClick = { colouring = cal }, modifier = Modifier.semantics { contentDescription = colourLabel }) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(cal.color))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Shown until calendar access is granted. */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_calendar_month),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = if (denied) onSettings else onGrant) {
                    Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_grant))
                }
            }
        }
    }
}

/** Months as pages, numbered from January 1900; enough for any calendar in use. */
private val FIRST_PAGE_MONTH = YearMonth.of(1900, 1)
private const val MONTH_PAGES = 12 * 300

private fun pageOf(month: YearMonth) = (month.year - FIRST_PAGE_MONTH.year) * 12 + month.monthValue - 1

private fun monthOf(page: Int): YearMonth = FIRST_PAGE_MONTH.plusMonths(page.toLong())

/**
 * The month grid in a pager: it follows the finger and settles on the next or previous month.
 * Settling tells the view model; a month changed elsewhere (the arrows, Today, a widget) animates.
 */
@Composable
private fun MonthPager(
    state: CalendarState,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onMonth: (YearMonth) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { firstDayOfWeek(locale) }
    val pager = rememberPagerState(initialPage = pageOf(state.month)) { MONTH_PAGES }
    val shown by rememberUpdatedState(state.month)
    val show by rememberUpdatedState(onMonth)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val month = monthOf(page)
            if (month != shown) show(month)
        }
    }
    LaunchedEffect(state.month) {
        val page = pageOf(state.month)
        if (pager.currentPage != page && !pager.isScrollInProgress) pager.animateScrollToPage(page)
    }
    Column(modifier) {
        // The weekday names stay put; only the days slide.
        val firstWeek = remember(firstDay) { MonthWeeks.weeks(state.month, firstDay).first() }
        WeekdayHeader(firstWeek, Modifier.padding(contentPadding))
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
            val month = monthOf(page)
            val weeks = remember(month, firstDay) { MonthWeeks.weeks(month, firstDay) }
            MonthGrid(
                weeks,
                month,
                state.today,
                selected,
                state.byDay,
                onSelect,
                Modifier.fillMaxSize().padding(contentPadding),
                showWeekdays = false,
            )
        }
    }
}

/** "Wed, 25 Nov" for the day view's title. */
private fun dayTitle(
    day: LocalDate,
    locale: java.util.Locale,
): String =
    day.format(
        java.time.format.DateTimeFormatter
            .ofPattern(
                android.text.format.DateFormat
                    .getBestDateTimePattern(locale, "EEEdMMM"),
                locale,
            ),
    )

/** The view switcher: an icon for the current view that opens Month, Week, Day and Schedule. */
@Composable
private fun ViewPicker(
    mode: ViewMode,
    onPick: (ViewMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val views =
        listOf(
            Triple(ViewMode.Month, R.string.view_month, DesignR.drawable.ic_calendar_month),
            Triple(ViewMode.Week, R.string.view_week, DesignR.drawable.ic_view_week),
            Triple(ViewMode.Day, R.string.view_day, DesignR.drawable.ic_view_day),
            Triple(ViewMode.Schedule, R.string.view_schedule, DesignR.drawable.ic_view_agenda),
        )
    val current = views.first { it.first == mode }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(current.third), contentDescription = stringResource(R.string.change_view, stringResource(current.second)))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            views.forEach { (view, label, icon) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                    trailingIcon =
                        if (view ==
                            mode
                        ) {
                            ({ Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) })
                        } else {
                            null
                        },
                    onClick = {
                        open = false
                        onPick(view)
                    },
                )
            }
        }
    }
}

private const val VIEWS_KEY = "views"
private const val EDITOR_KEY = "editor"
