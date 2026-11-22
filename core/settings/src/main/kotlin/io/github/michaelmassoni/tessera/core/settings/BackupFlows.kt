package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A backup chosen to restore, waiting for the user to confirm. */
sealed interface PendingRestore {
    val uri: Uri
    val created: Long

    data class One(
        override val uri: Uri,
        override val created: Long,
        val label: String,
    ) : PendingRestore

    data class All(
        override val uri: Uri,
        override val created: Long,
        val labels: List<String>,
    ) : PendingRestore
}

/** The backup and restore work behind the settings screen's Backup section. Call off the main thread. */
internal class BackupFlows(
    private val context: Context,
) {
    private val own = AppBackup(context)
    private val suite = SuiteBackup(context)
    private val resolver = context.contentResolver

    /** A file name like "Tessera Notes 2026-11-22.zip". */
    fun fileName(all: Boolean): String {
        val date = DateFormat.format("yyyy-MM-dd", System.currentTimeMillis())
        return if (all) "Tessera $date.zip" else "Tessera ${own.label} $date.zip"
    }

    suspend fun backUp(uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { resolver.openOutputStream(uri, "wt")!!.use(own::write) }.isSuccess
        }

    /** @return how many apps were backed up (this one first), or -1 if the file couldn't be written. */
    suspend fun backUpAll(uri: Uri): Int =
        withContext(Dispatchers.IO) {
            runCatching {
                resolver.openOutputStream(uri, "wt")!!.use { out ->
                    val apps =
                        listOf(context.packageName to own::write) +
                            suite.peers().map { p -> p.packageName to { o: java.io.OutputStream -> suite.backUp(p, o) } }
                    BackupArchive
                        .writeSuite(out, BackupManifest(BackupArchive.SUITE, "Tessera", 0, System.currentTimeMillis()), apps)
                        .size
                }
            }.getOrDefault(-1)
        }

    /** Reads what [uri] holds, to ask before restoring. Throws [RestoreRefused] for a file that isn't a backup. */
    suspend fun inspect(uri: Uri): PendingRestore =
        withContext(Dispatchers.IO) {
            val manifest =
                resolver.openInputStream(uri)?.use(BackupArchive::manifest) ?: throw RestoreRefused(RestoreRefused.Reason.NotTessera)
            if (manifest.app != BackupArchive.SUITE) return@withContext PendingRestore.One(uri, manifest.created, manifest.label)
            val installed = (listOf(context.packageName to own.label) + suite.peers().map { it.packageName to it.label }).toMap()
            val labels = mutableListOf<String>()
            resolver.openInputStream(uri)!!.use { input ->
                BackupArchive.readSuite(input) { pkg, _ -> installed[pkg]?.let(labels::add) }
            }
            PendingRestore.All(uri, manifest.created, labels)
        }

    /** Restores this app from a single-app backup. The caller relaunches the app. */
    suspend fun restore(uri: Uri): BackupManifest = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use(own::restore) }

    /**
     * Restores every installed app in a suite backup: the others first (each restarts itself),
     * then this one. @return how many were restored and whether this app was among them.
     */
    suspend fun restoreAll(uri: Uri): Pair<Int, Boolean> =
        withContext(Dispatchers.IO) {
            val peers = suite.peers().associateBy { it.packageName }
            var count = 0
            var mine: ByteArray? = null
            resolver.openInputStream(uri)!!.use { input ->
                BackupArchive.readSuite(input) { pkg, entry ->
                    when {
                        // Kept until the others are done: restoring this app restarts it.
                        pkg == context.packageName -> mine = entry.readBytes()

                        peers[pkg] != null -> if (runCatching { suite.restore(peers.getValue(pkg), entry) }.getOrDefault(false)) count++
                    }
                }
            }
            val self = mine?.let { bytes -> runCatching { own.restore(bytes.inputStream()) }.isSuccess } ?: false
            (count + if (self) 1 else 0) to self
        }

    /** A refusal as a message. */
    fun message(e: Throwable): String =
        when ((e as? RestoreRefused)?.reason) {
            RestoreRefused.Reason.NotTessera -> context.getString(R.string.restore_not_tessera)
            RestoreRefused.Reason.OtherApp -> context.getString(R.string.restore_other_app)
            RestoreRefused.Reason.NewerVersion -> context.getString(R.string.restore_newer)
            null -> context.getString(R.string.restore_failed)
        }
}

/** Asks before replacing data with a backup. */
@Composable
internal fun RestoreDialog(
    pending: PendingRestore,
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val date = DateFormat.getMediumDateFormat(androidx.compose.ui.platform.LocalContext.current).format(java.util.Date(pending.created))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (pending) {
                    is PendingRestore.One -> stringResource(R.string.restore_title, appLabel)
                    is PendingRestore.All -> stringResource(R.string.restore_all_title)
                },
            )
        },
        text = {
            Text(
                when (pending) {
                    is PendingRestore.One -> stringResource(R.string.restore_text, appLabel, date)
                    is PendingRestore.All -> stringResource(R.string.restore_all_text, date, pending.labels.joinToString(", "))
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.restore_cancel)) } },
    )
}

/** Shown while a backup or restore runs: it can take a few seconds with many apps or photos. */
@Composable
internal fun WorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        text = {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator()
                Text(
                    stringResource(R.string.backup_working),
                    modifier =
                        androidx.compose.ui.Modifier
                            .padding(start = 16.dp),
                )
            }
        },
        confirmButton = {},
    )
}
