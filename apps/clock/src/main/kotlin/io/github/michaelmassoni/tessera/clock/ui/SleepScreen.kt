package io.github.michaelmassoni.tessera.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.alarm.Alarm
import io.github.michaelmassoni.tessera.clock.alarm.AlarmTimes
import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.clock.sleep.BedtimeReminder
import io.github.michaelmassoni.tessera.clock.sleep.SleepCycles
import io.github.michaelmassoni.tessera.clock.sleep.SleepPlan
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import java.time.LocalTime
import java.time.ZonedDateTime
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The calculator's three questions. */
enum class SleepMode(
    val label: Int,
) {
    /** Going to bed now: when to wake up. */
    Now(R.string.sleep_mode_now),

    /** Waking at a time: when to go to bed. */
    Wake(R.string.sleep_mode_wake),

    /** Going to bed at a time: when to wake up. */
    Bed(R.string.sleep_mode_bed),
}

interface SleepActions {
    /** A one-off alarm for this wake-up time. */
    fun onSetAlarm(wake: ZonedDateTime)

    /** The bedtime reminder, and the wake-up time it's for if there is one. */
    fun onSetBedtime(
        bedtime: ZonedDateTime,
        wake: ZonedDateTime?,
    )

    fun onCancelBedtime()
}

/**
 * The sleep calculator (docs/clock.md, "Sleep calculator"): times that fit whole sleep cycles,
 * each one tap from an alarm or a bedtime reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    now: ZonedDateTime,
    settings: ClockSettings,
    alarms: List<Alarm>,
    reminder: BedtimeReminder?,
    actions: SleepActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    initialMode: SleepMode = SleepMode.Now,
) {
    val format = rememberClockFormatter()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    // Waking: the time picked, else when the next alarm rings (following the alarms until one is
    // picked), else 7:00. In minutes of the day.
    var pickedWake by rememberSaveable { mutableStateOf<Int?>(null) }
    val wakeAt =
        pickedWake ?: alarms
            .mapNotNull { AlarmTimes.nextTrigger(it, now) }
            .minOrNull()
            ?.let { it.hour * 60 + it.minute } ?: (7 * 60)
    var bedAt by rememberSaveable { mutableIntStateOf(23 * 60) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<SleepPlan?>(null) }

    val cycle = settings.sleepCycleMinutes
    val fall = settings.fallAsleepMinutes
    val wake = SleepCycles.next(LocalTime.of(wakeAt / 60, wakeAt % 60), now)
    val bed = SleepCycles.next(LocalTime.of(bedAt / 60, bedAt % 60), now)
    val plans =
        when (mode) {
            SleepMode.Now -> SleepCycles.wakeTimes(now, cycle, fall)
            SleepMode.Wake -> SleepCycles.bedtimes(wake, now, cycle, fall)
            SleepMode.Bed -> SleepCycles.wakeTimes(bed, cycle, fall)
        }
    // Alarms already set to ring at a given instant.
    val alarmTimes = alarms.mapNotNull { AlarmTimes.nextTrigger(it, now)?.toInstant() }.toSet()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sleep_calculator)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // The question (mode, time, what the list means), the answers, and the rest.
        val controls: LazyListScope.() -> Unit = {
            item(key = "modes") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SleepMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, SleepMode.entries.size),
                        ) { Text(stringResource(m.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            if (mode != SleepMode.Now) {
                item(key = "time") {
                    val picked = if (mode == SleepMode.Wake) wake else bed
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { picking = true },
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                        ) {
                            Text(format.time(picked), style = MaterialTheme.typography.displayMedium)
                        }
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.sleep_change_time)) }
                        if (mode == SleepMode.Bed && reminder?.atMillis != bed.toInstant().toEpochMilli()) {
                            OutlinedButton(onClick = { actions.onSetBedtime(bed, null) }) {
                                Icon(painterResource(R.drawable.ic_bedtime), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.sleep_remind_me, format.time(bed)),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "intro") {
                Text(
                    when (mode) {
                        SleepMode.Now -> stringResource(R.string.sleep_intro_now, format.time(now.plusMinutes(fall.toLong())))
                        SleepMode.Wake -> stringResource(R.string.sleep_intro_wake, format.time(wake))
                        SleepMode.Bed -> stringResource(R.string.sleep_intro_bed, format.time(bed))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        val results: LazyListScope.() -> Unit = {
            if (plans.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.sleep_too_late, format.time(wake)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(plans, key = { "${mode.name}-${it.cycles}" }) { plan ->
                if (mode == SleepMode.Wake) {
                    PlanCard(plan, time = format.time(plan.bedtime), icon = R.drawable.ic_bedtime, action = null, onClick = {
                        confirming =
                            plan
                    })
                } else {
                    val on = plan.wake.toInstant() in alarmTimes
                    val possible = SleepCycles.canBeAlarm(plan.wake, now)
                    PlanCard(
                        plan,
                        time = format.time(plan.wake),
                        icon = if (on) DesignR.drawable.ic_check else DesignR.drawable.ic_alarm,
                        action =
                            when {
                                on -> stringResource(R.string.sleep_alarm_on)
                                !possible -> stringResource(R.string.sleep_too_far)
                                else -> stringResource(R.string.sleep_set_alarm)
                            },
                        onClick = if (possible && !on) ({ actions.onSetAlarm(plan.wake) }) else null,
                    )
                }
            }
        }
        val extras: LazyListScope.() -> Unit = {
            if (reminder != null && reminder.atMillis > now.toInstant().toEpochMilli()) {
                item(key = "reminder") {
                    val at =
                        java.time.Instant
                            .ofEpochMilli(reminder.atMillis)
                            .atZone(now.zone)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_bedtime), contentDescription = null)
                            Text(
                                stringResource(R.string.sleep_reminder_at, format.time(at)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            TextButton(onClick = actions::onCancelBedtime) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                }
            }
            item(key = "footnote") {
                Text(
                    stringResource(
                        R.string.sleep_footnote,
                        pluralStringResource(R.plurals.duration_minutes, cycle, cycle),
                        pluralStringResource(R.plurals.duration_minutes, fall, fall),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val listPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                // Wider than tall: the question on the left, the answers beside it, both in view.
                Row(Modifier.widthIn(max = 1040.dp).fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        controls()
                        extras()
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        results()
                    }
                }
            } else {
                LazyColumn(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    controls()
                    results()
                    extras()
                }
            }
        }
    }

    if (picking) {
        val current = if (mode == SleepMode.Wake) wakeAt else bedAt
        TimePickerDialog(
            initial = LocalTime.of(current / 60, current % 60),
            title = if (mode == SleepMode.Wake) R.string.sleep_pick_wake else R.string.sleep_pick_bed,
            onDismiss = { picking = false },
            onConfirm = { h, m ->
                picking = false
                if (mode == SleepMode.Wake) pickedWake = h * 60 + m else bedAt = h * 60 + m
            },
        )
    }
    confirming?.let { plan ->
        BedtimeDialog(
            plan,
            alarmOn = plan.wake.toInstant() in alarmTimes,
            onDismiss = { confirming = null },
            onConfirm = { remind, alarm ->
                confirming = null
                if (remind) actions.onSetBedtime(plan.bedtime, plan.wake)
                if (alarm) actions.onSetAlarm(plan.wake)
            },
        )
    }
}

/** One suggestion: the time, cycles and hours of sleep; Recommended for 5 or 6 cycles. */
@Composable
private fun PlanCard(
    plan: SleepPlan,
    time: String,
    icon: Int,
    action: String?,
    onClick: (() -> Unit)?,
) {
    val colors =
        if (plan.recommended) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time, style = MaterialTheme.typography.headlineLarge)
                    if (plan.recommended) {
                        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                            Text(
                                stringResource(R.string.sleep_recommended),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.sleep_cycles, plan.cycles, plan.cycles) + " · " +
                        stringResource(R.string.sleep_hours, sleepLength(plan.sleepMinutes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(icon), contentDescription = null)
                if (action != null) Text(action, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** "7 hours 30 minutes", "9 hours". */
@Composable
private fun sleepLength(minutes: Int): String {
    val r = LocalResources.current
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { r.getQuantityString(R.plurals.duration_hours, it, it) },
        m.takeIf { it > 0 }?.let { r.getQuantityString(R.plurals.duration_minutes, it, it) },
    ).joinToString(" ")
}

/** A bedtime was tapped: remind me then, set the wake-up alarm, or both. */
@Composable
private fun BedtimeDialog(
    plan: SleepPlan,
    alarmOn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (remind: Boolean, alarm: Boolean) -> Unit,
) {
    val format = rememberClockFormatter()
    var remind by rememberSaveable { mutableStateOf(true) }
    var alarm by rememberSaveable { mutableStateOf(!alarmOn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_bedtime_title, format.time(plan.bedtime))) },
        text = {
            Column {
                CheckRow(stringResource(R.string.sleep_remind_me, format.time(plan.bedtime)), remind) { remind = it }
                if (alarmOn) {
                    Text(
                        stringResource(R.string.sleep_alarm_on) + " · " + format.time(plan.wake),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    CheckRow(stringResource(R.string.sleep_set_alarm_for, format.time(plan.wake)), alarm) { alarm = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(remind, alarm && !alarmOn) }, enabled = remind || (alarm && !alarmOn)) {
                Text(stringResource(R.string.sleep_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CheckRow(
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

private object NoSleepActions : SleepActions {
    override fun onSetAlarm(wake: ZonedDateTime) = Unit

    override fun onSetBedtime(
        bedtime: ZonedDateTime,
        wake: ZonedDateTime?,
    ) = Unit

    override fun onCancelBedtime() = Unit
}

private val previewNow = ZonedDateTime.of(2026, 11, 25, 22, 40, 0, 0, java.time.ZoneId.of("Australia/Melbourne"))

// Going to bed now: wake-up times, one with its alarm already on.
@TesseraPreviews
@Composable
private fun SleepNowPreview() {
    ClockPreview {
        SleepScreen(
            now = previewNow,
            settings = ClockSettings(),
            alarms = listOf(Alarm(1, 6, 25)),
            reminder = null,
            actions = NoSleepActions,
            onBack = {},
        )
    }
}

// Waking at the next alarm's time: bedtimes, with a bedtime reminder set.
@TesseraPreviews
@Composable
private fun SleepWakePreview() {
    ClockPreview {
        SleepScreen(
            now = previewNow.withHour(20),
            settings = ClockSettings(),
            alarms = listOf(Alarm(1, 6, 30)),
            reminder =
                BedtimeReminder(
                    previewNow
                        .withHour(22)
                        .withMinute(45)
                        .toInstant()
                        .toEpochMilli(),
                ),
            actions = NoSleepActions,
            onBack = {},
            initialMode = SleepMode.Wake,
        )
    }
}
