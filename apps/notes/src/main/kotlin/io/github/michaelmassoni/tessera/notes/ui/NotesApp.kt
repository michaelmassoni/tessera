package io.github.michaelmassoni.tessera.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.notes.EditorState
import io.github.michaelmassoni.tessera.notes.ListState
import io.github.michaelmassoni.tessera.notes.NotesEvent
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.NoteView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the list can do; implemented over the ViewModel. */
interface ListActions {
    fun setView(view: NoteView)

    fun setSearch(text: String)

    fun open(note: Note)

    fun newNote(checklist: Boolean)

    fun toggleSelection(note: Note)

    fun clearSelection()

    fun selectAll()

    fun pinSelection()

    fun archiveSelection()

    fun trashSelection()

    fun restore(ids: Collection<String>)

    fun undoTrash(ids: List<String>)

    fun undoArchive(
        ids: List<String>,
        archived: Boolean,
    )

    fun deleteForever(ids: Collection<String>)

    fun emptyTrash()

    fun shareSelection()

    fun setLabels(
        ids: Collection<String>,
        add: Set<String>,
        remove: Set<String>,
    )

    fun openSettings()
}

private val ExpandedWidth = 840.dp

/**
 * The whole app: list and editor, one at a time on compact and medium widths, side by side on
 * expanded widths. Back closes the editor, then selection, then search, then returns to Notes.
 */
