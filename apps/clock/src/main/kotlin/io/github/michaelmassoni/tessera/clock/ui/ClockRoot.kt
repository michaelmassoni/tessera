package io.github.michaelmassoni.tessera.clock.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.alarm.Alarm
import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.clock.graph
import io.github.michaelmassoni.tessera.clock.timer.TimerInput
import io.github.michaelmassoni.tessera.clock.worldclock.CityCatalog
import java.time.LocalTime
import io.github.michaelmassoni.tessera.core.design.R as DesignR

enum class ClockTab(
    val title: Int,
    val icon: Int,
) {
    Alarms(R.string.tab_alarm, DesignR.drawable.ic_alarm),
    World(R.string.tab_clock, DesignR.drawable.ic_public),
    Timer(R.string.tab_timer, DesignR.drawable.ic_hourglass),
    Stopwatch(R.string.tab_stopwatch, DesignR.drawable.ic_timer),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockRoot(
    viewModel: ClockViewModel,
    initialTab: Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val current = ClockTab.entries[tab.coerceIn(0, ClockTab.entries.lastIndex)]
    val snackbar = remember { SnackbarHostState() }
    var addingAlarm by rememberSaveable { mutableStateOf(false) }
    var pickingCity by rememberSaveable { mutableStateOf(false) }
    var timerInput by rememberSaveable(stateSaver = TimerInputSaver) { mutableStateOf(TimerInput()) }
    var addingTimer by rememberSaveable { mutableStateOf(false) }

    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val stopwatch by viewModel.stopwatch.collectAsStateWithLifecycle()
    val cityIds by viewModel.cities.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val catalog = remember(locale) { CityCatalog.icu(locale) }

    val context = LocalContext.current
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ensureNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    EventSnackbars(
        events = viewModel.events,
        snackbar = snackbar,
        onUndoDelete = viewModel::restoreAlarm,
        onUndoRemoveCity = viewModel::restoreCity,
    )

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            ClockTab.entries.forEach { t ->
                item(
                    selected = t == current,
                    onClick = { tab = t.ordinal },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(stringResource(t.title)) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(current.title)) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painterResource(DesignR.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                val fab: (() -> Unit)? =
                    when (current) {
                        ClockTab.Alarms -> {
                            {
                                ensureNotifications()
                                addingAlarm = true
                            }
                        }

                        ClockTab.World -> {
                            { pickingCity = true }
                        }

                        ClockTab.Timer -> {
                            if (timers.isNotEmpty() && !addingTimer) ({ addingTimer = true }) else null
                        }

                        ClockTab.Stopwatch -> {
                            null
                        }
                    }
                if (fab != null) {
                    MediumFloatingActionButton(onClick = fab) {
                        Icon(
                            painterResource(DesignR.drawable.ic_add),
                            contentDescription = stringResource(R.string.action_add),
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val listPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom =
                        padding.calculateBottomPadding() + 96.dp,
                )
            when (current) {
                ClockTab.Alarms -> {
                    AlarmsScreen(
                        alarms = alarms,
                        actions =
                            object : AlarmActions {
                                override fun onUpdate(alarm: Alarm) = viewModel.updateAlarm(alarm)

                                override fun onDelete(alarm: Alarm) = viewModel.deleteAlarm(alarm)

                                override fun onCancelSnooze(alarm: Alarm) = viewModel.cancelSnooze(alarm)
                            },
                        banners = { PermissionBanners() },
                        contentPadding = listPadding,
                    )
                }

                ClockTab.World -> {
                    val now by rememberTicker(60_000) { System.currentTimeMillis() }
                    val zoned =
                        java.time.Instant
                            .ofEpochMilli(now)
                            .atZone(java.time.ZoneId.systemDefault())
                    WorldClockScreen(
                        now = zoned,
                        cities = cityIds.mapNotNull(catalog::find),
                        onRemove = { viewModel.removeCity(it.zoneId) },
                        contentPadding = listPadding,
                    )
                }

                ClockTab.Timer -> {
                    if (timers.isEmpty() || addingTimer) {
                        androidx.activity.compose.BackHandler(enabled = addingTimer) { addingTimer = false }
                        TimerKeypad(
                            input = timerInput,
                            onInput = { timerInput = it },
                            onStart = {
                                ensureNotifications()
                                viewModel.createTimer(timerInput.durationMillis)
                                timerInput = TimerInput()
                                addingTimer = false
                            },
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        val elapsed by rememberTicker(100) { SystemClock.elapsedRealtime() }
                        TimerList(
                            timers = timers,
                            elapsedNow = elapsed,
                            actions =
                                object : TimerActions {
                                    override fun onStart(id: Int) = viewModel.startTimer(id)

                                    override fun onPause(id: Int) = viewModel.pauseTimer(id)

                                    override fun onReset(id: Int) = viewModel.resetTimer(id)

                                    override fun onAddMinute(id: Int) = viewModel.addMinute(id)

                                    override fun onLabel(
                                        id: Int,
                                        label: String,
                                    ) = viewModel.setTimerLabel(id, label)

                                    override fun onDelete(id: Int) = viewModel.deleteTimer(id)
                                },
                            contentPadding = listPadding,
                        )
                    }
                }

                ClockTab.Stopwatch -> {
                    val elapsed by rememberTicker(
                        if (stopwatch.running) 16 else 1_000,
                    ) { SystemClock.elapsedRealtime() }
                    StopwatchScreen(
                        stopwatch = stopwatch,
                        elapsedNow = elapsed,
                        actions =
                            object : StopwatchActions {
                                override fun onStart() = viewModel.startStopwatch()

                                override fun onPause() = viewModel.pauseStopwatch()

                                override fun onLap() = viewModel.lap()

                                override fun onReset() = viewModel.resetStopwatch()
                            },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    if (addingAlarm) {
        val now = LocalTime.now()
        TimePickerDialog(
            initial = now.withMinute(0).plusHours(1),
            onDismiss = { addingAlarm = false },
            onConfirm = { h, m ->
                addingAlarm = false
                viewModel.addAlarm(h, m)
            },
        )
    }
    if (pickingCity) {
        CityPicker(
            catalog = catalog,
            onPick = {
                viewModel.addCity(it.zoneId)
                pickingCity = false
            },
            onClose = { pickingCity = false },
        )
    }
}

private val TimerInputSaver =
    androidx.compose.runtime.saveable.Saver<TimerInput, String>(
        save = { it.digits },
        restore = { TimerInput(it) },
    )

@Composable
private fun EventSnackbars(
    events: kotlinx.coroutines.flow.Flow<ClockEvent>,
    snackbar: SnackbarHostState,
    onUndoDelete: (Alarm) -> Unit,
    onUndoRemoveCity: (String, Int) -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val undo = stringResource(R.string.action_undo)
    LaunchedEffect(events, resources) {
        events.collect { event ->
            when (event) {
                is ClockEvent.AlarmSet -> {
                    val r = resources
                    val parts =
                        listOfNotNull(
                            event.until.days.takeIf { it > 0 }?.let {
                                r.getQuantityString(
                                    R.plurals.duration_days,
                                    it.toInt(),
                                    it.toInt(),
                                )
                            },
                            event.until.hours.takeIf { it > 0 }?.let {
                                r.getQuantityString(
                                    R.plurals.duration_hours,
                                    it.toInt(),
                                    it.toInt(),
                                )
                            },
                            event.until.minutes
                                .takeIf { it > 0 || (event.until.days == 0L && event.until.hours == 0L) }
                                ?.let {
                                    r.getQuantityString(R.plurals.duration_minutes, it.toInt(), it.toInt())
                                },
                        )
                    snackbar.showSnackbar(resources.getString(R.string.alarm_set_for, parts.joinToString(" ")))
                }

                is ClockEvent.AlarmDeleted -> {
                    val result =
                        snackbar.showSnackbar(
                            resources.getString(R.string.alarm_deleted),
                            actionLabel = undo,
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) onUndoDelete(event.alarm)
                }

                is ClockEvent.CityRemoved -> {
                    val result =
                        snackbar.showSnackbar(
                            resources.getString(R.string.city_removed),
                            actionLabel = undo,
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) onUndoRemoveCity(event.zoneId, event.index)
                }
            }
        }
    }
}

/** Explains anything that would stop alarms from ringing properly, with a fix button. */
@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    val graph = context.graph
    var exact by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    var fullScreen by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        exact = graph.scheduler.canScheduleExactAlarms()
        notifications = graph.notifications.canPost()
        fullScreen = graph.notifications.canUseFullScreenIntent()
        onPauseOrDispose { }
    }
    val pkg = android.net.Uri.fromParts("package", context.packageName, null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!exact) {
            Banner(stringResource(R.string.banner_exact_alarms)) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            }
        }
        if (!notifications) {
            Banner(stringResource(R.string.banner_notifications)) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        if (!fullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Banner(stringResource(R.string.banner_full_screen)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    onFix: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
        }
    }
}
