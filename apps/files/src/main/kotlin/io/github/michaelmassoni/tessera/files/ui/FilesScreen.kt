package io.github.michaelmassoni.tessera.files.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.files.ClipMode
import io.github.michaelmassoni.tessera.files.FilesEvent
import io.github.michaelmassoni.tessera.files.FilesUiState
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.TrashState
import io.github.michaelmassoni.tessera.files.data.ViewOptions
import io.github.michaelmassoni.tessera.files.data.Volume
import io.github.michaelmassoni.tessera.files.engine.FileEntry
import io.github.michaelmassoni.tessera.files.engine.FolderKeys
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Search
import io.github.michaelmassoni.tessera.files.engine.SortField
import io.github.michaelmassoni.tessera.files.engine.TrashedItem
import kotlinx.coroutines.flow.Flow
import java.io.File
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Everything the screen can ask for; implemented by the ViewModel plus the activity. */
interface FilesActions {
    fun open(dir: File?)

    fun back(): Boolean

    fun refresh()

    fun openFile(file: File)

    fun share(files: List<File>)

    fun setOptions(options: ViewOptions)

    fun toggleSelection(file: File)

    fun selectAll()

    fun clearSelection()

    fun copySelection()

    fun moveSelection()

    fun paste()

    fun cancelClipboard()

    fun cancelOperation()

    fun delete(files: Collection<File>)

    fun openTrash()

    fun toggleTrashSelection(id: String)

    fun selectAllTrash()

    fun restore(ids: Collection<String>)

    /** Deletes items in the trash for good; null empties it. */
    fun deleteForever(ids: Collection<String>?)

    fun rename(
        file: File,
        name: String,
    ): NameProblem?

    fun createFolder(name: String): NameProblem?

    fun openSearch()

    fun search(query: String)

    fun closeSearch()

    fun openSettings()
}

private val ExpandedWidth = 840.dp

// Saved-state keys of what the main pane shows.
private const val HOME_KEY = "home"
private const val SEARCH_KEY = "search"

