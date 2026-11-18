package io.github.michaelmassoni.tessera.files.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.engine.FileEntry
import io.github.michaelmassoni.tessera.files.engine.FileType
import java.io.File
import io.github.michaelmassoni.tessera.core.design.R as DesignR

fun FileType.icon(): Int =
    when (this) {
        FileType.Folder -> DesignR.drawable.ic_folder
        FileType.Image -> DesignR.drawable.ic_image
        FileType.Video -> DesignR.drawable.ic_movie
        FileType.Audio -> DesignR.drawable.ic_music_note
        FileType.Pdf -> DesignR.drawable.ic_picture_as_pdf
        FileType.Document -> DesignR.drawable.ic_description
        FileType.Archive -> DesignR.drawable.ic_folder_zip
        FileType.Apk -> DesignR.drawable.ic_android
        FileType.Other -> DesignR.drawable.ic_draft
    }

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun FileType.label(): String =
    stringResource(
        when (this) {
            FileType.Folder -> R.string.type_folder
            FileType.Image -> R.string.type_image
            FileType.Video -> R.string.type_video
            FileType.Audio -> R.string.type_audio
            FileType.Pdf -> R.string.type_pdf
            FileType.Document -> R.string.type_document
            FileType.Archive -> R.string.type_archive
            FileType.Apk -> R.string.type_apk
            FileType.Other -> R.string.type_file
        },
    )

/** "12 items" for folders, "3.4 MB" for files. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun sizeText(entry: FileEntry): String {
    val context = LocalContext.current
    return if (entry.isDirectory) {
        val n = entry.childCount ?: 0
        pluralStringResource(R.plurals.items, n, n)
    } else {
        Formatter.formatShortFileSize(context, entry.size)
    }
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun dateText(entry: FileEntry): String {
    val context = LocalContext.current
    return DateUtils.formatDateTime(context, entry.lastModified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
}

@Composable
private fun EntryIcon(
    entry: FileEntry,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val thumbnail by rememberThumbnail(entry)
    val bitmap = thumbnail
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (entry.isDirectory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(entry.type.icon()),
                contentDescription = null,
                tint = if (entry.isDirectory) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPath: Boolean = false,
) {
    val label = "${entry.name}, ${entry.type.label()}, ${sizeText(entry)}, ${dateText(entry)}"
    val selectLabel = stringResource(R.string.action_select)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = selectLabel)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                EntryIcon(entry, 44.dp)
                if (selected) SelectedBadge(Modifier.align(Alignment.BottomEnd))
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    if (showPath) entry.file.parent.orEmpty() else "${sizeText(entry)} · ${dateText(entry)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTile(
    entry: FileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = "${entry.name}, ${entry.type.label()}, ${sizeText(entry)}"
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier =
            modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.action_select))
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                },
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                EntryIcon(entry, size = 160.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Icon(
        painterResource(DesignR.drawable.ic_check_circle),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(22.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge),
    )
}

/** Tappable path segments from the volume root to [location]. */
@Composable
fun Breadcrumbs(
    rootName: String,
    root: File,
    location: File,
    onOpen: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments =
        buildList {
            var f: File? = location
            while (f != null && f.path.length >= root.path.length) {
                add(f)
                if (f == root) break
                f = f.parentFile
            }
        }.reversed()
    val state = rememberLazyListState()
    LaunchedEffect(location) { state.scrollToItem((segments.size - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(segments) { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Icon(
                        painterResource(DesignR.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = { onOpen(f) }, enabled = i < segments.lastIndex) {
                    Text(if (f == root) rootName else f.name, maxLines = 1)
                }
            }
        }
    }
}
