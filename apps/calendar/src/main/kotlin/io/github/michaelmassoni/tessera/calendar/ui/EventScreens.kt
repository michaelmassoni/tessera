package io.github.michaelmassoni.tessera.calendar.ui

import android.content.res.Resources
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.calendar.Detail
import io.github.michaelmassoni.tessera.calendar.EditorState
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.RecurrenceWords
import io.github.michaelmassoni.tessera.calendar.engine.Repeat
import io.github.michaelmassoni.tessera.calendar.engine.describe
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import io.github.michaelmassoni.tessera.core.design.R as DesignR

private val ReminderChoices = listOf(0, 5, 10, 15, 30, 60, 1440)

fun reminderLabel(
    minutes: Int,
    resources: Resources,
): String =
    when {
        minutes == 0 -> resources.getString(R.string.reminder_at_start)
        minutes % 1440 == 0 -> resources.getQuantityString(R.plurals.reminder_days, minutes / 1440, minutes / 1440)
        minutes % 60 == 0 -> resources.getQuantityString(R.plurals.reminder_hours, minutes / 60, minutes / 60)
        else -> resources.getQuantityString(R.plurals.reminder_minutes, minutes, minutes)
    }

fun recurrenceWords(resources: Resources) =
    RecurrenceWords(
        daily = resources.getString(R.string.repeat_daily),
        weekly = resources.getString(R.string.repeat_weekly),
        monthly = resources.getString(R.string.repeat_monthly),
        yearly = resources.getString(R.string.repeat_yearly),
        everyNDays = { resources.getQuantityString(R.plurals.every_n_days, it, it) },
        everyNWeeks = { resources.getQuantityString(R.plurals.every_n_weeks, it, it) },
        everyNMonths = { resources.getQuantityString(R.plurals.every_n_months, it, it) },
        everyNYears = { resources.getQuantityString(R.plurals.every_n_years, it, it) },
        custom = resources.getString(R.string.repeat_custom),
    )

