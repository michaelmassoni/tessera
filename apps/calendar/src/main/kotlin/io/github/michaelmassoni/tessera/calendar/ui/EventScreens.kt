package io.github.michaelmassoni.tessera.calendar.ui

import android.content.res.Resources
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
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

/** The event editor. */
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
                    .widthIn(
                        max = 640.dp,
                    ).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    label = { Text(stringResource(R.string.field_title)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!state.isNew && draft.rrule != null) {
                    Text(
                        stringResource(R.string.edit_series_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = draft.allDay, role = Role.Switch, onValueChange = { onChange(toggleAllDay(draft, it, zone)) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.field_all_day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Picker(
                    label = stringResource(R.string.field_calendar),
                    value =
                        state.calendars
                            .firstOrNull { it.id == draft.calendarId }
                            ?.let { calendarName(it, resources) }
                            .orEmpty(),
                    options = state.calendars.map { calendarName(it, resources) },
                    onSelect = { onChange(draft.copy(calendarId = state.calendars[it].id)) },
                )
                val repeat = Repeat.of(draft.rrule)
                val repeatLabels =
                    listOf(
                        R.string.repeat_none,
                        R.string.repeat_daily,
                        R.string.repeat_weekly,
                        R.string.repeat_monthly,
                        R.string.repeat_yearly,
                    ).map { stringResource(it) }
                Picker(
                    label = stringResource(R.string.field_repeat),
                    value = repeat?.let { repeatLabels[it.ordinal] } ?: describe(draft.rrule.orEmpty(), recurrenceWords(resources)),
                    options = repeatLabels,
                    onSelect = { onChange(draft.copy(rrule = Repeat.entries[it].rrule)) },
                )
                Text(
                    stringResource(R.string.field_reminders),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                draft.reminders.forEach { minutes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(DesignR.drawable.ic_notifications), contentDescription = null)
                        Text(reminderLabel(minutes, resources), modifier = Modifier.weight(1f).padding(start = 16.dp))
                        IconButton(onClick = { onChange(draft.copy(reminders = draft.reminders - minutes)) }) {
                            Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.remove_reminder))
                        }
                    }
                }
                val available = ReminderChoices.filter { it !in draft.reminders }
                if (available.isNotEmpty()) {
                    Picker(
                        label = null,
                        value = stringResource(R.string.add_reminder),
                        options = available.map { reminderLabel(it, resources) },
                        onSelect = { onChange(draft.copy(reminders = (draft.reminders + available[it]).sorted())) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = draft.location,
                    onValueChange = { onChange(draft.copy(location = it)) },
                    label = { Text(stringResource(R.string.field_location)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onChange(draft.copy(description = it)) },
                    label = { Text(stringResource(R.string.field_description)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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

/** A label with a date button and (for timed events) a time button. */
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
    val z: ZoneId = if (allDay) ZoneOffset.UTC else zone
    val dateTime = Instant.ofEpochMilli(millis).atZone(z)
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { pickDate = true }) {
            val locale = LocalConfiguration.current.locales[0]
            Text(
                dateTime.toLocalDate().format(
                    java.time.format.DateTimeFormatter
                        .ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy"), locale),
                ),
            )
        }
        if (!allDay) {
            TextButton(onClick = { pickTime = true }) {
                Text(DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME))
            }
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

/** A label and a value that opens a menu of [options]. */
@Composable
private fun Picker(
    label: String?,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(value)
                Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = null)
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
}
