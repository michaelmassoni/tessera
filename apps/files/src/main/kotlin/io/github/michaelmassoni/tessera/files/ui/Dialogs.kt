package io.github.michaelmassoni.tessera.files.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.data.FileIntents
import io.github.michaelmassoni.tessera.files.data.OperationKind
import io.github.michaelmassoni.tessera.files.data.RunningOperation
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Naming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OperationDialog(
    operation: RunningOperation,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val p = operation.progress
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when (operation.kind) {
                        OperationKind.Copy -> R.string.progress_copying
                        OperationKind.Move -> R.string.progress_moving
                        OperationKind.Compress -> R.string.progress_compressing
                        OperationKind.Extract -> R.string.progress_extracting
                        null -> R.string.progress_deleting
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p != null && p.bytesTotal > 0) {
                    LinearWavyProgressIndicator(progress = { p.bytesDone.toFloat() / p.bytesTotal }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(
                            R.string.progress_detail,
                            pluralStringResource(R.plurals.files_progress, p.filesTotal, p.filesDone, p.filesTotal),
                            Formatter.formatShortFileSize(context, p.bytesDone),
                            Formatter.formatShortFileSize(context, p.bytesTotal),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { if (operation.kind != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.delete_title, count, count)) },
        text = { Text(stringResource(R.string.delete_permanent)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_move_to_trash)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Rename / new folder. For files, only the base name is pre-selected, not the extension. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    selectBaseName: Boolean,
    onConfirm: (String) -> NameProblem?,
    onDismiss: () -> Unit,
) {
    val selectionEnd = if (selectBaseName) Naming.split(initial).first.length else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectionEnd))) }
    var problem by remember { mutableStateOf<NameProblem?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    problem = null
                },
                singleLine = true,
                isError = problem != null,
                supportingText = { problem?.let { Text(it.message()) } },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { problem = onConfirm(value.text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DetailsDialog(
    file: File,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Folder sizes are computed in the background; files are instant.
    val measured by produceState<Pair<Int, Long>?>(null, file) {
        value = withContext(Dispatchers.IO) { FileOperations(freeSpace = { 0 }).measure(listOf(file)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Detail(stringResource(R.string.detail_path), file.parent.orEmpty())
                Detail(
                    stringResource(R.string.detail_type),
                    if (file.isDirectory) stringResource(R.string.type_folder) else FileIntents.mimeType(file),
                )
                val m = measured
                Detail(
                    stringResource(R.string.detail_size),
                    when {
                        m == null -> {
                            stringResource(R.string.calculating)
                        }

                        file.isDirectory -> {
                            pluralStringResource(R.plurals.items, m.first, m.first) + " · " + Formatter.formatFileSize(context, m.second)
                        }

                        else -> {
                            Formatter.formatFileSize(context, m.second)
                        }
                    },
                )
                Detail(
                    stringResource(R.string.detail_modified),
                    DateUtils.formatDateTime(
                        context,
                        file.lastModified(),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
private fun Detail(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
