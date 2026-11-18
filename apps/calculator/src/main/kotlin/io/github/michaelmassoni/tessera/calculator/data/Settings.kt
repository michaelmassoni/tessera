package io.github.michaelmassoni.tessera.calculator.data

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit

/** Small persisted preferences. An interface so the ViewModel can be tested without Android. */
interface CalculatorSettings {
    var angleUnit: AngleUnit
    var scientificExpanded: Boolean?
}

class SharedPreferencesSettings(
    private val prefs: SharedPreferences,
) : CalculatorSettings {
    override var angleUnit: AngleUnit
        get() = if (prefs.getString(KEY_ANGLE, null) == AngleUnit.RADIANS.name) AngleUnit.RADIANS else AngleUnit.DEGREES
        set(value) = prefs.edit { putString(KEY_ANGLE, value.name) }

    /** null until the user toggles it, so the layout can choose its own default. */
    override var scientificExpanded: Boolean?
        get() = if (prefs.contains(KEY_SCIENTIFIC)) prefs.getBoolean(KEY_SCIENTIFIC, false) else null
        set(value) =
            prefs.edit {
                if (value == null) remove(KEY_SCIENTIFIC) else putBoolean(KEY_SCIENTIFIC, value)
            }

    private companion object {
        const val KEY_ANGLE = "angle_unit"
        const val KEY_SCIENTIFIC = "scientific_expanded"
    }
}