/** The event detail sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(
    detail: Detail,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMap: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val event = detail.event
    val instance = detail.instance
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val writable = detail.calendar?.writable != false
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = detail.calendar?.color ?: instance.color
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(color)))
                Text(
                    event.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                )
                if (writable) {
                    IconButton(
                        onClick = onEdit,
                    ) { Icon(painterResource(DesignR.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
            val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR
            val time =
                if (event.allDay) {
                    // All-day ranges are stored as UTC midnights with an exclusive end.
                    DateUtils.formatDateRange(context, java.util.Formatter(), instance.begin, instance.end - 1, flags, "UTC").toString()
                } else {
                    DateUtils.formatDateRange(context, instance.begin, instance.end, flags or DateUtils.FORMAT_SHOW_TIME)
                }
            DetailRow(DesignR.drawable.ic_schedule, time)
            event.rrule?.let { DetailRow(DesignR.drawable.ic_repeat, describe(it, recurrenceWords(resources))) }
            if (event.location.isNotBlank()) DetailRow(DesignR.drawable.ic_location_on, event.location) { onMap(event.location) }
            if (event.description.isNotBlank()) DetailRow(DesignR.drawable.ic_notes, event.description)
            event.reminders.forEach { DetailRow(DesignR.drawable.ic_notifications, reminderLabel(it, resources)) }
            detail.calendar?.let { DetailRow(DesignR.drawable.ic_calendar_month, calendarName(it, resources)) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(if (event.rrule != null) R.string.delete_series_confirm else R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DetailRow(
    icon: Int,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    val leading: @Composable () -> Unit = { Icon(painterResource(icon), contentDescription = null) }
    if (onClick != null) {
        ListItem(onClick = onClick, leadingContent = leading) { Text(text) }
    } else {
        ListItem(leadingContent = leading) { Text(text) }
    }
}

fun calendarName(
    calendar: CalendarInfo,
    resources: Resources,
): String =
    if (calendar.accountType == android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL) {
        resources.getString(R.string.device_calendar)
    } else {
        calendar.name.ifBlank { calendar.accountName }
    }

/**
 * The event editor: a large title, then rounded sections (when, calendar, reminders, details) of
 * rows with icons, in the style of current Android apps. No outlined boxes: fields are borderless
 * and rows open pickers or menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(
    state: EditorState,
    onChange: (EventDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val resources = LocalResources.current
    val zone = remember(draft.timeZone) { runCatching { ZoneId.of(draft.timeZone) }.getOrDefault(ZoneId.systemDefault()) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    Button(onClick = onSave, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlainField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    hint = stringResource(R.string.hint_title),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    capitalize = true,
                    singleLine = true,
                    modifier = Modifier.padding(start = 40.dp),
                )
                if (!state.isNew && draft.rrule != null) {
                    Text(
                        stringResource(R.string.edit_series_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    )
                }

                Section {
                    EditorRow(
                        icon = DesignR.drawable.ic_schedule,
                        modifier =
                            Modifier.toggleable(
                                value = draft.allDay,
                                role = Role.Switch,
                                onValueChange = { onChange(toggleAllDay(draft, it, zone)) },
                            ),
                    ) {
                        Text(
                            stringResource(R.string.field_all_day),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = draft.allDay, onCheckedChange = null)
                    }
                    DateTimeRow(stringResource(R.string.field_starts), draft.start, draft.allDay, zone) { newStart ->
                        val length = draft.end - draft.start
                        onChange(draft.copy(start = newStart, end = newStart + length))
                    }
                    DateTimeRow(
                        stringResource(R.string.field_ends),
                        if (draft.allDay) draft.end - DAY else draft.end,
                        draft.allDay,
                        zone,
                    ) { newEnd ->
                        val end = if (draft.allDay) newEnd + DAY else newEnd
                        onChange(draft.copy(end = maxOf(end, draft.start + if (draft.allDay) DAY else 0)))
                    }
                    val repeat = Repeat.of(draft.rrule)
                    val repeatLabels =
                        listOf(
                            R.string.repeat_none,
                            R.string.repeat_daily,
                            R.string.repeat_weekly,
                            R.string.repeat_monthly,
                            R.string.repeat_yearly,
                        ).map { stringResource(it) }
                    MenuRow(
                        icon = DesignR.drawable.ic_repeat,
                        label = stringResource(R.string.field_repeat),
                        value = repeat?.let { repeatLabels[it.ordinal] } ?: describe(draft.rrule.orEmpty(), recurrenceWords(resources)),
                        options = repeatLabels,
                        onSelect = { onChange(draft.copy(rrule = Repeat.entries[it].rrule)) },
                    )
                }

                val calendar = state.calendars.firstOrNull { it.id == draft.calendarId }
                Section {
                    MenuRow(
                        icon = null,
                        leading = {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(calendar?.let { Color(it.color).copy(alpha = 1f) } ?: MaterialTheme.colorScheme.primary),
                            )
                        },
                        label = stringResource(R.string.field_calendar),
                        value = calendar?.let { calendarName(it, resources) }.orEmpty(),
                        options = state.calendars.map { calendarName(it, resources) },
                        onSelect = { onChange(draft.copy(calendarId = state.calendars[it].id)) },
                    )
                }

                Section {
                    draft.reminders.forEachIndexed { i, minutes ->
                        EditorRow(icon = if (i == 0) DesignR.drawable.ic_notifications else null) {
                            Text(
                                reminderLabel(minutes, resources),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onChange(draft.copy(reminders = draft.reminders - minutes)) }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_close),
                                    contentDescription = stringResource(R.string.remove_reminder),
                                )
                            }
                        }
                    }
                    val available = ReminderChoices.filter { it !in draft.reminders }
                    if (available.isNotEmpty()) {
                        MenuRow(
                            icon = if (draft.reminders.isEmpty()) DesignR.drawable.ic_notifications else null,
                            label = stringResource(R.string.field_reminders),
                            value = stringResource(R.string.add_reminder),
                            muted = true,
                            options = available.map { reminderLabel(it, resources) },
                            onSelect = { onChange(draft.copy(reminders = (draft.reminders + available[it]).sorted())) },
                        )
                    }
                }

                Section {
                    EditorRow(icon = DesignR.drawable.ic_location_on, iconWidth = 24.dp) {
                        PlainField(
                            value = draft.location,
                            onValueChange = { onChange(draft.copy(location = it)) },
                            hint = stringResource(R.string.hint_location),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    EditorRow(icon = DesignR.drawable.ic_notes, alignTop = true, iconWidth = 24.dp) {
                        PlainField(
                            value = draft.description,
                            onValueChange = { onChange(draft.copy(description = it)) },
                            hint = stringResource(R.string.hint_description),
                            capitalize = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** A group of rows on a tonal, well-rounded surface. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** A row in a section: an icon column (blank when [icon] is null, so rows line up), then content. */
