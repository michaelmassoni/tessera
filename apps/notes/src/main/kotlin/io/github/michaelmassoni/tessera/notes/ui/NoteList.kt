package io.github.michaelmassoni.tessera.notes.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The staggered grid of note cards, with "Pinned" and "Others" headings when both exist. */
@Composable
fun NoteGrid(
    notes: List<Note>,
    selection: Set<String>,
    openId: String?,
    onOpen: (Note) -> Unit,
    onToggleSelect: (Note) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    groupPinned: Boolean = true,
) {
    val pinned = if (groupPinned) notes.filter { it.pinned } else emptyList()
    val others = if (groupPinned) notes.filterNot { it.pinned } else notes
    val selecting = selection.isNotEmpty()
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(170.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        fun section(
            title: Int?,
            list: List<Note>,
        ) {
            if (list.isEmpty()) return
            if (title != null) {
                item(span = StaggeredGridItemSpan.FullLine, key = "h$title", contentType = "header") {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            }
            items(list, key = { it.id }, contentType = { "note" }) { note ->
                NoteCard(
                    note = note,
                    selected = note.id in selection || note.id == openId,
                    onClick = { if (selecting) onToggleSelect(note) else onOpen(note) },
                    onLongClick = { onToggleSelect(note) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        val headings = pinned.isNotEmpty()
        section(if (headings) R.string.pinned else null, pinned)
        section(if (headings) R.string.others else null, others)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = cardDescription(note)
    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    this.selected = selected
                },
        border =
            if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.action_select))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (note.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val items = note.checklist
            if (items != null) {
                ChecklistPreview(items.map { it.text to it.checked })
            } else if (note.body.isNotBlank()) {
                Text(
                    note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.labels.isNotEmpty()) {
                Text(
                    note.labels.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChecklistPreview(
    items: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier,
) {
    val unchecked = items.filter { !it.second && it.first.isNotBlank() }
    val checked = items.count { it.second }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        unchecked.take(6).forEach { (text, _) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(DesignR.drawable.ic_check_box_outline_blank),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (unchecked.size > 6) {
            Text("…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (checked > 0) {
            Text(
                androidx.compose.ui.res
                    .pluralStringResource(R.plurals.checked_items, checked, checked),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun cardDescription(note: Note): String {
    val pinned = if (note.pinned) stringResource(R.string.pinned_state) else null
    return listOfNotNull(
        note.plainText().take(300).ifBlank { null },
        pinned,
        note.labels.joinToString().ifBlank { null },
    ).joinToString(", ")
}

/** A centred icon and message for an empty list. */
@Composable
fun EmptyState(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
