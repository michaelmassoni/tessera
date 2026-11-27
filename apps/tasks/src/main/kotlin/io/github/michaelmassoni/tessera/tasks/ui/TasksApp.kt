package io.github.michaelmassoni.tessera.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraTextField
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.data.TaskStore
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Row
import io.github.michaelmassoni.tessera.tasks.engine.Sort
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.LocalDateTime
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The Starred view's id, in place of a list's. */
const val STARRED = "*starred"

/** Why sync needs attention. */
enum class SyncNotice { Failed, SignIn }

data class TasksState(
    val data: TaskData = TaskData(),
    val signedIn: Boolean = false,
    val syncing: Boolean = false,
    val notice: SyncNotice? = null,
    // Exact alarms are off and a task has a time, so its reminder may come up to an hour late.
    val alarmsLate: Boolean = false,
    // Stars (on tasks, the Starred view, "starred first"); the person may turn them off.
    val stars: Boolean = true,
)

/** What the screens ask for. */
interface TasksActions {
    /** Adds a task and returns its id. */
    fun add(
        listId: String,
        title: String,
        notes: String = "",
        due: Due? = null,
        repeat: Repeat? = null,
        starred: Boolean = false,
        parentId: String? = null,
    ): String

    fun toggle(id: String)

    fun save(task: Task)

    fun delete(id: String)

    fun undelete(id: String)

    fun move(
        listId: String,
        id: String,
        to: Int,
    )

    fun newList(
        name: String,
        onServer: Boolean,
    )

    fun renameList(
        id: String,
        name: String,
    )

    fun deleteList(id: String)

    fun deleteCompleted(listId: String)

    fun refresh()

    fun openSettings()

    /** Opens the system setting that allows exact alarms. */
    fun allowAlarms() {}
}

