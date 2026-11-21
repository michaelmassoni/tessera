package io.github.michaelmassoni.tessera.files.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.TrashState
import io.github.michaelmassoni.tessera.files.engine.FileType
import io.github.michaelmassoni.tessera.files.engine.Trash
import io.github.michaelmassoni.tessera.files.engine.TrashedItem
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Deleted files, newest first. Tap to select; Restore puts them back, Delete removes them for
 * good, and Empty trash removes everything. Items go by themselves after 30 days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    state: TrashState,
    actions: FilesActions,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    var confirm by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = empty, false = selected
    val selection = state.selection
    Scaffold(
        modifier = modifier,
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.trash)) },
                    navigationIcon = {
                        IconButton(onClick = { actions.back() }) {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_up))
                        }
                    },
                    actions = {
                        TextButton(onClick = { confirm = true }, enabled = state.items.isNotEmpty()) {
                            Text(stringResource(R.string.action_empty_trash))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected, selection.size, selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = { actions.back() }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_clear_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = actions::selectAllTrash) {
                            Icon(
                                painterResource(DesignR.drawable.ic_select_all),
                                contentDescription = stringResource(R.string.action_select_all),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selection.isNotEmpty()) {
                BottomAppBar {
                    Box(Modifier.weight(1f))
                    OutlinedButton(onClick = { confirm = false }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.action_delete))
                    }
                    Button(onClick = { actions.restore(selection) }, modifier = Modifier.padding(end = 12.dp)) {
                        Text(stringResource(R.string.action_restore))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    pluralStringResource(R.plurals.trash_empty, Trash.KEEP_DAYS, Trash.KEEP_DAYS),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item(key = "note") {
                    Text(
                        pluralStringResource(R.plurals.trash_note, Trash.KEEP_DAYS, Trash.KEEP_DAYS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(
                    state.items,
                    key = { it.id },
                ) { item -> TrashRow(item, item.id in selection, now) { actions.toggleTrashSelection(item.id) } }
            }
        }
    }
    confirm?.let { all ->
        val count = if (all) state.items.size else selection.size
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    if (all) {
                        stringResource(
                            R.string.empty_trash_title,
                        )
                    } else {
                        pluralStringResource(R.plurals.delete_forever_title, count, count)
                    },
                )
            },
            text = { Text(stringResource(R.string.delete_forever_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    actions.deleteForever(if (all) null else selection)
                }) { Text(stringResource(if (all) R.string.action_empty_trash else R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TrashRow(
    item: TrashedItem,
    selected: Boolean,
    now: Long,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val daysLeft = ((item.expiresAt() - now) / 86_400_000L).toInt().coerceAtLeast(0)
    val type = if (item.isDirectory) FileType.Folder else FileType.of(item.name)
    ListItem(
        selected = selected,
        onClick = onToggle,
        leadingContent = { Icon(painterResource(type.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        supportingContent = {
            Text(
                "${Formatter.formatShortFileSize(context, item.size)} · ${pluralStringResource(R.plurals.days_left, daysLeft, daysLeft)}",
                maxLines = 1,
            )
        },
        trailingContent = { Checkbox(checked = selected, onCheckedChange = null) },
    ) { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
