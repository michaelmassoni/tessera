package io.github.michaelmassoni.tessera.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.gallery.R
import io.github.michaelmassoni.tessera.gallery.data.Thumbnail
import io.github.michaelmassoni.tessera.gallery.engine.DaySection
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.engine.formatDuration
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A grid of media, either grouped by day ([sections]) or flat ([items]). */
@Composable
fun MediaGrid(
    sections: List<DaySection>?,
    items: List<MediaItem>,
    selection: Set<Long>,
    onOpen: (MediaItem) -> Unit,
    onToggle: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onToggleDay: ((DaySection) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    caption: ((MediaItem) -> String?)? = null,
) {
    val selecting = selection.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (sections != null) {
            sections.forEach { section ->
                day(section, selection, onToggleDay)
                cells(section.items, selection, selecting, onOpen, onToggle, caption)
            }
        } else {
            cells(items, selection, selecting, onOpen, onToggle, caption)
        }
    }
}

private fun LazyGridScope.day(
    section: DaySection,
    selection: Set<Long>,
    onToggleDay: ((DaySection) -> Unit)?,
) {
    item(key = "d${section.day}", span = { GridItemSpan(maxLineSpan) }, contentType = "day") {
        val all = section.items.all { it.id in selection }
        val base =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
                .semantics { heading() }
        Row(
            if (onToggleDay != null) base.toggleable(value = all, role = Role.Checkbox, onValueChange = { onToggleDay(section) }) else base,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selection.isNotEmpty() && onToggleDay != null) {
                Icon(
                    painterResource(if (all) DesignR.drawable.ic_check_circle else DesignR.drawable.ic_radio_button_unchecked),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun LazyGridScope.cells(
    items: List<MediaItem>,
    selection: Set<Long>,
    selecting: Boolean,
    onOpen: (MediaItem) -> Unit,
    onToggle: (MediaItem) -> Unit,
    caption: ((MediaItem) -> String?)?,
) {
    items(items, key = { it.id }, contentType = { "media" }) { item ->
        MediaCell(
            item = item,
            selected = item.id in selection,
            selecting = selecting,
            onClick = { if (selecting) onToggle(item) else onOpen(item) },
            onLongClick = { onToggle(item) },
            caption = caption?.invoke(item),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    caption: String?,
) {
    val date =
        android.text.format.DateUtils
            .formatDateTime(LocalContext.current, item.date, android.text.format.DateUtils.FORMAT_SHOW_DATE)
    val description =
        if (item.isVideo) {
            stringResource(
                R.string.cd_video,
                formatDuration(item.durationMillis),
                date,
            )
        } else {
            stringResource(R.string.cd_photo, date)
        }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            },
    ) {
        val inset = if (selected) Modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)) else Modifier
        AsyncImage(
            model = Thumbnail(item.uri, item.modified),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(inset),
        )
        if (item.isVideo) {
            Badge(Modifier.align(Alignment.BottomEnd)) {
                Text(formatDuration(item.durationMillis), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (item.favorite) {
            Icon(
                painterResource(DesignR.drawable.ic_favorite_filled),
                contentDescription = stringResource(R.string.cd_favorite),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
            )
        }
        if (caption != null) {
            Badge(Modifier.align(Alignment.TopStart)) { Text(caption, style = MaterialTheme.typography.labelSmall) }
        }
        if (selecting) {
            Icon(
                painterResource(if (selected) DesignR.drawable.ic_check_circle else DesignR.drawable.ic_radio_button_unchecked),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.inverseOnSurface,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                            RoundedCornerShape(50),
                        ),
            )
        }
    }
}

/** A small pill over a thumbnail, readable on any photo. */
@Composable
private fun Badge(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(50),
        modifier = modifier.padding(6.dp),
    ) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) { content() }
    }
}
