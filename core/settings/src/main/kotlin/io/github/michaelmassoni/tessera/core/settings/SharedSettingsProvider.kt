package io.github.michaelmassoni.tessera.core.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings.Companion.toBundle
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings.Companion.toSharedState
import java.util.concurrent.Executors

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

            else -> {
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
    }
}

/** Finds the other Tessera apps through the provider's intent filter and talks to them. */
internal class ProviderPeers(
    private val app: Context,
) : TesseraSettings.Peers {
    private val background = Executors.newSingleThreadExecutor()

    private fun authorities(): List<String> =
        app.packageManager
            .queryIntentContentProviders(Intent(SharedSettingsProvider.ACTION), 0)
            .mapNotNull { it.providerInfo }
            .filter { it.packageName != app.packageName }
            .filter { app.packageManager.checkSignatures(it.packageName, app.packageName) == PackageManager.SIGNATURE_MATCH }
            .map { it.authority }

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

    private companion object {
        const val TAG = "TesseraSettings"
    }
}