/** A list's name, with the first phone-only list's default name localised. */
@Composable
fun TaskList.title() = if (name == TaskStore.DEFAULT_NAME && !synced) stringResource(R.string.my_tasks) else name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksApp(
    state: TasksState,
    actions: TasksActions,
    modifier: Modifier = Modifier,
    openTask: String? = null,
) {
    val lists = state.data.lists.sortedBy { it.order }
    var selected by rememberSaveable { mutableStateOf(lists.firstOrNull()?.id ?: STARRED) }
    if (selected != STARRED && lists.none { it.id == selected }) selected = lists.firstOrNull()?.id ?: STARRED
    // With stars off there's no Starred view to be on.
    if (selected == STARRED && !state.stars) lists.firstOrNull()?.let { selected = it.id }
    var editing by rememberSaveable { mutableStateOf(openTask) }
    // A task deleted in the editor: the list offers Undo.
    var deleted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openTask) { if (openTask != null) editing = openTask }
    BackHandler(editing != null) { editing = null }
    val open = editing?.takeIf { state.data.task(it)?.deleted == false }
    // The list (and a parent under its subtask) keeps its scroll position while a task is open over
    // it; a closed task's is dropped.
    val saveable = rememberSaveableStateHolder()
    val pages = remember(saveable) { PageStack(saveable::removeState) }
    val stack = taskPages(open) { state.data.task(it)?.parentId }
    SideEffect { pages.show(stack) }
    AnimatedContent(
        targetState = open,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150))
            } else {
                fadeIn(tween(250)) togetherWith (slideOutHorizontally(tween(250)) { it / 5 } + fadeOut(tween(200)))
            }
        },
        label = "tasks",
        contentKey = ::taskPageKey,
        modifier = modifier,
    ) { id ->
        saveable.SaveableStateProvider(taskPageKey(id)) {
            if (id != null) {
                val task = state.data.task(id)
                if (task != null) {
                    TaskEditor(task, state.data, actions, state.stars, onOpen = {
                        editing = it
                    }, onClose = { editing = task.parentId }, onDeleted = {
                        deleted = task.id
                        editing = task.parentId
                    })
                }
            } else {
                ListScreen(state, lists, selected, { selected = it }, actions, onOpen = { editing = it }, deleted = deleted, onUndoShown = {
                    deleted =
                        null
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    state: TasksState,
    lists: List<TaskList>,
    selected: String,
    onSelect: (String) -> Unit,
    actions: TasksActions,
    onOpen: (String) -> Unit,
    deleted: String?,
    onUndoShown: () -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var newList by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(Sort.MyOrder) }
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.task_deleted)
    val undoText = stringResource(R.string.undo)
    val failed = stringResource(R.string.sync_failed)
    val signIn = stringResource(R.string.sync_sign_in)
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(if (it == SyncNotice.SignIn) signIn else failed) }
    }
    LaunchedEffect(deleted) {
        val id = deleted ?: return@LaunchedEffect
        onUndoShown()
        if (snackbar.showSnackbar(deletedText, undoText) == SnackbarResult.ActionPerformed) actions.undelete(id)
    }
    // A list just made opens, as in Google Tasks (a server list arrives once the server has made it).
    var listsBefore by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(lists) {
        val before = listsBefore ?: return@LaunchedEffect
        lists.firstOrNull { it.id !in before }?.let {
            onSelect(it.id)
            listsBefore = null
        }
    }
    val stars = state.stars
    val tabs = (if (stars) listOf(STARRED) else emptyList()) + lists.map { it.id }
    // One list and no Starred view: no tabs to switch between, so the list's name and its menus
    // go in the top bar instead of a heading.
    val single = lists.singleOrNull()?.takeIf { tabs.size == 1 }
    var renaming by remember { mutableStateOf(false) }
    var deletingList by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            Column {
                if (single != null) {
                    TopAppBar(
                        title = { Text(single.title(), maxLines = 1) },
                        actions = {
                            ListMenus(
                                stars = stars,
                                sort = sort,
                                onSort = { sort = it },
                                onRename = { renaming = true },
                                onDelete = { deletingList = true },
                                onDeleteCompleted = { actions.deleteCompleted(single.id) },
                                hasCompleted = state.data.tasksOf(single.id).any { it.completed },
                                onNewList = { newList = true },
                            )
                            IconButton(onClick = actions::openSettings) {
                                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                            }
                        },
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            IconButton(onClick = actions::openSettings) {
                                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                            }
                        },
                    )
                }
                if (single == null) {
                    PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0), edgePadding = 8.dp) {
                        if (stars) {
                            Tab(selected = selected == STARRED, onClick = { onSelect(STARRED) }, icon = {
                                Icon(
                                    painterResource(DesignR.drawable.ic_star_filled),
                                    contentDescription = stringResource(R.string.starred),
                                )
                            })
                        }
                        lists.forEach { l ->
                            Tab(selected = selected == l.id, onClick = { onSelect(l.id) }, text = { Text(l.title(), maxLines = 1) })
                        }
                        Tab(selected = false, onClick = { newList = true }, text = { Text("+ " + stringResource(R.string.new_list)) })
                    }
                }
                if (state.alarmsLate) AlarmsBanner(onAllow = actions::allowAlarms)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(painterResource(DesignR.drawable.ic_add), contentDescription = stringResource(R.string.new_task))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Each list (and Starred) keeps its own scroll position when switching between them.
        val tabState = rememberSaveableStateHolder()
        val content: @Composable () -> Unit = {
            tabState.SaveableStateProvider("tab:$selected") {
                if (selected == STARRED) {
                    StarredList(state.data, actions, onOpen)
                } else {
                    state.data.list(selected)?.let { list ->
                        TaskListContent(list, state.data, sort, { sort = it }, actions, stars, onOpen, showHeader = single == null)
                    }
                }
            }
        }
        if (state.signedIn) {
            PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = actions::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                content()
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
    if (adding) {
        val listId = if (selected == STARRED) lists.firstOrNull()?.id else selected
        if (listId !=
            null
        ) {
            AddTaskSheet(listId, starred = selected == STARRED, actions = actions, stars = stars, onDismiss = { adding = false })
        }
    }
    single?.let { ListDialogs(it, actions, renaming, { renaming = it }, deletingList, { deletingList = it }) }
    if (newList) {
        NewListDialog(state.signedIn, onDismiss = { newList = false }) { name, onServer ->
            listsBefore = lists.map { it.id }.toSet()
            actions.newList(name, onServer)
        }
    }
}

