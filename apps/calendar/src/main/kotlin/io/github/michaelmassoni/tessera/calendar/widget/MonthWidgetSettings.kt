package io.github.michaelmassoni.tessera.calendar.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings
import io.github.michaelmassoni.tessera.core.settings.ThemeMode
import androidx.glance.color.ColorProvider as DayNightColorProvider

enum class WidgetBackground { Solid, Tinted, Transparent }

enum class WidgetTheme { App, Light, Dark }

/**
 * A month widget's own settings (each placed widget has its own), kept in its Glance state, plus
 * the month it's showing ([offset] from this month) and [stamp], bumped to make it reload.
 */
data class MonthWidgetSettings(
    val background: WidgetBackground = WidgetBackground.Tinted,
    /** 0–100, of the background only (the day cells stay as they are). */
    val opacity: Int = 100,
    val theme: WidgetTheme = WidgetTheme.App,
    /** Calendars left out; empty shows every visible calendar. */
    val hidden: Set<Long> = emptySet(),
    val offset: Int = 0,
    val stamp: Long = 0,
) {
    fun write(prefs: MutablePreferences) {
        prefs[BACKGROUND] = background.name
        prefs[OPACITY] = opacity
        prefs[THEME] = theme.name
        prefs[HIDDEN] = hidden.map { it.toString() }.toSet()
        prefs[OFFSET] = offset
        prefs[STAMP] = stamp
    }

    companion object {
        private val BACKGROUND = stringPreferencesKey("background")
        private val OPACITY = intPreferencesKey("opacity")
        private val THEME = stringPreferencesKey("theme")
        private val HIDDEN = stringSetPreferencesKey("hidden_calendars")
        val OFFSET = intPreferencesKey("month_offset")
        val STAMP = longPreferencesKey("stamp")

        fun from(prefs: Preferences?): MonthWidgetSettings {
            if (prefs == null) return MonthWidgetSettings()
            return MonthWidgetSettings(
                background = WidgetBackground.entries.firstOrNull { it.name == prefs[BACKGROUND] } ?: WidgetBackground.Tinted,
                opacity = (prefs[OPACITY] ?: 100).coerceIn(0, 100),
                theme = WidgetTheme.entries.firstOrNull { it.name == prefs[THEME] } ?: WidgetTheme.App,
                hidden = prefs[HIDDEN].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
                offset = prefs[OFFSET] ?: 0,
                stamp = prefs[STAMP] ?: 0,
            )
        }
    }
}

/** The widget's colours for its settings: the theme (or the Tessera app's), and the background. */
internal class WidgetColors(
    val scheme: ColorProviders,
    val background: ColorProvider,
)

internal fun MonthWidgetSettings.colors(context: Context): WidgetColors {
    val light = dynamicLightColorScheme(context)
    val dark = dynamicDarkColorScheme(context)
    val mode =
        when (theme) {
            WidgetTheme.Light -> {
                ThemeMode.Light
            }

            WidgetTheme.Dark -> {
                ThemeMode.Dark
            }

            WidgetTheme.App -> {
                TesseraSettings
                    .get(context)
                    .shared.value.theme.mode
            }
        }

    fun fill(scheme: ColorScheme): Color =
        when (background) {
            WidgetBackground.Solid -> scheme.surfaceContainerLowest
            WidgetBackground.Tinted -> scheme.surfaceContainer
            WidgetBackground.Transparent -> Color.Transparent
        }.let { it.copy(alpha = it.alpha * opacity / 100f) }
    return when (mode) {
        ThemeMode.System -> WidgetColors(ColorProviders(light, dark), DayNightColorProvider(day = fill(light), night = fill(dark)))
        ThemeMode.Light -> WidgetColors(ColorProviders(light), ColorProvider(fill(light)))
        ThemeMode.Dark -> WidgetColors(ColorProviders(dark), ColorProvider(fill(dark)))
    }
}