@Composable
fun NotesApp(
    list: ListState,
    editor: EditorState?,
    events: Flow<NotesEvent>,
    checkedLast: Boolean,
    listActions: ListActions,
    editorActions: EditorActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    var labelling by remember { mutableStateOf<Set<String>?>(null) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteForever by remember { mutableStateOf<Set<String>?>(null) }
    val wrapped =
        remember(editorActions) {
            object : EditorActions by editorActions {
                override fun editLabels(note: Note) {
                    labelling = setOf(note.id)
                }
            }
        }

    NoteEvents(events, snackbar, listActions)

    BackHandler(enabled = editor == null && (list.selection.isNotEmpty() || list.search.isNotEmpty() || list.view != NoteView.Notes)) {
        when {
            list.selection.isNotEmpty() -> listActions.clearSelection()
            list.search.isNotEmpty() -> listActions.setSearch("")
            else -> listActions.setView(NoteView.Notes)
        }
    }
    BackHandler(enabled = editor != null) { editorActions.close() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= ExpandedWidth
        val listWidth = (maxWidth * 0.4f).coerceIn(360.dp, 480.dp)
        val listPane: @Composable (Modifier) -> Unit = { m ->
            NoteListScreen(
                state = list,
                openId = if (expanded) editor?.note?.id else null,
                snackbar = snackbar,
                actions = listActions,
                onLabelSelection = { labelling = list.selection },
                onEmptyTrash = { confirmEmptyTrash = true },
                onDeleteForever = { confirmDeleteForever = list.selection },
                modifier = m,
            )
        }
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                listPane(Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                    if (editor != null) {
                        NoteEditor(editor, checkedLast, wrapped, showBack = false)
                    } else {
                        EmptyState(DesignR.drawable.ic_notes, stringResource(R.string.select_note))
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = editor != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "editor",
            ) { editing ->
                if (editing && editor != null) {
                    NoteEditor(editor, checkedLast, wrapped)
                } else {
                    listPane(Modifier.fillMaxSize())
                }
            }
        }
    }

    labelling?.let { ids ->
        val notes = ids.mapNotNull { id -> list.notes.firstOrNull { it.id == id } ?: editor?.note?.takeIf { it.id == id } }
        LabelDialog(
            allLabels = list.labels,
            notes = notes,
            onApply = { add, remove ->
                listActions.setLabels(ids, add, remove)
                labelling = null
            },
            onDismiss = { labelling = null },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(stringResource(R.string.empty_trash_confirm), stringResource(R.string.empty_trash_action), {
            listActions.emptyTrash()
            confirmEmptyTrash = false
        }) { confirmEmptyTrash = false }
    }
    confirmDeleteForever?.let { ids ->
        ConfirmDialog(stringResource(R.string.delete_forever_confirm), stringResource(R.string.action_delete_forever), {
            listActions.deleteForever(ids)
            confirmDeleteForever = null
        }) { confirmDeleteForever = null }
    }
}

@Composable
private fun NoteEvents(
    events: Flow<NotesEvent>,
    snackbar: SnackbarHostState,
    actions: ListActions,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is NotesEvent.Trashed -> {
                    val result =
                        snackbar.showSnackbar(
                            resources.getQuantityString(R.plurals.trashed, event.ids.size, event.ids.size),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) actions.undoTrash(event.ids)
                }

                is NotesEvent.Archived -> {
                    val plural = if (event.archived) R.plurals.archived else R.plurals.unarchived
                    val result =
                        snackbar.showSnackbar(
                            resources.getQuantityString(plural, event.ids.size, event.ids.size),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) actions.undoArchive(event.ids, event.archived)
                }

                NotesEvent.EmptyDiscarded -> {
                    snackbar.showSnackbar(resources.getString(R.string.empty_discarded))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteListScreen(
    state: ListState,
    openId: String?,
    snackbar: SnackbarHostState,
    actions: ListActions,
    onLabelSelection: () -> Unit,
    onEmptyTrash: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawer,
        modifier = modifier,
        drawerContent = {
            NotesDrawer(state, onSelect = { view ->
                scope.launch { drawer.close() }
                actions.setView(view)
            }, onSettings = {
                scope.launch { drawer.close() }
                actions.openSettings()
            })
        },
    ) {
        Scaffold(
            topBar = {
                if (state.selection.isNotEmpty()) {
                    SelectionBar(state, actions, onLabelSelection, onDeleteForever)
                } else {
                    ListSearchBar(state, actions, onOpenDrawer = { scope.launch { drawer.open() } }, onEmptyTrash = onEmptyTrash)
                }
            },
            floatingActionButton = {
                if (state.view == NoteView.Notes || state.view is NoteView.Label) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SmallFloatingActionButton(onClick = { actions.newNote(checklist = true) }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_checklist),
                                contentDescription = stringResource(R.string.new_checklist),
                            )
                        }
                        FloatingActionButton(onClick = { actions.newNote(checklist = false) }) {
                            Icon(painterResource(DesignR.drawable.ic_add), contentDescription = stringResource(R.string.new_note))
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.view == NoteView.Trash && state.notes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.trash_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.loaded && state.notes.isEmpty()) {
                    val (icon, text) =
                        when {
                            state.search.isNotEmpty() -> DesignR.drawable.ic_search to R.string.empty_search
                            state.view == NoteView.Archive -> DesignR.drawable.ic_archive to R.string.empty_archive
                            state.view == NoteView.Trash -> DesignR.drawable.ic_delete to R.string.empty_trash
                            state.view is NoteView.Label -> DesignR.drawable.ic_label to R.string.empty_label
                            else -> DesignR.drawable.ic_lightbulb to R.string.empty_notes
                        }
                    EmptyState(icon, stringResource(text))
                } else {
                    NoteGrid(
                        notes = state.notes,
                        selection = state.selection,
                        openId = openId,
                        onOpen = actions::open,
                        onToggleSelect = actions::toggleSelection,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
                        groupPinned = state.view != NoteView.Trash && state.search.isEmpty(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListSearchBar(
    state: ListState,
    actions: ListActions,
    onOpenDrawer: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val text = rememberTextFieldState(state.search)
    val searchState = rememberSearchBarState()
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(actions::setSearch) }
    LaunchedEffect(state.search) { if (state.search.isEmpty() && text.text.isNotEmpty()) text.edit { replace(0, length, "") } }
    val placeholder =
        when (val v = state.view) {
            NoteView.Notes -> stringResource(R.string.search_hint)
            NoteView.Archive -> stringResource(R.string.search_in, stringResource(R.string.view_archive))
            NoteView.Trash -> stringResource(R.string.search_in, stringResource(R.string.view_trash))
            is NoteView.Label -> stringResource(R.string.search_in, v.name)
        }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBar(
            state = searchState,
            modifier = Modifier.weight(1f),
            inputField = {
                SearchBarDefaults.InputField(
                    textFieldState = text,
                    searchBarState = searchState,
                    onSearch = {},
                    placeholder = { Text(placeholder) },
                    leadingIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(painterResource(DesignR.drawable.ic_menu), contentDescription = stringResource(R.string.open_menu))
                        }
                    },
                    trailingIcon = {
                        if (text.text.isNotEmpty()) {
                            IconButton(onClick = { text.edit { replace(0, length, "") } }) {
                                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                )
            },
        )
        if (state.view == NoteView.Trash && state.notes.isNotEmpty()) {
            TextButton(onClick = onEmptyTrash, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.empty_trash_action))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    state: ListState,
    actions: ListActions,
    onLabelSelection: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val count = state.selection.size
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = actions::clearSelection) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // The common actions as icons; the rest in a menu, so the title keeps its room at phone width.
        actions = {
            val action: @Composable (Int, Int, () -> Unit) -> Unit = { icon, label, onClick ->
                IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = stringResource(label)) }
            }
            var menu by remember { mutableStateOf(false) }
            val more: @Composable (List<Pair<Int, () -> Unit>>) -> Unit = { items ->
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        items.forEach { (label, onClick) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                menu = false
                                onClick()
                            })
                        }
                    }
                }
            }
            if (state.view == NoteView.Trash) {
                action(DesignR.drawable.ic_restore_from_trash, R.string.action_restore) { actions.restore(state.selection) }
                action(DesignR.drawable.ic_delete_forever, R.string.action_delete_forever, onDeleteForever)
                more(listOf(R.string.action_select_all to actions::selectAll))
            } else {
                val allPinned = state.notes.filter { it.id in state.selection }.all { it.pinned }
                if (state.view != NoteView.Archive) {
                    action(
                        if (allPinned) DesignR.drawable.ic_push_pin_filled else DesignR.drawable.ic_push_pin,
                        if (allPinned) R.string.action_unpin else R.string.action_pin,
                        actions::pinSelection,
                    )
                }
                action(
                    if (state.view == NoteView.Archive) DesignR.drawable.ic_unarchive else DesignR.drawable.ic_archive,
                    if (state.view == NoteView.Archive) R.string.action_unarchive else R.string.action_archive,
                    actions::archiveSelection,
                )
                action(DesignR.drawable.ic_delete, R.string.action_delete, actions::trashSelection)
                more(
                    listOf(
                        R.string.action_select_all to actions::selectAll,
                        R.string.action_labels to onLabelSelection,
                        R.string.action_share to actions::shareSelection,
                    ),
                )
            }
        },
    )
}

