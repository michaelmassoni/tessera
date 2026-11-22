package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.QueueItem
import io.github.michaelmassoni.tessera.music.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A queue row with a key that stays with the song as it moves (a song may be queued twice). */
private data class Entry(
    val key: String,
    val item: QueueItem,
)

private fun entries(queue: List<QueueItem>): List<Entry> {
    val seen = mutableMapOf<String, Int>()
    return queue.map { item ->
        val n = seen.merge(item.id, 1, Int::plus) ?: 1
        Entry("${item.id}#$n", item)
    }
}

/**
 * The queue: the song playing highlighted, tap to play another, drag the handle to reorder, swipe
 * a song away to remove it. Moves happen on screen while dragging and reach the player on release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<QueueItem>,
    index: Int,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    var list by remember { mutableStateOf(entries(queue)) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    // The player's queue changed (a move landed, a song ended): show it, unless a drag is under way.
    LaunchedEffect(queue) { if (dragFrom < 0) list = entries(queue) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0))
    val reorder =
        rememberReorderableLazyListState(listState) { from, to ->
            list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val remove = stringResource(R.string.remove_from_queue)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.queue),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(list, key = { _, e -> e.key }) { i, entry ->
                ReorderableItem(reorder, key = entry.key) { dragging ->
                    val swipe = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = swipe,
                        enableDismissFromStartToEnd = false,
                        onDismiss = {
                            list = list.filterNot { it.key == entry.key }
                            actions.removeQueueItem(i)
                        },
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_delete),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        ListItem(
                            selected = i == index && dragFrom < 0,
                            onClick = {
                                actions.skipTo(i)
                                onDismiss()
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor =
                                        if (dragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                ),
                            leadingContent = { AlbumArt(entry.item.albumId, size = 40.dp) },
                            supportingContent = { Text(entry.item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                IconButton(
                                    onClick = {},
                                    modifier =
                                        Modifier.draggableHandle(
                                            onDragStarted = { dragFrom = i },
                                            onDragStopped = {
                                                val to = list.indexOfFirst { it.key == entry.key }
                                                val from = dragFrom
                                                dragFrom = -1
                                                if (from >= 0 && to >= 0 && from != to) actions.moveQueueItem(from, to)
                                            },
                                        ),
                                ) {
                                    Icon(
                                        painterResource(DesignR.drawable.ic_drag_indicator),
                                        contentDescription = stringResource(R.string.reorder),
                                    )
                                }
                            },
                            modifier =
                                Modifier.semantics {
                                    customActions =
                                        listOfNotNull(
                                            if (i > 0) {
                                                CustomAccessibilityAction(moveUp) {
                                                    actions.moveQueueItem(i, i - 1)
                                                    true
                                                }
                                            } else {
                                                null
                                            },
                                            if (i < list.lastIndex) {
                                                CustomAccessibilityAction(moveDown) {
                                                    actions.moveQueueItem(i, i + 1)
                                                    true
                                                }
                                            } else {
                                                null
                                            },
                                            CustomAccessibilityAction(remove) {
                                                actions.removeQueueItem(i)
                                                true
                                            },
                                        )
                                },
                        ) { Text(entry.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}
