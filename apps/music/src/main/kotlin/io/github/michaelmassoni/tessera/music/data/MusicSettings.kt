package io.github.michaelmassoni.tessera.music.data

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.music.engine.SortOrder

/** Music's own settings (kept in `settings.xml`, which Android backs up). */
class MusicSettings(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Look up lyrics on LRCLIB when the song has none of its own. Off until the person turns it on. */
    var onlineLyrics: Boolean
        get() = prefs.getBoolean("online_lyrics", false)
        set(value) = prefs.edit { putBoolean("online_lyrics", value) }

    /** How each library page is ordered and shown, remembered between visits. */
    fun sort(
        page: String,
        default: SortOrder,
    ): SortOrder = SortOrder.entries.firstOrNull { it.name == prefs.getString("sort_$page", null) } ?: default

    fun setSort(
        page: String,
        order: SortOrder,
    ) = prefs.edit { putString("sort_$page", order.name) }

    fun grid(
        page: String,
        default: Boolean,
    ): Boolean = prefs.getBoolean("grid_$page", default)

    fun setGrid(
        page: String,
        grid: Boolean,
    ) = prefs.edit { putBoolean("grid_$page", grid) }

    /** Artists by album artist (one per album's credit) rather than every track artist. */
    var albumArtists: Boolean
        get() = prefs.getBoolean("album_artists", true)
        set(value) = prefs.edit { putBoolean("album_artists", value) }
}
