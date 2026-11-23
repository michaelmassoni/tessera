package io.github.michaelmassoni.tessera.weather.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings
import io.github.michaelmassoni.tessera.core.settings.ThemeMode
import androidx.glance.color.ColorProvider as DayNightColorProvider

enum class WidgetBackground { Tinted, Solid, Transparent }

enum class WidgetTheme { App, Light, Dark }

/** How the date widget writes the date. */
enum class DateStyle { Long, Short }

/**
 * One placed weather widget's settings, kept in its Glance state (each widget has its own). The
 * forecast widget uses the hours/days options, the date widget the date ones; both use the rest.
 */
data class WeatherWidgetSettings(
    /** A saved place's id, [io.github.michaelmassoni.tessera.weather.engine.Place.HERE], or null to follow the app's main place. */
    val place: Long? = null,
    val background: WidgetBackground = WidgetBackground.Tinted,
    /** 0–100, of the background only. */
    val opacity: Int = 100,
    val theme: WidgetTheme = WidgetTheme.App,
    val showName: Boolean = true,
    val showHighLow: Boolean = true,
    val showHours: Boolean = true,
    val showDays: Boolean = true,
    val showCondition: Boolean = true,
    val dateStyle: DateStyle = DateStyle.Long,
) {
    fun write(prefs: MutablePreferences) {
        place?.let { prefs[PLACE] = it } ?: prefs.remove(PLACE)
        prefs[BACKGROUND] = background.name
        prefs[OPACITY] = opacity
        prefs[THEME] = theme.name
        prefs[NAME] = showName
        prefs[HIGH_LOW] = showHighLow
        prefs[HOURS] = showHours
        prefs[DAYS] = showDays
        prefs[CONDITION] = showCondition
        prefs[DATE_STYLE] = dateStyle.name
        prefs[SET] = true
    }

    /** The theme's colours (or the Tessera apps' theme), and the background with its opacity. */
    fun colors(context: Context): Pair<androidx.glance.color.ColorProviders, ColorProvider> {
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
            ThemeMode.System -> ColorProviders(light, dark) to DayNightColorProvider(day = fill(light), night = fill(dark))
            ThemeMode.Light -> ColorProviders(light) to ColorProvider(fill(light))
            ThemeMode.Dark -> ColorProviders(dark) to ColorProvider(fill(dark))
        }
    }

    companion object {
        private val PLACE = longPreferencesKey("place")
        private val BACKGROUND = stringPreferencesKey("background")
        private val OPACITY = intPreferencesKey("opacity")
        private val THEME = stringPreferencesKey("theme")
        private val NAME = booleanPreferencesKey("show_name")
        private val HIGH_LOW = booleanPreferencesKey("show_high_low")
        private val HOURS = booleanPreferencesKey("show_hours")
        private val DAYS = booleanPreferencesKey("show_days")
        private val CONDITION = booleanPreferencesKey("show_condition")
        private val DATE_STYLE = stringPreferencesKey("date_style")
        private val SET = booleanPreferencesKey("configured")

        /**
         * The date widget sits on the wallpaper by default, like the At a Glance date it stands in
         * for: light text (the dark theme's) and no place name.
         */
        val DATE_DEFAULTS = WeatherWidgetSettings(background = WidgetBackground.Transparent, theme = WidgetTheme.Dark, showName = false)

        fun from(
            prefs: Preferences?,
            defaults: WeatherWidgetSettings = WeatherWidgetSettings(),
        ): WeatherWidgetSettings {
            if (prefs == null || prefs[SET] != true) return defaults
            return WeatherWidgetSettings(
                place = prefs[PLACE],
                background = WidgetBackground.entries.firstOrNull { it.name == prefs[BACKGROUND] } ?: defaults.background,
                opacity = (prefs[OPACITY] ?: defaults.opacity).coerceIn(0, 100),
                theme = WidgetTheme.entries.firstOrNull { it.name == prefs[THEME] } ?: defaults.theme,
                showName = prefs[NAME] ?: defaults.showName,
                showHighLow = prefs[HIGH_LOW] ?: defaults.showHighLow,
                showHours = prefs[HOURS] ?: defaults.showHours,
                showDays = prefs[DAYS] ?: defaults.showDays,
                showCondition = prefs[CONDITION] ?: defaults.showCondition,
                dateStyle = DateStyle.entries.firstOrNull { it.name == prefs[DATE_STYLE] } ?: defaults.dateStyle,
            )
        }
    }
}