private fun folderKey(path: String) = "folder:$path"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: FilesUiState,
    events: Flow<FilesEvent>,
    actions: FilesActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(events, resources) {
        events.collect { e ->
            val message =
                when (e) {
                    is FilesEvent.Done -> {
                        resources.getQuantityString(if (e.mode == ClipMode.Copy) R.plurals.copied else R.plurals.moved, e.count, e.count)
                    }

                    is FilesEvent.Deleted -> {
                        resources.getQuantityString(R.plurals.deleted, e.count, e.count)
                    }

                    is FilesEvent.Trashed -> {
                        val result =
                            snackbar.showSnackbar(
                                resources.getQuantityString(R.plurals.trashed, e.ids.size, e.ids.size),
                                actionLabel = resources.getString(R.string.action_undo),
                            )
                        if (result == SnackbarResult.ActionPerformed) actions.restore(e.ids)
                        null
                    }

                    is FilesEvent.Restored -> {
                        resources.getQuantityString(R.plurals.restored, e.count, e.count)
                    }

                    is FilesEvent.Failed -> {
                        resources.getString(
                            when (e.reason) {
                                FilesEvent.Reason.IntoItself -> R.string.error_into_itself
                                FilesEvent.Reason.NoSpace -> R.string.error_no_space
                                FilesEvent.Reason.Io -> R.string.error_io
                                FilesEvent.Reason.Cancelled -> R.string.error_cancelled
                                FilesEvent.Reason.NoApp -> R.string.error_no_app
                            },
                        )
                    }
                }
            if (message != null) snackbar.showSnackbar(message)
        }
    }
    BackHandler(enabled = state.location != null || state.selection.isNotEmpty() || state.search != null || state.trash != null) {
        actions.back()
    }
    // Home, each folder you're inside and the search results keep their scroll position while
    // something else is shown (a subfolder, Trash), so going back up lands where you were.
    val saveable = rememberSaveableStateHolder()
    var kept by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val location = state.location?.path
    val searching = state.search != null
    LaunchedEffect(location) {
        val stale = FolderKeys.stale(kept, location)
        stale.forEach { saveable.removeState(folderKey(it)) }
        kept = kept - stale.toSet() + listOfNotNull(location).filter { it !in kept }
    }
    LaunchedEffect(searching) { if (!searching) saveable.removeState(SEARCH_KEY) }
    state.trash?.let { trash ->
        TrashScreen(trash, actions, snackbar, modifier)
        return
    }

    var dialog by remember { mutableStateOf<FileDialog?>(null) }
    val selected = state.selection.toList()
    val entriesShown = state.search?.results ?: state.entries

    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                selected.isNotEmpty() -> {
                    SelectionBar(
                        count = selected.size,
                        onClose = actions::clearSelection,
                        onSelectAll = actions::selectAll,
                        onShare = { actions.share(selected.filter { it.isFile }) },
                        onCopy = actions::copySelection,
                        onMove = actions::moveSelection,
                        onDelete = { dialog = FileDialog.Delete(selected) },
                        onRename = selected.singleOrNull()?.let { f -> { dialog = FileDialog.Rename(f) } },
                        onDetails = selected.singleOrNull()?.let { f -> { dialog = FileDialog.Details(f) } },
                        shareEnabled = selected.any { it.isFile },
                    )
                }

                state.search != null -> {
                    SearchBarTop(state.search.query, actions::search, actions::closeSearch)
                }

                else -> {
                    BrowserTopBar(state, actions, onNewFolder = { dialog = FileDialog.NewFolder })
                }
            }
        },
        bottomBar = {
            val clip = state.clipboard
            if (clip != null) {
                BottomAppBar {
                    Text(
                        pluralStringResource(
                            if (clip.mode ==
                                ClipMode.Copy
                            ) {
                                R.plurals.clip_copy
                            } else {
                                R.plurals.clip_move
                            },
                            clip.files.size,
                            clip.files.size,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    TextButton(onClick = actions::cancelClipboard) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = actions::paste, enabled = state.location != null, modifier = Modifier.padding(end = 12.dp)) {
                        Text(stringResource(R.string.action_paste_here))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val width = maxWidth
            val wide = width >= ExpandedWidth
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    HomeContent(
                        state.volumes,
                        actions::open,
                        actions::openTrash,
                        compact = true,
                        modifier = Modifier.width(320.dp).fillMaxHeight(),
                    )
                    VerticalDivider()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val shown =
                        when {
                            searching -> SEARCH_KEY
                            location == null -> HOME_KEY
                            else -> folderKey(location)
                        }
                    saveable.SaveableStateProvider(shown) {
                        when {
                            state.location == null && state.search == null && !wide -> {
                                HomeContent(
                                    state.volumes,
                                    actions::open,
                                    actions::openTrash,
                                    compact = false,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            state.location == null && state.search == null -> {
                                Empty(stringResource(R.string.pick_a_location))
                            }

                            else -> {
                                BrowserContent(
                                    state = state,
                                    entries = entriesShown,
                                    columnsHint = width,
                                    onItemClick = { entry ->
                                        when {
                                            state.selection.isNotEmpty() -> actions.toggleSelection(entry.file)
                                            entry.isDirectory -> actions.open(entry.file)
                                            else -> actions.openFile(entry.file)
                                        }
                                    },
                                    onItemLongClick = { actions.toggleSelection(it.file) },
                                    onRefresh = actions::refresh,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.operation?.let { OperationDialog(it, actions::cancelOperation) }
    when (val d = dialog) {
        is FileDialog.Delete -> {
            ConfirmDeleteDialog(d.files.size, onConfirm = {
                actions.delete(d.files)
                dialog = null
            }, onDismiss = { dialog = null })
        }

        is FileDialog.Rename -> {
            NameDialog(
                title = stringResource(R.string.action_rename),
                initial = d.file.name,
                selectBaseName = !d.file.isDirectory,
                onConfirm = { name -> actions.rename(d.file, name).also { if (it == null) dialog = null } },
                onDismiss = { dialog = null },
            )
        }

        FileDialog.NewFolder -> {
            NameDialog(
                title = stringResource(R.string.action_new_folder),
                initial = "",
                selectBaseName = false,
                onConfirm = { name -> actions.createFolder(name).also { if (it == null) dialog = null } },
                onDismiss = { dialog = null },
            )
        }

        is FileDialog.Details -> {
            DetailsDialog(d.file, onDismiss = { dialog = null })
        }

        null -> {}
    }
}

private sealed interface FileDialog {
    data class Delete(
        val files: List<File>,
    ) : FileDialog

    data class Rename(
        val file: File,
    ) : FileDialog

    data object NewFolder : FileDialog

    data class Details(
        val file: File,
    ) : FileDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    state: FilesUiState,
    actions: FilesActions,
    onNewFolder: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val location = state.location
    Column {
        TopAppBar(
            title = {
                Text(
                    location?.let { loc -> state.volumes.firstOrNull { it.root == loc }?.description ?: loc.name }
                        ?: stringResource(R.string.app_name),
                    maxLines = 1,
                )
            },
            navigationIcon = {
                if (location != null) {
                    IconButton(onClick = { actions.back() }) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_up))
                    }
                }
            },
            actions = {
                IconButton(onClick = actions::openSearch) {
                    Icon(painterResource(DesignR.drawable.ic_search), contentDescription = stringResource(R.string.action_search))
                }
                if (location != null) {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(painterResource(DesignR.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
                        }
                        SortMenu(sortMenu, state.options, actions::setOptions) { sortMenu = false }
                    }
                    IconButton(onClick = { actions.setOptions(state.options.copy(grid = !state.options.grid)) }) {
                        Icon(
                            painterResource(if (state.options.grid) DesignR.drawable.ic_view_list else DesignR.drawable.ic_grid_view),
                            contentDescription = stringResource(if (state.options.grid) R.string.view_list else R.string.view_grid),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                        if (location != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_new_folder)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_create_new_folder), null) },
                                onClick = {
                                    moreMenu = false
                                    onNewFolder()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (state.options.showHidden) R.string.hide_hidden else R.string.show_hidden))
                                },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_visibility), null) },
                                onClick = {
                                    moreMenu = false
                                    actions.setOptions(state.options.copy(showHidden = !state.options.showHidden))
                                },
                            )
                        }
                        MenuItem(stringResource(R.string.settings), DesignR.drawable.ic_settings) {
                            moreMenu = false
                            actions.openSettings()
                        }
                    }
                }
            },
        )
        if (location != null) {
            val volume = state.volumes.filter { location.path.startsWith(it.root.path) }.maxByOrNull { it.root.path.length }
            if (volume != null) Breadcrumbs(volume.description, volume.root, location, actions::open)
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    options: ViewOptions,
    onChange: (ViewOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        listOf(
            SortField.Name to R.string.sort_name,
            SortField.Modified to R.string.sort_modified,
            SortField.Size to R.string.sort_size,
            SortField.Type to R.string.sort_type,
        ).forEach { (field, label) ->
            val current = options.sort.field == field
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                trailingIcon = {
                    if (current) {
                        Icon(
                            painterResource(
                                if (options.sort.ascending) DesignR.drawable.ic_arrow_upward else DesignR.drawable.ic_arrow_downward,
                            ),
                            contentDescription =
                                stringResource(
                                    if (options.sort.ascending) R.string.sort_ascending else R.string.sort_descending,
                                ),
                        )
                    }
                },
                onClick = {
                    // Tapping the current field flips the direction.
                    onChange(
                        options.copy(sort = options.sort.copy(field = field, ascending = if (current) !options.sort.ascending else true)),
                    )
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: (() -> Unit)?,
    onDetails: (() -> Unit)?,
    shareEnabled: Boolean,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_selection))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected, count, count)) },
        actions = {
            IconButton(onClick = onShare, enabled = shareEnabled) {
                Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
            }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(more, onDismissRequest = { more = false }) {
                    val close = { more = false }
                    MenuItem(stringResource(R.string.action_copy), DesignR.drawable.ic_content_copy) {
                        close()
                        onCopy()
                    }
                    MenuItem(stringResource(R.string.action_move), DesignR.drawable.ic_drive_file_move) {
                        close()
                        onMove()
                    }
                    onRename?.let {
                        MenuItem(stringResource(R.string.action_rename), DesignR.drawable.ic_edit) {
                            close()
                            it()
                        }
                    }
                    onDetails?.let {
                        MenuItem(stringResource(R.string.action_details), DesignR.drawable.ic_info) {
                            close()
                            it()
                        }
                    }
                    MenuItem(stringResource(R.string.action_select_all), DesignR.drawable.ic_select_all) {
                        close()
                        onSelectAll()
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarTop(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowserContent(
    state: FilesUiState,
    entries: List<FileEntry>,
    columnsHint: androidx.compose.ui.unit.Dp,
    onItemClick: (FileEntry) -> Unit,
    onItemLongClick: (FileEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    val searching = state.search != null
    val location = state.location
    when {
        location != null && !searching && Search.isRestricted(location) -> {
            Empty(stringResource(R.string.restricted_folder))
            return
        }

        state.unreadable && !searching -> {
            Empty(stringResource(R.string.unreadable_folder))
            return
        }

        state.loading && entries.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return
        }

        entries.isEmpty() -> {
            Empty(
                stringResource(
                    when {
                        searching && state.search.running -> R.string.searching
                        searching && state.search.query.isNotBlank() -> R.string.no_results
                        searching -> R.string.search_hint
                        else -> R.string.empty_folder
                    },
                ),
            )
            return
        }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (state.options.grid && !searching) {
            val columns = if (columnsHint >= 600.dp) GridCells.Adaptive(150.dp) else GridCells.Fixed(3)
            LazyVerticalGrid(columns, contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.file.path }) { e ->
                    FileTile(e, e.file in state.selection, { onItemClick(e) }, { onItemLongClick(e) }, Modifier.padding(4.dp).animateItem())
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                if (searching && state.search.running) {
                    item { LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(8.dp)) }
                }
                items(entries, key = { it.file.path }) { e ->
                    FileRow(
                        e,
                        e.file in state.selection,
                        { onItemClick(e) },
                        { onItemLongClick(e) },
                        Modifier.animateItem(),
                        showPath = searching,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    volumes: List<Volume>,
    onOpen: (File) -> Unit,
    onTrash: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = volumes.firstOrNull { it.isPrimary }
    val folders =
        listOf(
            "Download" to (R.string.folder_downloads to DesignR.drawable.ic_download),
            "DCIM" to (R.string.folder_camera to DesignR.drawable.ic_photo_camera),
            "Pictures" to (R.string.folder_pictures to DesignR.drawable.ic_image),
            "Documents" to (R.string.folder_documents to DesignR.drawable.ic_description),
            "Music" to (R.string.folder_music to DesignR.drawable.ic_music_note),
            "Movies" to (R.string.folder_movies to DesignR.drawable.ic_movie),
        )
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (compact) 2 else 3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.storage), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        items(volumes, key = { it.root.path }, span = { GridItemSpan(maxLineSpan) }) { v ->
            Card(
                onClick = { onOpen(v.root) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(
                                when {
                                    v.isPrimary -> DesignR.drawable.ic_smartphone
                                    v.description.contains("USB", ignoreCase = true) -> DesignR.drawable.ic_usb
                                    else -> DesignR.drawable.ic_sd_card
                                },
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(v.description, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                    LinearWavyProgressIndicator(progress = { v.usedFraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(
                            R.string.storage_free_of,
                            Formatter.formatShortFileSize(context, v.freeBytes),
                            Formatter.formatShortFileSize(context, v.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (primary != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.folders),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(folders, key = { it.first }) { (dir, labelIcon) ->
                val (label, icon) = labelIcon
                Card(
                    onClick = { onOpen(File(primary.root, dir)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            item(key = "trash") {
                Card(
                    onClick = onTrash,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painterResource(DesignR.drawable.ic_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(stringResource(R.string.trash), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Name for [NameProblem] messages. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun NameProblem.message(): String =
    stringResource(
        when (this) {
            NameProblem.Empty -> R.string.name_empty
            NameProblem.Reserved -> R.string.name_reserved
            NameProblem.InvalidCharacter -> R.string.name_invalid
            NameProblem.TooLong -> R.string.name_too_long
            NameProblem.Exists -> R.string.name_exists
        },
    )

// ------------------------------------------------------------------------------- previews

private object PreviewActions : FilesActions {
    override fun openSettings() = Unit

    override fun open(dir: File?) = Unit

    override fun back() = true

    override fun refresh() = Unit

    override fun openFile(file: File) = Unit

    override fun share(files: List<File>) = Unit

    override fun setOptions(options: ViewOptions) = Unit

    override fun toggleSelection(file: File) = Unit

    override fun selectAll() = Unit

    override fun clearSelection() = Unit

    override fun copySelection() = Unit

    override fun moveSelection() = Unit

    override fun paste() = Unit

    override fun cancelClipboard() = Unit

    override fun cancelOperation() = Unit

    override fun delete(files: Collection<File>) = Unit

    override fun openTrash() = Unit

    override fun toggleTrashSelection(id: String) = Unit

    override fun selectAllTrash() = Unit

    override fun restore(ids: Collection<String>) = Unit

    override fun deleteForever(ids: Collection<String>?) = Unit

    override fun rename(
        file: File,
        name: String,
    ): NameProblem? = null

    override fun createFolder(name: String): NameProblem? = null

    override fun openSearch() = Unit

    override fun search(query: String) = Unit

    override fun closeSearch() = Unit
}

private val previewRoot = File("/storage/emulated/0")
private val previewVolumes =
    listOf(
        Volume(
            previewRoot,
            "Internal storage",
            isPrimary = true,
            isRemovable = false,
            totalBytes = 128_000_000_000,
            freeBytes = 41_500_000_000,
        ),
        Volume(
            File("/storage/1A2B-3C4D"),
            "SD card",
            isPrimary = false,
            isRemovable = true,
            totalBytes = 64_000_000_000,
            freeBytes = 50_000_000_000,
        ),
    )
private val day = 86_400_000L
private val previewEntries =
    listOf(
        FileEntry(File(previewRoot, "Download/Receipts"), true, 0, 1_763_400_000_000, 12),
        FileEntry(File(previewRoot, "Download/Tax return 2025.pdf"), false, 1_240_000, 1_763_400_000_000 - day, null),
        FileEntry(File(previewRoot, "Download/holiday-video.mp4"), false, 734_000_000, 1_763_400_000_000 - 3 * day, null),
        FileEntry(File(previewRoot, "Download/podcast.opus"), false, 41_000_000, 1_763_400_000_000 - 9 * day, null),
        FileEntry(File(previewRoot, "Download/backup.zip"), false, 2_300_000_000, 1_763_400_000_000 - 30 * day, null),
        FileEntry(File(previewRoot, "Download/notes.md"), false, 4_200, 1_763_400_000_000 - 31 * day, null),
    )

@io.github.michaelmassoni.tessera.core.design.TesseraPreviews
@Composable
private fun HomePreview() {
    io.github.michaelmassoni.tessera.core.design.TesseraTheme(dynamicColor = false) {
        FilesScreen(FilesUiState(volumes = previewVolumes), kotlinx.coroutines.flow.emptyFlow(), PreviewActions)
    }
}

@io.github.michaelmassoni.tessera.core.design.TesseraPreviews
@Composable
private fun BrowserPreview() {
    io.github.michaelmassoni.tessera.core.design.TesseraTheme(dynamicColor = false) {
        FilesScreen(
            FilesUiState(location = File(previewRoot, "Download"), entries = previewEntries, volumes = previewVolumes),
            kotlinx.coroutines.flow.emptyFlow(),
            PreviewActions,
        )
    }
}

@io.github.michaelmassoni.tessera.core.design.TesseraPreviews
@Composable
private fun SelectionPreview() {
    io.github.michaelmassoni.tessera.core.design.TesseraTheme(dynamicColor = false) {
        FilesScreen(
            FilesUiState(
                location = File(previewRoot, "Download"),
                entries = previewEntries,
                volumes = previewVolumes,
                selection = setOf(previewEntries[1].file, previewEntries[2].file),
                options = ViewOptions(grid = true),
            ),
            kotlinx.coroutines.flow.emptyFlow(),
            PreviewActions,
        )
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(painterResource(icon), contentDescription = null) }, onClick = onClick)
}

// The trash with one item selected, in both themes.
@io.github.michaelmassoni.tessera.core.design.TesseraPreviews
@Composable
private fun TrashPreview() {
    val day = 86_400_000L
    val now = 100 * day
    val items =
        listOf(
            TrashedItem("1", "/storage/emulated/0/Download/Invoice.pdf", "", now - day, isDirectory = false, size = 182_000),
            TrashedItem("2", "/storage/emulated/0/DCIM/Holiday", "", now - 12 * day, isDirectory = true, size = 48_000_000),
            TrashedItem("3", "/storage/emulated/0/Music/Demo.mp3", "", now - 29 * day, isDirectory = false, size = 4_200_000),
        )
    io.github.michaelmassoni.tessera.core.design.TesseraTheme(dynamicColor = false) {
        TrashScreen(TrashState(items, selection = setOf("2")), PreviewActions, remember { SnackbarHostState() }, now = now)
    }
}
