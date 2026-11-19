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
}