@Composable
private fun TaskListContent(
    list: TaskList,
    data: TaskData,
    sort: Sort,
    onSort: (Sort) -> Unit,
    actions: TasksActions,
    stars: Boolean,
    onOpen: (String) -> Unit,
    showHeader: Boolean = true,
) {
    val view = remember(data, list.id, sort) { TaskOps.view(data.tasksOf(list.id), sort) }
    var showCompleted by rememberSaveable(list.id) { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deletingList by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    // Each top-level task with its subtasks is one item, so they move together.
    val groups = remember(view) { groups(view.open) }
    var order by remember(groups) { mutableStateOf(groups) }
    val listState = rememberLazyListState()
    val reorder =
        rememberReorderableLazyListState(listState) { from, to ->
            val a = order.indexOfFirst { it.first().task.id == from.key }
            val b = order.indexOfFirst { it.first().task.id == to.key }
            if (a >= 0 && b >= 0) order = order.toMutableList().apply { add(b, removeAt(a)) }
        }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        if (showHeader) {
            item(key = "header") {
                ListHeader(list, stars, sort, onSort, onRename = {
                    renaming = true
                }, onDelete = { deletingList = true }, onDeleteCompleted = {
                    actions.deleteCompleted(list.id)
                }, hasCompleted = view.completed.isNotEmpty())
            }
        }
        if (order.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    if (view.completed.isEmpty()) stringResource(R.string.empty_list) else stringResource(R.string.all_done),
                    stringResource(R.string.empty_list_hint),
                )
            }
        }
        items(order, key = { it.first().task.id }) { group ->
            ReorderableItem(reorder, key = group.first().task.id, enabled = sort == Sort.MyOrder) { dragging ->
                Surface(
                    tonalElevation = if (dragging) 4.dp else 0.dp,
                    color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                ) {
                    Column {
                        group.forEach { row ->
                            val index = order.indexOf(group)
                            TaskRow(
                                row,
                                today,
                                now,
                                actions,
                                onOpen,
                                stars,
                                dragHandle =
                                    if (sort == Sort.MyOrder && row.depth == 0) {
                                        Modifier.longPressDraggableHandle(onDragStopped = {
                                            val to = order.indexOf(group)
                                            if (to != groups.indexOf(group)) actions.move(list.id, row.task.id, to)
                                        })
                                    } else {
                                        null
                                    },
                                moveActions =
                                    if (sort == Sort.MyOrder && row.depth == 0) {
                                        listOfNotNull(
                                            if (index > 0) R.string.move_up to { actions.move(list.id, row.task.id, index - 1) } else null,
                                            if (index <
                                                order.lastIndex
                                            ) {
                                                R.string.move_down to { actions.move(list.id, row.task.id, index + 1) }
                                            } else {
                                                null
                                            },
                                        )
                                    } else {
                                        emptyList()
                                    },
                            )
                        }
                    }
                }
            }
        }
        if (view.completed.isNotEmpty()) {
            item(key = "completed") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(pluralStringResource(R.plurals.completed_count, view.completed.size, view.completed.size))
                    Icon(
                        painterResource(if (showCompleted) DesignR.drawable.ic_expand_less else DesignR.drawable.ic_expand_more),
                        contentDescription = null,
                    )
                }
            }
            if (showCompleted) {
                items(view.completed, key = { "c-" + it.id }) { t -> TaskRow(Row(t), today, now, actions, onOpen, stars) }
            }
        }
    }
    ListDialogs(list, actions, renaming, { renaming = it }, deletingList, { deletingList = it })
}

