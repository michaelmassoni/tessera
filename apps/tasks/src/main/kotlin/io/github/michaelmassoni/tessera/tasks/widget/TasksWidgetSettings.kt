package io.github.michaelmassoni.tessera.tasks.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings
import io.github.michaelmassoni.tessera.core.settings.ThemeMode
import io.github.michaelmassoni.tessera.tasks.engine.Sort
import androidx.glance.color.ColorProvider as DayNightColorProvider

enum class WidgetBackground { Tinted, Solid, Transparent }

enum class WidgetTheme { App, Light, Dark }

/** Which tasks due later the widget shows: [days] ahead of today, or every one (null). Undated tasks always show. */
enum class Upcoming(
    val days: Long?,
) {
    All(null),
    Week(7),
    None(0),
}

/** One placed Tasks widget's settings, kept in its Glance state (each widget has its own). */
data class TasksWidgetSettings(
    /** The list shown; null for the first. */
    val list: String? = null,
    /** Due date first by default: what's next is on top. */
    val sort: Sort = Sort.Date,
    val showDates: Boolean = true,
    val showName: Boolean = true,
    val upcoming: Upcoming = Upcoming.All,
    val background: WidgetBackground = WidgetBackground.Tinted,
    /** 0–100, of the background only. */
    val opacity: Int = 100,
    val theme: WidgetTheme = WidgetTheme.App,
) {
    fun write(prefs: MutablePreferences) {
        list?.let { prefs[LIST] = it } ?: prefs.remove(LIST)
        prefs[SORT] = sort.name
        prefs[DATES] = showDates
        prefs[NAME] = showName
        prefs[UPCOMING] = upcoming.name
        prefs[BACKGROUND] = background.name
        prefs[OPACITY] = opacity
        prefs[THEME] = theme.name
    }

    /** The colours: the theme (or the Tessera apps' theme), and the background with its opacity. */
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
        /** The key the widget has always kept its list under. */
        val LIST = stringPreferencesKey("list")
        private val SORT = stringPreferencesKey("sort")
        private val DATES = booleanPreferencesKey("show_dates")
        private val NAME = booleanPreferencesKey("show_name")
        private val UPCOMING = stringPreferencesKey("upcoming")
        private val BACKGROUND = stringPreferencesKey("background")
        private val OPACITY = intPreferencesKey("opacity")
        private val THEME = stringPreferencesKey("theme")

        fun from(prefs: Preferences?): TasksWidgetSettings {
            if (prefs == null) return TasksWidgetSettings()
            return TasksWidgetSettings(
                list = prefs[LIST],
                sort = Sort.entries.firstOrNull { it.name == prefs[SORT] } ?: Sort.Date,
                showDates = prefs[DATES] ?: true,
                showName = prefs[NAME] ?: true,
                upcoming = Upcoming.entries.firstOrNull { it.name == prefs[UPCOMING] } ?: Upcoming.All,
                background = WidgetBackground.entries.firstOrNull { it.name == prefs[BACKGROUND] } ?: WidgetBackground.Tinted,
                opacity = (prefs[OPACITY] ?: 100).coerceIn(0, 100),
                theme = WidgetTheme.entries.firstOrNull { it.name == prefs[THEME] } ?: WidgetTheme.App,
            )
        }
    }
}
