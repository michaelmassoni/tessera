package io.github.michaelmassoni.tessera.files.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.files.engine.SortField
import io.github.michaelmassoni.tessera.files.engine.SortOrder

data class ViewOptions(
    val sort: SortOrder = SortOrder(),
    val grid: Boolean = false,
    val showHidden: Boolean = false,
)

interface FilesSettings {
    var options: ViewOptions
}

class SharedPreferencesFilesSettings(
    private val prefs: SharedPreferences,
) : FilesSettings {
    override var options: ViewOptions
        get() =
            ViewOptions(
                sort =
                    SortOrder(
                        field = runCatching { SortField.valueOf(prefs.getString("sort", null) ?: "") }.getOrDefault(SortField.Name),
                        ascending = prefs.getBoolean("ascending", true),
                    ),
                grid = prefs.getBoolean("grid", false),
                showHidden = prefs.getBoolean("hidden", false),
            )
        set(value) =
            prefs.edit {
                putString("sort", value.sort.field.name)
                putBoolean("ascending", value.sort.ascending)
                putBoolean("grid", value.grid)
                putBoolean("hidden", value.showHidden)
            }
}
