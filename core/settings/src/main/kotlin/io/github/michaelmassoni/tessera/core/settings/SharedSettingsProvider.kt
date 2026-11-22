package io.github.michaelmassoni.tessera.core.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings.Companion.toBundle
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings.Companion.toSharedState
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Lets Tessera apps read and share each other's appearance settings.
 *
 * Every app exports one of these (declared in this module's manifest). Only callers signed with
 * the same certificate are served. A signature-level custom permission would also do that, but
 * two apps defining the same permission with different signers can't both be installed, which
 * would break mixing builds from GitHub and F-Droid. Checking the signature here only means
 * apps from different signers don't share settings.
 */
class SharedSettingsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val context = context ?: return null
        if (!callerIsTrusted(context)) throw SecurityException("Caller isn't signed like this app")
        val settings = TesseraSettings.get(context)
        return when (method) {
            METHOD_GET -> {
                settings.shared.value.toBundle()
            }

            METHOD_PUT -> {
                extras?.let { settings.receive(it.toSharedState()) }
                null
            }

            METHOD_RESTORE_STATUS -> {
                val status = restoreStatus
                Bundle().apply { putString(KEY_STATUS, status) }.also {
                    // Reported: restart so the app reads its restored data (it's in the background).
                    if (status ==
                        STATUS_DONE
                    ) {
                        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, KILL_DELAY_MS)
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    /**
     * Backups for "Back up all Tessera apps": `backup` streams this app's backup out, `restore`
     * takes one in (reported through [METHOD_RESTORE_STATUS]). Same signature check as [call].
     */
    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        val context = context ?: return null
        if (!callerIsTrusted(context)) throw SecurityException("Caller isn't signed like this app")
        val backup = AppBackup(context)
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        return when (uri.lastPathSegment) {
            "backup" -> {
                thread(name = "tessera-backup") {
                    runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use(backup::write) }
                        .onFailure { Log.w(TAG, "Backup failed", it) }
                }
                read
            }

            "restore" -> {
                restoreStatus = STATUS_RUNNING
                thread(name = "tessera-restore") {
                    restoreStatus =
                        runCatching { ParcelFileDescriptor.AutoCloseInputStream(read).use(backup::restore) }
                            .onFailure { Log.w(TAG, "Restore failed", it) }
                            .fold({ STATUS_DONE }, { STATUS_FAILED })
                }
                write
            }

            else -> {
                read.close()
                write.close()
                null
            }
        }
    }

    private fun callerIsTrusted(context: Context): Boolean {
        val caller = callingPackage ?: return false
        return context.packageManager.checkSignatures(caller, context.packageName) == PackageManager.SIGNATURE_MATCH
    }

    // Not a data provider: only call() is used.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    internal companion object {
        const val ACTION = "io.github.michaelmassoni.tessera.action.SHARED_SETTINGS"
        const val METHOD_GET = "get"
        const val METHOD_PUT = "put"
        const val METHOD_RESTORE_STATUS = "restore_status"
        const val KEY_STATUS = "status"
        const val STATUS_RUNNING = "running"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        private const val KILL_DELAY_MS = 500L
        private const val TAG = "TesseraBackup"

        @Volatile private var restoreStatus: String? = null
    }
}

/** Finds the other Tessera apps through the provider's intent filter and talks to them. */
internal class ProviderPeers(
    private val app: Context,
) : TesseraSettings.Peers {
    private val background = Executors.newSingleThreadExecutor()

    private fun authorities(): List<String> = find(app).map { it.authority }

    override fun readAll(): List<SharedState> =
        authorities().mapNotNull { authority ->
            safely(authority) { app.contentResolver.call(authority, SharedSettingsProvider.METHOD_GET, null, null)?.toSharedState() }
        }

    override fun pushAll(state: SharedState) {
        val bundle = state.toBundle()
        background.execute {
            authorities().forEach { authority ->
                safely(authority) { app.contentResolver.call(authority, SharedSettingsProvider.METHOD_PUT, null, bundle) }
            }
        }
    }

    private fun <T> safely(
        authority: String,
        block: () -> T,
    ): T? =
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't share settings with $authority", e)
            null
        } catch (e: IllegalArgumentException) {
            // The app was uninstalled between finding it and calling it.
            Log.w(TAG, "Can't share settings with $authority", e)
            null
        }

    companion object {
        private const val TAG = "TesseraSettings"

        /** The other Tessera apps signed like this one. */
        fun find(app: Context): List<ProviderInfo> =
            app.packageManager
                .queryIntentContentProviders(Intent(SharedSettingsProvider.ACTION), 0)
                .mapNotNull { it.providerInfo }
                .filter { it.packageName != app.packageName }
                .filter { app.packageManager.checkSignatures(it.packageName, app.packageName) == PackageManager.SIGNATURE_MATCH }
    }
}