@Composable
private fun EditorRow(
    icon: Int?,
    modifier: Modifier = Modifier,
    alignTop: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /** 40dp lines text up; a text field pads its own text by 16dp, so its row uses 24dp. */
    iconWidth: Dp = 40.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(iconWidth).then(if (alignTop) Modifier.padding(top = 16.dp) else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                leading != null -> leading()
                icon != null -> Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** A text field without a box or underline; [hint] shows (and is read by TalkBack) when it's empty. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    capitalize: Boolean = false,
    singleLine: Boolean = false,
) {
    val clear = Color.Transparent
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, style = textStyle) },
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions =
            KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            ),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = clear,
                unfocusedContainerColor = clear,
                disabledContainerColor = clear,
                focusedIndicatorColor = clear,
                unfocusedIndicatorColor = clear,
                disabledIndicatorColor = clear,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

private const val DAY = 86_400_000L

/** Switching all-day on keeps the date (as UTC midnights); switching it off restores 9:00–10:00 local. */
private fun toggleAllDay(
    draft: EventDraft,
    allDay: Boolean,
    zone: ZoneId,
): EventDraft {
    if (allDay == draft.allDay) return draft
    return if (allDay) {
        val startDay = Instant.ofEpochMilli(draft.start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(maxOf(draft.start, draft.end - 1)).atZone(zone).toLocalDate()
        draft.copy(
            allDay = true,
            start = startDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            end =
                endDay
                    .plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
        )
    } else {
        val day = Instant.ofEpochMilli(draft.start).atZone(ZoneOffset.UTC).toLocalDate()
        val start =
            day
                .atTime(9, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        draft.copy(allDay = false, start = start, end = start + 3_600_000L, timeZone = zone.id)
    }
}

/** A date (and, for timed events, a time) on one row; each opens its picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    millis: Long,
    allDay: Boolean,
    zone: ZoneId,
    onChange: (Long) -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val z: ZoneId = if (allDay) ZoneOffset.UTC else zone
    val dateTime = Instant.ofEpochMilli(millis).atZone(z)
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val date =
        dateTime.toLocalDate().format(
            java.time.format.DateTimeFormatter
                .ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy"), locale),
        )
    val time = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
    EditorRow(icon = null) {
        Text(
            date,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = label) { pickDate = true }
                    .semantics { contentDescription = "$label, $date" }
                    .padding(vertical = 14.dp),
        )
        if (!allDay) {
            Text(
                time,
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = label) { pickTime = true }
                        .semantics { contentDescription = "$label, $time" }
                        .padding(horizontal = 8.dp, vertical = 14.dp),
            )
        }
    }
    if (pickDate) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    dateTime
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(ZonedDateTime.of(day, dateTime.toLocalTime(), z).toInstant().toEpochMilli())
                    }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(dateTime.hour, dateTime.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        dateTime
                            .withHour(state.hour)
                            .withMinute(state.minute)
                            .toInstant()
                            .toEpochMilli(),
                    )
                    pickTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

/** A row showing [value] that opens a menu of [options]. [label] is for TalkBack. */
@Composable
private fun MenuRow(
    icon: Int?,
    label: String?,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    muted: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        EditorRow(
            icon = icon,
            leading = leading,
            modifier =
                Modifier
                    .clickable(onClickLabel = label) { open = true }
                    .semantics { if (label != null) contentDescription = "$label, $value" },
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    open = false
                    onSelect(i)
                })
            }
        }
    }
}
