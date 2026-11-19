package io.github.michaelmassoni.tessera.messages.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What a conversation needs to be shown or acted on: its title and whether it's a group. */
internal fun Conversation.title(
    names: Map<String, String>,
    unknown: String,
) = addresses.joinToString { names[it] ?: it }.ifBlank { unknown }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationList(
    state: MessagesState,
    open: Long?,
    actions: MessagesActions,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var dialog by rememberSaveable { mutableStateOf<ListDialog?>(null) }
    val unknown = stringResource(R.string.unknown)
    val context = LocalContext.current
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(selected.isEmpty() && showArchived) { showArchived = false }

    val shown =
        state.conversations
            .filter { (it.threadId in state.archived) == showArchived }
            .filter { c ->
                query.isBlank() || c.snippet.contains(query, true) ||
                    c.addresses.any { a -> a.contains(query) || state.names[a]?.contains(query, true) == true }
            }.sortedByDescending { it.threadId in state.pinned }
    // Drop selections that disappeared (deleted, or archived out of this view).
    val live = selected.filter { id -> shown.any { it.threadId == id } }.toSet()
    if (live != selected) selected = live

    /** Runs [action] if Android allows it, or explains that it needs the default SMS app. */
    fun needsDefault(action: () -> Unit) = if (state.isDefault) action() else dialog = ListDialog.NeedsDefault

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selected.isNotEmpty()) {
                val chosen = shown.filter { it.threadId in selected }
                val allPinned = chosen.all { it.threadId in state.pinned }
                TopAppBar(
                    title = { Text(selected.size.toString()) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    navigationIcon = {
                        IconButton(onClick = {
                            selected = emptySet()
                        }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_close),
                                contentDescription = stringResource(R.string.clear_selection),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            actions.pin(selected, !allPinned)
                            selected = emptySet()
                        }) {
                            Icon(
                                painterResource(if (allPinned) DesignR.drawable.ic_push_pin_filled else DesignR.drawable.ic_push_pin),
                                contentDescription = stringResource(if (allPinned) R.string.unpin else R.string.pin),
                            )
                        }
                        IconButton(onClick = {
                            actions.archive(selected, !showArchived)
                            selected = emptySet()
                        }) {
                            Icon(
                                painterResource(if (showArchived) DesignR.drawable.ic_unarchive else DesignR.drawable.ic_archive),
                                contentDescription = stringResource(if (showArchived) R.string.unarchive else R.string.archive),
                            )
                        }
                        IconButton(onClick = {
                            needsDefault {
                                actions.markRead(selected)
                                selected = emptySet()
                            }
                        }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_mark_chat_read),
                                contentDescription = stringResource(R.string.mark_read),
                            )
                        }
                        if (chosen.size == 1 && chosen.single().addresses.size == 1) {
                            IconButton(onClick = { needsDefault { dialog = ListDialog.Block(chosen.single().addresses.single()) } }) {
                                Icon(painterResource(DesignR.drawable.ic_block), contentDescription = stringResource(R.string.block))
                            }
                        }
                        IconButton(onClick = { needsDefault { dialog = ListDialog.Delete } }) {
                            Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(if (showArchived) R.string.archived else R.string.app_name)) },
                    navigationIcon = {
                        if (showArchived) {
                            IconButton(onClick = {
                                showArchived = false
                            }) { Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
                        }
                    },
                    actions = {
                        if (!showArchived) {
                            IconButton(onClick = {
                                showArchived = true
                            }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_archive),
                                    contentDescription = stringResource(R.string.archived),
                                )
                            }
                        }
                        IconButton(onClick = actions::openSettings) {
                            Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selected.isEmpty() && !showArchived) {
                ExtendedFloatingActionButton(onClick = onNew, icon = {
                    Icon(painterResource(DesignR.drawable.ic_chat), contentDescription = null)
                }, text = { Text(stringResource(R.string.start_chat)) })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.hasPermissions -> {
                    Notice(stringResource(R.string.permissions_needed), stringResource(R.string.grant), actions::grant)
                }

                !state.isDefault && state.loaded -> {
                    Notice(
                        stringResource(R.string.default_note),
                        stringResource(R.string.set_default),
                        actions::makeDefault,
                    )
                }
            }
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state.loaded && shown.isEmpty()) {
                Text(
                    stringResource(if (showArchived) R.string.empty_archived else R.string.empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp)) {
                items(shown, key = { it.threadId }) { c ->
                    val isSelected = c.threadId in selected
                    ConversationRow(
                        c,
                        title = c.title(state.names, unknown),
                        time = DateUtils.getRelativeTimeSpanString(context, c.date, false).toString(),
                        pinned = c.threadId in state.pinned,
                        highlighted = isSelected || (selected.isEmpty() && c.threadId == open),
                        selected = isSelected,
                        onClick = {
                            if (selected.isEmpty()) {
                                actions.open(c)
                            } else {
                                selected =
                                    if (isSelected) selected - c.threadId else selected + c.threadId
                            }
                        },
                        onLongClick = { selected = if (isSelected) selected - c.threadId else selected + c.threadId },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        ListDialog.NeedsDefault -> {
            NeedsDefaultDialog(onDismiss = { dialog = null }, onMakeDefault = actions::makeDefault)
        }

        ListDialog.Delete -> {
            ConfirmDialog(
                title = pluralStringResource(R.plurals.delete_conversations, selected.size, selected.size),
                text = stringResource(R.string.delete_conversations_text),
                confirm = stringResource(R.string.delete),
                onDismiss = { dialog = null },
            ) {
                actions.deleteThreads(selected)
                selected = emptySet()
            }
        }

        is ListDialog.Block -> {
            ConfirmDialog(
                title = stringResource(R.string.block_title, state.names[d.address] ?: d.address),
                text = stringResource(R.string.block_text),
                confirm = stringResource(R.string.block),
                onDismiss = { dialog = null },
            ) {
                actions.block(d.address)
                selected = emptySet()
            }
        }

        null -> {}
    }
}

/** Dialogs from the conversation list, saveable across rotation. */
internal sealed interface ListDialog : java.io.Serializable {
    data object NeedsDefault : ListDialog {
        private fun readResolve(): Any = NeedsDefault
    }

    data object Delete : ListDialog {
        private fun readResolve(): Any = Delete
    }

    data class Block(
        val address: String,
    ) : ListDialog
}

@Composable
private fun ConversationRow(
    c: Conversation,
    title: String,
    time: String,
    pinned: Boolean,
    highlighted: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val weight = if (c.unread) FontWeight.Bold else null
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.select))
                .semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(title, group = c.addresses.size > 1)
                if (selected) {
                    Icon(
                        painterResource(DesignR.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(time, style = MaterialTheme.typography.labelSmall, fontWeight = weight)
                if (pinned) {
                    Icon(
                        painterResource(DesignR.drawable.ic_push_pin_filled),
                        contentDescription = stringResource(R.string.pinned),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp).size(16.dp),
                    )
                } else if (c.unread) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp).size(8.dp),
                    ) {}
                }
            }
        }
    }
}
