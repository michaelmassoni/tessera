package io.github.michaelmassoni.tessera.messages.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.ScheduleSlot
import io.github.michaelmassoni.tessera.messages.engine.ScheduleTimes
import io.github.michaelmassoni.tessera.messages.engine.ScheduledMessage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Send, or long-press to schedule. Material's icon buttons have no long-press, so this is a
 * filled circle with the same size and colors, and both actions are exposed to accessibility.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SendButton(
    enabled: Boolean,
    onSend: () -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val send = stringResource(R.string.send)
    Surface(
        shape = CircleShape,
        color = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics { contentDescription = send }
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.schedule_send),
                    onLongClick = onSchedule,
                    onClick = onSend,
                ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(DesignR.drawable.ic_send), contentDescription = null)
        }
    }
}

/** A message waiting to go out, above the composer: when, what, Send now and Cancel. */
@Composable
internal fun ScheduledRow(
    message: ScheduledMessage,
    actions: MessagesActions,
) {
    val context = LocalContext.current
    val time =
        DateUtils.formatDateTime(
            context,
            message.at,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
        )
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Icon(painterResource(DesignR.drawable.ic_schedule), contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.scheduled_for, time), style = MaterialTheme.typography.labelMedium)
                Text(
                    message.text.ifEmpty { stringResource(R.string.picture) },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { actions.sendScheduledNow(message.id) }) {
                Icon(painterResource(DesignR.drawable.ic_send), contentDescription = stringResource(R.string.send_now))
            }
            IconButton(onClick = { actions.cancelScheduled(message.id) }) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.cancel_scheduled))
            }
        }
    }
}

/** Quick times, or any date and time, for the draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleSheet(
    actions: MessagesActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val now = remember { LocalDateTime.now(zone) }
    val exact = remember { actions.scheduleIsExact() }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }

    fun schedule(at: LocalDateTime) {
        actions.schedule(at.atZone(zone).toInstant().toEpochMilli())
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.schedule_send),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ScheduleTimes.suggestions(now).forEach { (slot, at) ->
                val label =
                    stringResource(
                        when (slot) {
                            ScheduleSlot.LaterToday -> R.string.later_today
                            ScheduleSlot.Tonight -> R.string.tonight
                            ScheduleSlot.TomorrowMorning -> R.string.tomorrow
                        },
                    )
                val time = DateUtils.formatDateTime(context, at.atZone(zone).toInstant().toEpochMilli(), DateUtils.FORMAT_SHOW_TIME)
                ListItem(
                    trailingContent = { Text(time) },
                    modifier = Modifier.clickable { schedule(at) },
                ) { Text(label) }
            }
            ListItem(
                leadingContent = { Icon(painterResource(DesignR.drawable.ic_schedule), contentDescription = null) },
                modifier = Modifier.clickable { pickDate = true },
            ) { Text(stringResource(R.string.pick_date_time)) }
            if (!exact) {
                ListItem(
                    trailingContent = { TextButton(onClick = actions::allowExactSchedule) { Text(stringResource(R.string.allow)) } },
                ) { Text(stringResource(R.string.schedule_inexact)) }
            }
        }
    }

    if (pickDate) {
        val today = now.toLocalDate()
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = !utcDay(utcTimeMillis).isBefore(today)

                        override fun isSelectableYear(year: Int) = year >= today.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDay = state.selectedDateMillis
                    pickDate = false
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    pickedDay?.let { dayMillis ->
        val day = utcDay(dayMillis)
        val start = if (day == now.toLocalDate()) now.toLocalTime().plusHours(1) else LocalTime.of(8, 0)
        val state = rememberTimePickerState(start.hour, 0, DateFormat.is24HourFormat(context))
        val at = day.atTime(state.hour, state.minute)
        val valid = ScheduleTimes.isValid(at, LocalDateTime.now(zone))
        AlertDialog(
            onDismissRequest = { pickedDay = null },
            confirmButton = {
                TextButton(onClick = { schedule(at) }, enabled = valid) { Text(stringResource(R.string.schedule)) }
            },
            dismissButton = { TextButton(onClick = { pickedDay = null }) { Text(stringResource(R.string.cancel)) } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                    if (!valid) {
                        Text(
                            stringResource(R.string.schedule_in_past),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
        )
    }
}

/** The date pickers work in UTC midnights. */
private fun utcDay(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
