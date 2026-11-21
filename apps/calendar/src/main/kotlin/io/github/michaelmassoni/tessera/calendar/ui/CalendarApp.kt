package io.github.michaelmassoni.tessera.calendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
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

    fun edit()

    fun delete()

    fun map(location: String)

    fun update(draft: EventDraft)

    fun save()

    fun cancelEdit()

    fun loadMore()

    fun setVisible(
        calendar: CalendarInfo,
        visible: Boolean,
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

    if (editor != null) {
        EventEditor(editor, actions::update, actions::save, close, modifier)
    } else {
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
    if (calendarsDialog) CalendarsDialog(state.calendars, actions::setVisible) { calendarsDialog = false }
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
                if (state.mode == ViewMode.Month) monthTitle(state.month, locale, short) else stringResource(R.string.view_schedule),
                maxLines = 1,
            )
        },
        actions = {
            if (state.mode == ViewMode.Month) {
                IconButton(onClick = { actions.showMonth(state.month.minusMonths(1)) }) {
                    Icon(painterResource(DesignR.drawable.ic_chevron_left), contentDescription = stringResource(R.string.previous_month))
                }
                IconButton(onClick = { actions.showMonth(state.month.plusMonths(1)) }) {
                    Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_month))
                }
            }
            IconButton(onClick = actions::today) {
                Icon(painterResource(DesignR.drawable.ic_today), contentDescription = stringResource(R.string.today))
            }
            IconButton(onClick = { actions.setMode(if (state.mode == ViewMode.Month) ViewMode.Schedule else ViewMode.Month) }) {
                Icon(
                    painterResource(
                        if (state.mode ==
                            ViewMode.Month
                        ) {
                            DesignR.drawable.ic_view_agenda
                        } else {
                            DesignR.drawable.ic_calendar_month
                        },
                    ),
                    contentDescription = stringResource(if (state.mode == ViewMode.Month) R.string.view_schedule else R.string.view_month),
                )
            }
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
        val swipe =
            Modifier.pointerInput(state.month) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    when {
                        total > 120 -> actions.showMonth(state.month.minusMonths(1))
                        total < -120 -> actions.showMonth(state.month.plusMonths(1))
                    }
                }) { _, amount -> total += amount }
            }
        val day = state.selected
        val dayEvents = listOf(day to state.byDay[day].orEmpty())
        if (wide) {
            // Room for the grid and the selected day's events side by side.
            Row(Modifier.fillMaxSize()) {
                MonthGrid(
                    state.weeks,
                    state.month,
                    state.today,
                    day,
                    state.byDay,
                    actions::select,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(swipe)
                        .padding(horizontal = 4.dp),
                )
                VerticalDivider()
                Column(Modifier.widthIn(max = 420.dp).weight(0.6f)) {
                    DayHeading(day, state.today)
                    EventList(dayEvents, state.today, actions::open, showHeadings = false)
                }
            }
        } else {
            // The month takes the whole screen; a day's events open in a sheet.
            MonthGrid(
                state.weeks,
                state.month,
                state.today,
                null,
                state.byDay,
                actions::openDay,
                Modifier.fillMaxSize().then(swipe).padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
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
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
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
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Color(cal.color)))
                        Column {
                            Text(calendarName(cal, resources))
                            if (cal.accountName.isNotBlank() && cal.accountName != cal.name) {
                                Text(
                                    cal.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
