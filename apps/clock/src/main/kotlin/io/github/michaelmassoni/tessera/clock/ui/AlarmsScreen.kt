package io.github.michaelmassoni.tessera.clock.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.alarm.Alarm
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Callbacks for the alarm list. */
interface AlarmActions {
    fun onUpdate(alarm: Alarm)

    fun onDelete(alarm: Alarm)

    fun onCancelSnooze(alarm: Alarm)

    fun onSkip(
        alarm: Alarm,
        skip: Boolean,
    )
}

@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    actions: AlarmActions,
    banners: @Composable () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { banners() }
        if (alarms.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(icon = DesignR.drawable.ic_alarm, text = stringResource(R.string.alarms_empty))
            }
        }
        items(alarms.sortedWith(compareBy({ it.hour }, { it.minute })), key = { it.id }) { alarm ->
            AlarmCard(
                alarm = alarm,
                expanded = expandedId == alarm.id,
                onToggleExpanded = { expandedId = if (expandedId == alarm.id) null else alarm.id },
                actions = actions,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: AlarmActions,
) {
    val formatter = rememberClockFormatter()
    val time = LocalTime.of(alarm.hour, alarm.minute)
    var editingTime by rememberSaveable { mutableStateOf(false) }
    var editingLabel by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val summary = repeatSummary(alarm, locale)
    val onOff = stringResource(if (alarm.enabled) R.string.alarm_on else R.string.alarm_off)

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.run { if (alarm.enabled) surfaceContainerHigh else surfaceContainer },
            ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onToggleExpanded).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                        contentDescription =
                            listOf(
                                formatter.time(time),
                                alarm.label,
                                summary,
                            ).filter { it.isNotEmpty() }.joinToString(", ")
                    },
                ) {
                    if (alarm.label.isNotEmpty()) {
                        Text(
                            alarm.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clickable { editingTime = true }) {
                        Text(
                            formatter.digits(time),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        formatter.amPm(time)?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { actions.onUpdate(alarm.copy(enabled = it)) },
                    modifier = Modifier.semantics { stateDescription = onOff },
                )
            }
            val snoozed = alarm.snoozedUntil
            if (snoozed != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            R.string.alarm_snoozed_until,
                            formatter.time(
                                java.time.Instant
                                    .ofEpochMilli(snoozed)
                                    .atZone(java.time.ZoneId.systemDefault()),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { actions.onCancelSnooze(alarm) },
                    ) { Text(stringResource(R.string.action_dismiss)) }
                }
            }
            val skipped = alarm.skipped
            if (skipped != null && alarm.enabled && skipped > System.currentTimeMillis()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.alarm_skipping, skipDay(skipped, locale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { actions.onSkip(alarm, false) }) { Text(stringResource(R.string.action_undo)) }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayToggles(alarm, locale) { days -> actions.onUpdate(alarm.copy(days = days)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editingLabel = true }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_label),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(alarm.label.ifEmpty { stringResource(R.string.alarm_add_label) })
                        }
                    }
                    SoundRow(alarm) { sound -> actions.onUpdate(alarm.copy(ringtone = sound)) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                actions.onUpdate(
                                    alarm.copy(vibrate = !alarm.vibrate),
                                )
                            },
                    ) {
                        Checkbox(checked = alarm.vibrate, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text(stringResource(R.string.alarm_vibrate), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (alarm.repeats && alarm.enabled && (skipped == null || skipped <= System.currentTimeMillis())) {
                        TextButton(onClick = { actions.onSkip(alarm, true) }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_skip_next),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.alarm_skip_next))
                        }
                    }
                    TextButton(onClick = { actions.onDelete(alarm) }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }

    if (editingTime) {
        TimePickerDialog(
            initial = time,
            onDismiss = { editingTime = false },
            onConfirm = { h, m ->
                editingTime = false
                actions.onUpdate(alarm.copy(hour = h, minute = m, enabled = true))
            },
        )
    }
    if (editingLabel) {
        LabelDialog(
            initial = alarm.label,
            onDismiss = { editingLabel = false },
            onConfirm = {
                editingLabel = false
                actions.onUpdate(alarm.copy(label = it))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayToggles(
    alarm: Alarm,
    locale: Locale,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    val first = WeekFields.of(locale).firstDayOfWeek
    val days = (0L until 7L).map { first.plus(it) }
    val selected = stringResource(R.string.state_selected)
    val notSelected = stringResource(R.string.state_not_selected)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val on = day in alarm.days
            ToggleButton(
                checked = on,
                onCheckedChange = { onChange(if (on) alarm.days - day else alarm.days + day) },
                contentPadding = PaddingValues(0.dp),
                modifier =
                    Modifier.weight(1f).semantics {
                        contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                        stateDescription = if (on) selected else notSelected
                    },
            ) {
                Text(day.getDisplayName(TextStyle.NARROW, locale), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SoundRow(
    alarm: Alarm,
    onPicked: (String?) -> Unit,
) {
    val context = LocalContext.current
    val silent = stringResource(R.string.sound_silent)
    val default = stringResource(R.string.sound_default)
    val title =
        remember(alarm.ringtone) {
            when (alarm.ringtone) {
                null -> {
                    default
                }

                "" -> {
                    silent
                }

                else -> {
                    runCatching {
                        RingtoneManager
                            .getRingtone(
                                context,
                                alarm.ringtone.toUri(),
                            )?.getTitle(context)
                    }.getOrNull()
                        ?: default
                }
            }
        }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri =
                result.data?.let {
                    androidx.core.content.IntentCompat.getParcelableExtra(
                        it,
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java,
                    )
                }
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            onPicked(
                when {
                    uri == null -> ""
                    uri == defaultUri || uri == android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI -> null
                    else -> uri.toString()
                },
            )
        }
    TextButton(onClick = {
        val current =
            when (alarm.ringtone) {
                null -> android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                "" -> null
                else -> alarm.ringtone.toUri()
            }
        picker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current),
        )
    }) {
        Icon(
            painterResource(DesignR.drawable.ic_music_note),
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(title)
    }
}

/** "Today", "Tomorrow", "Every day", "Weekdays" or a short list of day names. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun repeatSummary(
    alarm: Alarm,
    locale: Locale,
): String {
    if (alarm.days.size == 7) return stringResource(R.string.repeat_every_day)
    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    if (alarm.days == weekdays) return stringResource(R.string.repeat_weekdays)
    if (alarm.days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return stringResource(R.string.repeat_weekends)
    if (alarm.days.isNotEmpty()) {
        val first = WeekFields.of(locale).firstDayOfWeek
        return (0L until 7L)
            .map {
                first.plus(it)
            }.filter { it in alarm.days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    if (!alarm.enabled) return stringResource(R.string.repeat_not_scheduled)
    val now = LocalTime.now()
    return stringResource(
        if (LocalTime.of(alarm.hour, alarm.minute).isAfter(now)) R.string.repeat_today else R.string.repeat_tomorrow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    title: Int = R.string.alarm_select_time,
) {
    val formatter = rememberClockFormatter()
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = formatter.uses24Hour,
        )
    var keyboard by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (keyboard) TimeInput(state) else TimePicker(state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            Row {
                IconButton(onClick = { keyboard = !keyboard }) {
                    Icon(
                        painterResource(if (keyboard) DesignR.drawable.ic_schedule else DesignR.drawable.ic_keyboard),
                        contentDescription =
                            stringResource(
                                if (keyboard) R.string.time_picker_dial else R.string.time_picker_keyboard,
                            ),
                    )
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
internal fun LabelDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60).replace('\n', ' ') },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun EmptyState(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------------------- previews

private object NoAlarmActions : AlarmActions {
    override fun onUpdate(alarm: Alarm) = Unit

    override fun onDelete(alarm: Alarm) = Unit

    override fun onCancelSnooze(alarm: Alarm) = Unit

    override fun onSkip(
        alarm: Alarm,
        skip: Boolean,
    ) = Unit
}

@TesseraPreviews
@Composable
private fun AlarmsPreview() {
    ClockPreview {
        AlarmsScreen(
            alarms =
                listOf(
                    Alarm(
                        1,
                        6,
                        45,
                        setOf(
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY,
                        ),
                        label = "Gym",
                    ),
                    Alarm(2, 8, 30, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), enabled = false),
                    Alarm(3, 13, 15, setOf(DayOfWeek.WEDNESDAY), label = "Call the dentist"),
                ),
            actions = NoAlarmActions,
            banners = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}

/** "Mon 24 Nov" for the skipped occurrence. */
private fun skipDay(
    millis: Long,
    locale: Locale,
): String =
    java.time.Instant
        .ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(
            java.time.format.DateTimeFormatter.ofPattern(
                android.text.format.DateFormat
                    .getBestDateTimePattern(locale, "EEEdMMM"),
                locale,
            ),
        )