@Composable
private fun NotesDrawer(
    state: ListState,
    onSelect: (NoteView) -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            val entry: @Composable (NoteView, String, Int) -> Unit = { view, label, icon ->
                NavigationDrawerItem(
                    label = { Text(label) },
                    icon = { Icon(painterResource(icon), contentDescription = null) },
                    selected = state.view == view,
                    onClick = { onSelect(view) },
                )
            }
            entry(NoteView.Notes, stringResource(R.string.view_notes), DesignR.drawable.ic_lightbulb)
            if (state.labels.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.labels),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                state.labels.forEach { entry(NoteView.Label(it), it, DesignR.drawable.ic_label) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            entry(NoteView.Archive, stringResource(R.string.view_archive), DesignR.drawable.ic_archive)
            entry(NoteView.Trash, stringResource(R.string.view_trash), DesignR.drawable.ic_delete)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings)) },
                icon = { Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = null) },
                selected = false,
                onClick = onSettings,
            )
        }
    }
}

/**
 * Adds or removes labels on [notes]. A label on some but not all of the notes starts in an
 * indeterminate state and is left alone unless the user changes it.
 */
@Composable
private fun LabelDialog(
    allLabels: List<String>,
    notes: List<Note>,
    onApply: (add: Set<String>, remove: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var added by remember { mutableStateOf(emptySet<String>()) }
    var removed by remember { mutableStateOf(emptySet<String>()) }
    var created by remember { mutableStateOf(emptyList<String>()) }
    var newLabel by remember { mutableStateOf("") }
    val labels = (allLabels + created).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_labels)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEach { label ->
                    val onAll = notes.isNotEmpty() && notes.all { label in it.labels }
                    val checked = label in added || (onAll && label !in removed)
                    Surface(
                        onClick = {
                            if (checked) {
                                added = added - label
                                removed = removed + label
                            } else {
                                added = added + label
                                removed = removed - label
                            }
                        },
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it.replace("\n", "") },
                        label = { Text(stringResource(R.string.label_new_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            val name = newLabel.trim()
                            if (name.isNotEmpty()) {
                                created = created + name
                                added = added + name
                                newLabel = ""
                            }
                        },
                        enabled = newLabel.isNotBlank(),
                    ) { Icon(painterResource(DesignR.drawable.ic_new_label), contentDescription = stringResource(R.string.action_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(added, removed) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
