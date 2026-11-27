package io.github.michaelmassoni.tessera.feeds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraTextField
import io.github.michaelmassoni.tessera.feeds.R
import io.github.michaelmassoni.tessera.feeds.data.Adding
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Folders and their feeds: add, rename, move, notify, unsubscribe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeeds(
    state: FeedsState,
    actions: FeedsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.server_unreachable)
    var adding by rememberSaveable { mutableStateOf(false) }
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var renamingFeed by remember { mutableStateOf<Feed?>(null) }
    var movingFeed by remember { mutableStateOf<Feed?>(null) }
    var removingFeed by remember { mutableStateOf<Feed?>(null) }
    var renamingFolder by remember { mutableStateOf<Folder?>(null) }
    var removingFolder by remember { mutableStateOf<Folder?>(null) }

    fun run(block: suspend () -> Boolean) {
        scope.launch { if (!block()) snackbar.showSnackbar(failed) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_feeds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { newFolder = true }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_create_new_folder),
                            contentDescription = stringResource(R.string.new_folder),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.add_feed)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val folderIds = data.folders.map { it.id }.toSet()
        val loose = data.feeds.filter { it.folderId == null || it.folderId !in folderIds }.sortedBy { it.title.lowercase() }
        if (data.feeds.isEmpty() && data.folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_no_feeds),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            data.folders.sortedBy { it.title.lowercase() }.forEach { folder ->
                item(key = "folder-${folder.id}") {
                    FolderHeader(folder, onRename = { renamingFolder = folder }, onDelete = { removingFolder = folder })
                }
                val inside = data.feeds.filter { it.folderId == folder.id }.sortedBy { it.title.lowercase() }
                items(inside, key = { it.id }) { feed ->
                    FeedRow(
                        feed,
                        state.icons[feed.id],
                        onRename = { renamingFeed = feed },
                        onMove = { movingFeed = feed },
                        onNotify = { actions.setNotify(feed.id, !feed.notify) },
                        onRemove = { removingFeed = feed },
                    )
                }
            }
            if (loose.isNotEmpty() && data.folders.isNotEmpty()) {
                item(key = "loose") {
                    Text(
                        stringResource(R.string.no_folder),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
            }
            items(loose, key = { it.id }) { feed ->
                FeedRow(
                    feed,
                    state.icons[feed.id],
                    onRename = { renamingFeed = feed },
                    onMove = { movingFeed = feed },
                    onNotify = { actions.setNotify(feed.id, !feed.notify) },
                    onRemove = { removingFeed = feed },
                )
            }
        }
    }

    if (adding) AddFeedDialog(data.folders, actions, onDismiss = { adding = false })
    if (newFolder) {
        TextDialog(stringResource(R.string.new_folder), "", stringResource(R.string.create), onDismiss = { newFolder = false }) { name ->
            newFolder = false
            run { actions.newFolder(name) != null }
        }
    }
    renamingFeed?.let { feed ->
        TextDialog(
            stringResource(R.string.rename),
            feed.title,
            stringResource(R.string.save),
            onDismiss = { renamingFeed = null },
        ) { name ->
            renamingFeed = null
            run { actions.renameFeed(feed, name) }
        }
    }
    renamingFolder?.let { folder ->
        TextDialog(
            stringResource(R.string.rename),
            folder.title,
            stringResource(R.string.save),
            onDismiss = { renamingFolder = null },
        ) { name ->
            renamingFolder = null
            run { actions.renameFolder(folder, name) }
        }
    }
    movingFeed?.let { feed ->
        FolderDialog(data.folders, feed.folderId, onDismiss = { movingFeed = null }) { folderId ->
            movingFeed = null
            run { actions.moveFeed(feed, folderId) }
        }
    }
    removingFeed?.let { feed ->
        ConfirmDialog(
            stringResource(R.string.unsubscribe_title, feed.title),
            stringResource(R.string.unsubscribe_text),
            stringResource(R.string.unsubscribe),
            onDismiss = { removingFeed = null },
        ) {
            removingFeed = null
            run { actions.unsubscribe(feed) }
        }
    }
    removingFolder?.let { folder ->
        ConfirmDialog(
            stringResource(R.string.delete_folder_title, folder.title),
            stringResource(R.string.delete_folder_text),
            stringResource(R.string.delete),
            onDismiss = { removingFolder = null },
        ) {
            removingFolder = null
            run { actions.deleteFolder(folder) }
        }
    }
}

