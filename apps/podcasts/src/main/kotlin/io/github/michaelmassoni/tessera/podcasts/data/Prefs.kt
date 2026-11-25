package io.github.michaelmassoni.tessera.podcasts.data

import android.content.Context
import androidx.core.content.edit

/** Podcasts' own settings, in `settings.xml` (backed up with Tessera's backups). */
object Prefs {
    private const val FILE = "settings"
    private const val AUTO_DOWNLOAD = "auto_download"
    private const val DELETE_PLAYED = "delete_played"
    private const val MOBILE_DOWNLOADS = "mobile_downloads"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun autoDownload(context: Context) = prefs(context).getBoolean(AUTO_DOWNLOAD, true)

    fun setAutoDownload(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit { putBoolean(AUTO_DOWNLOAD, on) }

    fun deletePlayed(context: Context) = prefs(context).getBoolean(DELETE_PLAYED, true)

    fun setDeletePlayed(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit { putBoolean(DELETE_PLAYED, on) }

    fun mobileDownloads(context: Context) = prefs(context).getBoolean(MOBILE_DOWNLOADS, false)

    fun setMobileDownloads(
        context: Context,
        on: Boolean,
    ) = prefs(context).edit { putBoolean(MOBILE_DOWNLOADS, on) }
}
