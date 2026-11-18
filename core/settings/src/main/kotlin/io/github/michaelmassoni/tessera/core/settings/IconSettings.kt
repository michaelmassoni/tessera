package io.github.michaelmassoni.tessera.core.settings

/**
 * Launcher icon styles. [Default], [Light] and [Dark] are icons built into the app and switched
 * with activity aliases. [Custom] can't be: Android has no API for an app to recolour its own
 * launcher icon. A custom icon is a pinned home-screen shortcut drawn in the chosen colours, and
 * the app drawer keeps the default icon. See docs/decisions.md (2026-11-19, app icons).
 */
enum class IconStyle { Default, Light, Dark, Custom }

data class IconSettings(
    val style: IconStyle = IconStyle.Default,
    /** ARGB. */
    val foreground: Int = DEFAULT_FOREGROUND,
    /** ARGB. */
    val background: Int = DEFAULT_BACKGROUND,
    /** False until the user picks colours; until then the app shows its own icon colours. */
    val colorsChosen: Boolean = false,
) {
    /** These colours, or the app's own icon colours if the user hasn't chosen any yet. */
    fun withAppColors(
        appForeground: Int,
        appBackground: Int,
    ) = if (colorsChosen) this else copy(foreground = appForeground, background = appBackground)

    companion object {
        const val DEFAULT_FOREGROUND: Int = 0xFFFFFFFF.toInt()
        const val DEFAULT_BACKGROUND: Int = 0xFF3E5F8A.toInt()
    }
}

/** The activity-alias names an app declares, one per built-in style (see the app's manifest). */
object LauncherAliases {
    fun className(
        packageName: String,
        style: IconStyle,
    ): String? =
        when (style) {
            IconStyle.Default, IconStyle.Custom -> "$packageName.LauncherDefault"
            IconStyle.Light -> "$packageName.LauncherLight"
            IconStyle.Dark -> "$packageName.LauncherDark"
        }

    /** All aliases, with whether each should be enabled for [style]. Exactly one is enabled. */
    fun states(
        packageName: String,
        style: IconStyle,
    ): List<Pair<String, Boolean>> {
        val enabled = className(packageName, style)
        return listOf(IconStyle.Default, IconStyle.Light, IconStyle.Dark).map {
            val name = className(packageName, it)!!
            name to (name == enabled)
        }
    }
}