@Composable
private fun FolderHeader(
    folder: Folder,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            folder.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    painterResource(DesignR.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.folder_options, folder.title),
                )
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}

@Composable
private fun FeedRow(
    feed: Feed,
    icon: String?,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onNotify: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { FeedIcon(feed.title, feed.id, icon, size = 40.dp) },
        supportingContent = {
            if (feed.error != null) {
                Text(
                    stringResource(R.string.feed_error, feed.error),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(feed.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (feed.notify) {
                    Icon(
                        painterResource(DesignR.drawable.ic_notifications),
                        contentDescription = stringResource(R.string.notify_on),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.feed_options, feed.title),
                        )
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                            menu = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = {
                            menu = false
                            onMove()
                        })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (feed.notify) R.string.notify_turn_off else R.string.notify_turn_on)) },
                            onClick = {
                                menu = false
                                onNotify()
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.unsubscribe)) }, onClick = {
                            menu = false
                            onRemove()
                        })
                    }
                }
            }
        },
    ) { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Paste an address, pick a folder, Add; then pick among several feeds if the site offers them. */
@Composable
private fun AddFeedDialog(
    folders: List<Folder>,
    actions: FeedsActions,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var choices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf(false) }

    fun add(url: String) {
        if (working || url.isBlank()) return
        working = true
        problem = null
        scope.launch {
            when (val r = actions.addFeed(url, folderId)) {
                is Adding.Added -> {
                    onDismiss()
                }

                is Adding.Choose -> {
                    choices = r.choices
                    chosen = r.choices.firstOrNull()?.first
                }

                Adding.NotFound -> {
                    problem = R.string.add_not_found
                }

                Adding.Unreachable -> {
                    problem = R.string.add_unreachable
                }
            }
            working = false
        }
    }

    val list = choices
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(if (list == null) R.string.add_feed else R.string.choose_feed)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (list == null) {
                    TesseraTextField(
                        address,
                        { address = it },
                        label = { Text(stringResource(R.string.feed_address)) },
                        placeholder = { Text(stringResource(R.string.feed_address_hint)) },
                        singleLine = true,
                        enabled = !working,
                        isError = problem != null,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                            ),
                        keyboardActions = KeyboardActions(onDone = { add(address) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (folders.isNotEmpty()) {
                        Box {
                            TextButton(onClick = { folderMenu = true }, enabled = !working) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_folder),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    folders.firstOrNull { it.id == folderId }?.title ?: stringResource(R.string.no_folder),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            DropdownMenu(folderMenu, onDismissRequest = { folderMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.no_folder)) }, onClick = {
                                    folderId = null
                                    folderMenu = false
                                })
                                folders.forEach { f ->
                                    DropdownMenuItem(text = { Text(f.title) }, onClick = {
                                        folderId = f.id
                                        folderMenu = false
                                    })
                                }
                            }
                        }
                    }
                } else {
                    list.forEach { (url, title) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(chosen == url, role = Role.RadioButton) { chosen = url },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = chosen == url, onClick = null, modifier = Modifier.size(48.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                problem?.let {
                    Text(
                        stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { add(if (list == null) address else chosen.orEmpty()) },
                enabled = !working && (if (list == null) address.isNotBlank() else chosen != null),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderDialog(
    folders: List<Folder>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf<Folder?>(null) + folders.sortedBy { it.title.lowercase() }).forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(current == f?.id, role = Role.RadioButton) { onPick(f?.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == f?.id, onClick = null, modifier = Modifier.size(48.dp))
                        Text(f?.title ?: stringResource(R.string.no_folder))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TesseraTextField(
                text,
                { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
