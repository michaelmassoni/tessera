package io.github.michaelmassoni.tessera.keyboard

import android.content.Context
import androidx.core.content.edit

/** Keyboard preferences, shared by the service and the settings screen. */
class KeyboardPrefs(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var numberRow: Boolean
        get() = prefs.getBoolean("number_row", false)
        set(value) = prefs.edit { putBoolean("number_row", value) }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit { putBoolean("haptics", value) }

    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean("double_space_period", true)
        set(value) = prefs.edit { putBoolean("double_space_period", value) }

    var suggestions: Boolean
        get() = prefs.getBoolean("suggestions", true)
        set(value) = prefs.edit { putBoolean("suggestions", value) }

    var autocorrect: Boolean
        get() = prefs.getBoolean("autocorrect", true)
        set(value) = prefs.edit { putBoolean("autocorrect", value) }

    var autoCaps: Boolean
        get() = prefs.getBoolean("auto_caps", true)
        set(value) = prefs.edit { putBoolean("auto_caps", value) }

    var blockOffensive: Boolean
        get() = prefs.getBoolean("block_offensive", true)
        set(value) = prefs.edit { putBoolean("block_offensive", value) }

    /** Words the user kept, as dictionary keys. Stored only on this device. */
    var learned: Set<String>
        get() = prefs.getStringSet("learned", emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet("learned", value) }
}