/** Renaming and deleting a list (from its heading, or the top bar when it's the only list). */
@Composable
private fun ListDialogs(
    list: TaskList,
    actions: TasksActions,
    renaming: Boolean,
    onRenaming: (Boolean) -> Unit,
    deletingList: Boolean,
    onDeleting: (Boolean) -> Unit,
) {
    if (renaming) {
        NameDialog(R.string.rename, list.title(), onDismiss = { onRenaming(false) }) {
            actions.renameList(list.id, it)
            onRenaming(false)
        }
    }
    if (deletingList) {
        AlertDialog(
            onDismissRequest = { onDeleting(false) },
            title = { Text(stringResource(R.string.delete_list_title, list.title())) },
            text = { Text(stringResource(if (list.synced) R.string.delete_list_server_text else R.string.delete_list_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleting(false)
                    actions.deleteList(list.id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { onDeleting(false) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Open rows grouped: a top-level task followed by its subtasks. */
private fun groups(rows: List<Row>): List<List<Row>> {
    val out = mutableListOf<MutableList<Row>>()
    rows.forEach { r -> if (r.depth == 0 || out.isEmpty()) out += mutableListOf(r) else out.last() += r }
    return out
}

@Composable
private fun ListHeader(
    list: TaskList,
    stars: Boolean,
    sort: Sort,
    onSort: (Sort) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteCompleted: () -> Unit,
    hasCompleted: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(list.title(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
        ListMenus(stars, sort, onSort, onRename, onDelete, onDeleteCompleted, hasCompleted)
    }
}

/** A list's Sort and more-options menus ([onNewList] adds "New list", for the top bar). */
@Composable
private fun ListMenus(
    stars: Boolean,
    sort: Sort,
    onSort: (Sort) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteCompleted: () -> Unit,
    hasCompleted: Boolean,
    onNewList: (() -> Unit)? = null,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    run {
        Box {
            IconButton(onClick = { sortMenu = true }) {
                Icon(painterResource(DesignR.drawable.ic_sort), contentDescription = stringResource(R.string.sort_by))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                Sort.entries.filter { stars || it != Sort.Starred }.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    when (s) {
                                        Sort.MyOrder -> R.string.sort_my_order
                                        Sort.Date -> R.string.sort_date
                                        Sort.Starred -> R.string.sort_starred
                                        Sort.Title -> R.string.sort_title
                                    },
                                ),
                            )
                        },
                        onClick = {
                            sortMenu = false
                            onSort(s)
                        },
                        trailingIcon =
                            if (s ==
                                sort
                            ) {
                                ({ Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) })
                            } else {
                                null
                            },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                onNewList?.let { add ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.new_list)) }, onClick = {
                        menu = false
                        add()
                    })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete_completed)) }, enabled = hasCompleted, onClick = {
                    menu = false
                    onDeleteCompleted()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete_list)) }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun StarredList(
    data: TaskData,
    actions: TasksActions,
    onOpen: (String) -> Unit,
) {
    val rows = remember(data) { TaskOps.starred(data.tasks) }
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Text(
                stringResource(R.string.starred),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        if (rows.isEmpty()) item(key = "empty") { EmptyState(stringResource(R.string.empty_starred), "") }
        items(
            rows,
            key = { it.task.id },
        ) { row -> TaskRow(row, today, now, actions, onOpen, stars = true, listName = data.list(row.task.listId)?.title()) }
    }
}

@Composable
private fun EmptyState(
    title: String,
    hint: String,
) {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_checklist),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint.isNotBlank()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One task: a round tick, its title and details, its date, and a star. */
@Composable
internal fun TaskRow(
    row: Row,
    today: LocalDate,
    now: LocalDateTime,
    actions: TasksActions,
    onOpen: (String) -> Unit,
    stars: Boolean,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
    listName: String? = null,
    moveActions: List<Pair<Int, () -> Unit>> = emptyList(),
) {
    val t = row.task
    val labels = moveActions.map { stringResource(it.first) to it.second }
    Row(
        modifier
            .fillMaxWidth()
            .let { if (dragHandle != null) it.then(dragHandle) else it }
            .semantics { customActions = labels.map { (label, run) -> CustomAccessibilityAction(label) { run().let { true } } } }
            .padding(start = 8.dp + 40.dp * row.depth, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { actions.toggle(t.id) }) {
            Icon(
                painterResource(if (t.completed) DesignR.drawable.ic_check_circle else DesignR.drawable.ic_radio_button_unchecked),
                contentDescription = stringResource(if (t.completed) R.string.mark_not_done else R.string.mark_done),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(
            onClick = { onOpen(t.id) },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    t.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (t.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (t.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    maxLines = 2,
                )
                if (t.notes.isNotBlank() && !t.completed) {
                    Text(
                        t.notes.lineSequence().first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                val due = t.due
                if ((due != null && !t.completed) || listName != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (due != null && !t.completed) {
                            val late = TaskOps.overdue(t, now)
                            DueChip(dueLabel(due, today), late, repeating = t.repeat != null)
                        }
                        if (listName !=
                            null
                        ) {
                            Text(
                                listName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (!t.completed && stars) {
            IconButton(onClick = { actions.save(t.copy(starred = !t.starred, modifiedAt = System.currentTimeMillis(), dirty = true)) }) {
                Icon(
                    painterResource(if (t.starred) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                    contentDescription = stringResource(if (t.starred) R.string.unstar else R.string.star),
                    tint = if (t.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DueChip(
    text: String,
    late: Boolean,
    repeating: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (late) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (late) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
            if (repeating) Icon(painterResource(DesignR.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun NameDialog(
    title: Int,
    initial: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { TesseraTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = { onDone(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NewListDialog(
    signedIn: Boolean,
    onDismiss: () -> Unit,
    onDone: (String, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var onServer by rememberSaveable { mutableStateOf(signedIn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 400.dp)) {
                TesseraTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true)
                if (signedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.on_server), modifier = Modifier.weight(1f))
                        Switch(checked = onServer, onCheckedChange = { onServer = it })
                    }
                }
                Spacer(Modifier.size(0.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone(name.trim(), onServer)
                onDismiss()
            }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks for exact alarms when reminders would otherwise be late. */
@Composable
private fun AlarmsBanner(onAllow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.alarms_late),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAllow,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Text(stringResource(R.string.alarms_allow))
            }
        }
    }
}
