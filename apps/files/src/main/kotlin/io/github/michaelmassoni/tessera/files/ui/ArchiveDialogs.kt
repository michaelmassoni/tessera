package io.github.michaelmassoni.tessera.files.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.engine.ArchiveFormat
import io.github.michaelmassoni.tessera.files.engine.ArchiveSummary
import io.github.michaelmassoni.tessera.files.engine.CompressOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Compress: the archive's name (the suggestion selected, ready to type over) and its format. */
@Composable
internal fun CompressDialog(
    suggested: String,
    onConfirm: (CompressOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState(suggested)
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.Zip) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        name.edit { selectAll() }
        focus.requestFocus()
    }
    val valid = name.text.isNotBlank() && '/' !in name.text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_compress)) },
        text = {
            Column {
                OutlinedTextField(
                    state = name,
                    label = { Text(stringResource(R.string.archive_name)) },
                    suffix = { Text(".${format.extension}") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                val formats = ArchiveFormat.entries.filter { it.writable }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    formats.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = format == f,
                            onClick = { format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, formats.size),
                        ) { Text(stringResource(if (f == ArchiveFormat.Zip) R.string.format_zip else R.string.format_tar_gz)) }
                    }
                }
                Text(
                    stringResource(if (format == ArchiveFormat.Zip) R.string.format_zip_hint else R.string.format_tar_gz_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(CompressOptions(name.text.toString().trim(), format)) }, enabled = valid) {
                Text(stringResource(R.string.action_compress))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Tapping an archive: what's in it, then Extract here, or open it in another app. */
@Composable
internal fun ArchiveDialog(
    archive: File,
    summary: (File) -> ArchiveSummary?,
    onExtract: () -> Unit,
    onOpenWith: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Null while reading; a tar.gz has to be read through to count it.
    val read by produceState<Result<ArchiveSummary?>?>(null, archive) {
        value =
            withContext(Dispatchers.IO) { runCatching { summary(archive) } }
    }
    val contents = read?.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(archive.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                when {
                    read == null -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    contents == null -> {
                        Text(stringResource(R.string.archive_unreadable))
                    }

                    else -> {
                        Text(
                            pluralStringResource(R.plurals.archive_files, contents.files, contents.files) + " · " +
                                Formatter.formatShortFileSize(context, contents.bytes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        contents.sample.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        val more = contents.files + contents.folders - contents.sample.size
                        if (more > 0) {
                            Text(
                                pluralStringResource(R.plurals.archive_more, more, more),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExtract, enabled = contents != null) { Text(stringResource(R.string.action_extract)) }
        },
        dismissButton = {
            TextButton(onClick = onOpenWith) { Text(stringResource(R.string.action_open_with)) }
        },
    )
}

@TesseraPreviews
@Composable
private fun CompressDialogPreview() {
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) { CompressDialog("Holiday", onConfirm = {}, onDismiss = {}) }
    }
}

@TesseraPreviews
@Composable
private fun ArchiveDialogPreview() {
    val summary =
        ArchiveSummary(files = 42, folders = 3, bytes = 18_400_000, sample = listOf("Trip/", "Trip/notes.txt", "Trip/photos/a.jpg"))
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            ArchiveDialog(File("Trip.zip"), summary = { summary }, onExtract = {}, onOpenWith = {}, onDismiss = {})
        }
    }
}
