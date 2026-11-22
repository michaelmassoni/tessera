package io.github.michaelmassoni.tessera.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Row
import io.github.michaelmassoni.tessera.tasks.engine.Sort
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import io.github.michaelmassoni.tessera.core.design.R as DesignR

private val noBackground =
    @Composable {
        TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )
    }

/**
 * Adding tasks: a title (Enter adds it and clears the field for the next one), and if wanted,
 * details, a date and time, and a star.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTaskSheet(
    listId: String,
    starred: Boolean,
    actions: TasksActions,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var due by remember { mutableStateOf<Due?>(null) }
    var star by rememberSaveable { mutableStateOf(starred) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // The sheet is its own window: focus only takes once it's on screen.
        delay(FOCUS_DELAY_MS)
        focus.requestFocus()
        keyboard?.show()
    }

    fun add() {
        if (title.isBlank()) return
        actions.add(listId, title.trim(), notes.trim(), due, starred = star)
        title = ""
        notes = ""
        due = null
        star = starred
    }
    // Short enough that half-open looks like open, so back went to half-open first and seemed to do
    // nothing: skip that state.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
        ) {
            TextField(
                title,
                { title = it },
                placeholder = { Text(stringResource(R.string.new_task)) },
                singleLine = true,
                colors = noBackground(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (showNotes) {
                TextField(
                    notes,
                    { notes = it },
                    placeholder = { Text(stringResource(R.string.details_hint)) },
                    colors = noBackground(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            due?.let { d ->
                InputChip(
                    selected = true,
                    onClick = { picking = true },
                    label = { Text(dueLabel(d, LocalDate.now())) },
                    trailingIcon = {
                        IconButton(onClick = { due = null }) {
                            Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.clear))
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showNotes = !showNotes }) {
                    Icon(painterResource(DesignR.drawable.ic_notes), contentDescription = stringResource(R.string.details_hint))
                }
                IconButton(onClick = { picking = true }) {
                    Icon(painterResource(DesignR.drawable.ic_schedule), contentDescription = stringResource(R.string.add_date))
                }
                IconButton(onClick = { star = !star }) {
                    Icon(
                        painterResource(if (star) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                        contentDescription = stringResource(if (star) R.string.unstar else R.string.star),
                        tint = if (star) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.foundation.layout
                    .Spacer(Modifier.weight(1f))
                TextButton(onClick = ::add, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (picking) DueDialog(due, onDismiss = { picking = false }) { due = it }
}

/** A task in full, saved as it's edited: title, details, list, date and time, repeat, star, subtasks. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskEditor(
    task: Task,
    data: TaskData,
    actions: TasksActions,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.notes) }
    var picking by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var subtask by rememberSaveable(task.id) { mutableStateOf("") }

    fun save(change: (Task) -> Task) {
        val current = data.task(task.id) ?: return
        actions.save(change(current).copy(modifiedAt = System.currentTimeMillis(), dirty = true))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { save { it.copy(starred = !it.starred) } }) {
                        Icon(
                            painterResource(if (task.starred) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                            contentDescription = stringResource(if (task.starred) R.string.unstar else R.string.star),
                            tint = if (task.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        actions.delete(task.id)
                        onDeleted()
                    }) { Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 8.dp)) {
                // The list (move to another).
                Row(Modifier.padding(start = 8.dp)) {
                    AssistChip(onClick = { listMenu = true }, label = { Text(data.list(task.listId)?.title().orEmpty()) }, trailingIcon = {
                        Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = stringResource(R.string.move_to))
                    })
                    DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                        data.lists.sortedBy { it.order }.forEach { l ->
                            DropdownMenuItem(text = { Text(l.title()) }, onClick = {
                                listMenu = false
                                if (l.id != task.listId) {
                                    // Its subtasks go with it.
                                    TaskOps.withSubtasks(task, data.tasks).forEach { t ->
                                        actions.save(t.copy(listId = l.id, modifiedAt = System.currentTimeMillis(), dirty = true))
                                    }
                                }
                            })
                        }
                    }
                }
                TextField(
                    title,
                    {
                        title = it
                        save { t -> t.copy(title = it) }
                    },
                    placeholder = { Text(stringResource(R.string.title_hint)) },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    colors = noBackground(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldRow(DesignR.drawable.ic_notes) {
                    TextField(
                        notes,
                        {
                            notes = it
                            save { t -> t.copy(notes = it) }
                        },
                        placeholder = { Text(stringResource(R.string.details_hint)) },
                        colors = noBackground(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FieldRow(DesignR.drawable.ic_schedule) {
                    val d = task.due
                    if (d == null) {
                        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.add_date)) }
                    } else {
                        InputChip(
                            selected = true,
                            onClick = { picking = true },
                            label = { Text(dueLabel(d, LocalDate.now())) },
                            trailingIcon = {
                                IconButton(onClick = { save { it.copy(due = null, repeat = null) } }) {
                                    Icon(
                                        painterResource(DesignR.drawable.ic_close),
                                        contentDescription = stringResource(R.string.clear),
                                    )
                                }
                            },
                        )
                    }
                }
                if (task.due != null) {
                    FieldRow(DesignR.drawable.ic_repeat) {
                        TextButton(onClick = { repeating = true }) {
                            Text(task.repeat?.let { repeatLabel(it) } ?: stringResource(R.string.does_not_repeat))
                        }
                    }
                }
                if (task.parentId == null) {
                    Text(
                        stringResource(R.string.subtasks),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    val today = LocalDate.now()
                    val now = LocalDateTime.now()
                    val subtasks = TaskOps.view(data.tasks.filter { it.parentId == task.id }, Sort.MyOrder)
                    (subtasks.open.map { it.task } + subtasks.completed).forEach { s ->
                        TaskRow(Row(s), today, now, actions, onOpen)
                    }
                    TextField(
                        subtask,
                        { subtask = it },
                        placeholder = { Text(stringResource(R.string.add_subtask)) },
                        singleLine = true,
                        leadingIcon = { Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null) },
                        colors = noBackground(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                if (subtask.isNotBlank()) {
                                    actions.add(task.listId, subtask.trim(), parentId = task.id)
                                    subtask = ""
                                }
                            }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (picking) {
        DueDialog(task.due, onDismiss = { picking = false }) { d ->
            save {
                it.copy(
                    due = d,
                    repeat =
                        if (d ==
                            null
                        ) {
                            null
                        } else {
                            it.repeat
                        },
                )
            }
        }
    }
    if (repeating) RepeatDialog(task.repeat, task.due, onDismiss = { repeating = false }) { r -> save { it.copy(repeat = r) } }
}

/** A date, then optionally a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDialog(
    initial: Due?,
    onDismiss: () -> Unit,
    onPick: (Due?) -> Unit,
) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val start = initial?.localDate ?: LocalDate.now()
    if (date == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start
                }) { Text(stringResource(R.string.time)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start
                    onPick(Due.of(picked))
                    onDismiss()
                }) { Text(stringResource(R.string.no_time)) }
            },
        ) { DatePicker(state) }
    } else {
        val time = initial?.localTime ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(time.hour, time.minute)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.time)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(Due.of(date!!, LocalTime.of(state.hour, state.minute)))
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onPick(Due.of(date!!))
                    onDismiss()
                }) { Text(stringResource(R.string.no_time)) }
            },
        )
    }
}

/** How often, on which weekdays, and whether to count from when it's done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatDialog(
    initial: Repeat?,
    due: Due?,
    onDismiss: () -> Unit,
    onPick: (Repeat?) -> Unit,
) {
    var frequency by remember { mutableStateOf(initial?.frequency) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var days by remember { mutableStateOf(initial?.weekdays ?: setOfNotNull(due?.localDate?.dayOfWeek?.value)) }
    var afterDone by remember { mutableStateOf(initial?.fromCompletion ?: false) }
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.repeat)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == null,
                        onClick = { frequency = null },
                        label = { Text(stringResource(R.string.does_not_repeat)) },
                    )
                    listOf(
                        Repeat.Frequency.Hourly to R.string.freq_hours,
                        Repeat.Frequency.Daily to R.string.freq_days,
                        Repeat.Frequency.Weekly to R.string.freq_weeks,
                        Repeat.Frequency.Monthly to R.string.freq_months,
                        Repeat.Frequency.Yearly to R.string.freq_years,
                    ).forEach { (f, label) ->
                        FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(stringResource(label)) })
                    }
                }
                if (frequency != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.repeat_every))
                        OutlinedTextField(
                            interval,
                            { interval = it.filter(Char::isDigit).take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.width(88.dp),
                        )
                    }
                    if (frequency == Repeat.Frequency.Weekly) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..7).forEach { d ->
                                FilterChip(
                                    selected = d in days,
                                    onClick = { days = if (d in days) days - d else days + d },
                                    label = {
                                        Text(
                                            java.time.DayOfWeek
                                                .of(
                                                    d,
                                                ).getDisplayName(java.time.format.TextStyle.NARROW, locale),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.repeat_after_done_switch))
                            Text(
                                stringResource(R.string.repeat_after_done_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = afterDone, onCheckedChange = { afterDone = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(
                    frequency?.let {
                        Repeat(
                            it,
                            interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            if (it ==
                                Repeat.Frequency.Weekly
                            ) {
                                days
                            } else {
                                emptySet()
                            },
                            afterDone,
                        )
                    },
                )
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** An editor row: an icon, then the field. */
@Composable
private fun FieldRow(
    icon: Int,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(start = 8.dp)) { content() }
    }
}

private const val FOCUS_DELAY_MS = 300L
