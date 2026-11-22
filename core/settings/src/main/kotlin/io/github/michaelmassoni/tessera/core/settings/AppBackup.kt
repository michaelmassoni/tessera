package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Why a backup wasn't restored. */
class RestoreRefused(
    val reason: Reason,
) : IOException(reason.name) {
    enum class Reason { NotTessera, OtherApp, NewerVersion }
}

/**
 * This app's data as a backup: its private files and settings, in normal and device-protected
 * storage (Clock keeps alarms there). An app can leave more out with a manifest
 * `<meta-data android:name="io.github.michaelmassoni.tessera.BACKUP_EXCLUDE" android:value="files/forecasts/,…"/>`.
 */
class AppBackup(
    context: Context,
) {
    private val app = context.applicationContext
    private val protected = app.createDeviceProtectedStorageContext()

    private val roots: Map<String, File> =
        mapOf(
            "files" to app.filesDir,
            "prefs" to File(app.dataDir, "shared_prefs"),
            "de_files" to protected.filesDir,
            "de_prefs" to File(protected.dataDir, "shared_prefs"),
        )

    private val exclude: List<String> =
        runCatching {
            app.packageManager
                .getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getString(META_EXCLUDE)
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    val label: String get() = app.applicationInfo.loadLabel(app.packageManager).toString()

    private val versionCode: Long get() = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode

    fun manifest() = BackupManifest(app.packageName, label, versionCode, System.currentTimeMillis())

    fun write(out: OutputStream) = BackupArchive.write(out, manifest(), roots, exclude)

    /**
     * Replaces this app's data with [input]'s. Refuses another app's backup, or one from a newer
     * version of this app (its data may not read). The app should restart afterwards.
     */
    fun restore(input: InputStream): BackupManifest =
        BackupArchive
            .restore(input, roots, File(app.cacheDir, "restore"), exclude) { m ->
                if (m.app != app.packageName) throw RestoreRefused(RestoreRefused.Reason.OtherApp)
                if (m.versionCode > versionCode) throw RestoreRefused(RestoreRefused.Reason.NewerVersion)
            }.also { File(app.noBackupFilesDir, RESTORED).createNewFile() }

    companion object {
        const val META_EXCLUDE = "io.github.michaelmassoni.tessera.BACKUP_EXCLUDE"
        private const val RESTORED = "tessera-restored"

        /**
         * True once after a restore (an app that keeps system state, like Clock's alarms, sets it
         * up again from the restored data).
         */
        fun consumeRestored(context: Context): Boolean = File(context.noBackupFilesDir, RESTORED).delete()

        /** Restarts the app on its launcher entry, so everything reloads from the restored data. */
        fun relaunch(context: Context) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            Process.killProcess(Process.myPid())
        }
    }
}

/** Another installed Tessera app signed like this one: its name and where to reach it. */
data class Peer(
    val packageName: String,
    val label: String,
    val authority: String,
)

/** Backing up and restoring the other Tessera apps through their settings providers. */
class SuiteBackup(
    context: Context,
) {
    private val app = context.applicationContext

    fun peers(): List<Peer> =
        ProviderPeers.find(app).map { info ->
            Peer(info.packageName, info.loadLabel(app.packageManager).toString(), info.authority)
        }

    /** Streams [peer]'s backup into [out]. */
    fun backUp(
        peer: Peer,
        out: OutputStream,
    ) {
        val fd =
            app.contentResolver.openFileDescriptor("content://${peer.authority}/backup".toUri(), "r")
                ?: throw IOException("No backup from ${peer.packageName}")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.copyTo(out) }
    }

    /** Sends [input] (an app backup) to [peer] to restore. @return whether it was restored. */
    fun restore(
        peer: Peer,
        input: InputStream,
    ): Boolean {
        val fd = app.contentResolver.openFileDescriptor("content://${peer.authority}/restore".toUri(), "w") ?: return false
        ParcelFileDescriptor.AutoCloseOutputStream(fd).use { input.copyTo(it) }
        // The peer reports once it has unpacked everything, then restarts itself.
        repeat(STATUS_TRIES) {
            val status = app.contentResolver.call(peer.authority, SharedSettingsProvider.METHOD_RESTORE_STATUS, null, null)
            when (status?.getString(SharedSettingsProvider.KEY_STATUS)) {
                SharedSettingsProvider.STATUS_DONE -> return true
                SharedSettingsProvider.STATUS_FAILED -> return false
            }
            Thread.sleep(STATUS_WAIT_MS)
        }
        return false
    }

    private companion object {
        const val STATUS_TRIES = 40
        const val STATUS_WAIT_MS = 250L
    }
}
