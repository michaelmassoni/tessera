package io.github.michaelmassoni.tessera.core.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap

/**
 * What an app tells `core:settings` about its launcher icon.
 *
 * @param glyph the icon's foreground: a 108dp adaptive-icon layer drawn in white, the same
 *   drawable the launcher icon and the monochrome (themed) layer use.
 * @param colors background and foreground colour resources of each built-in style. They must be
 *   the colours of the matching `mipmap/ic_launcher_*` icons, so the previews match.
 */
data class LauncherIcon(
    @param:DrawableRes val glyph: Int,
    val colors: Map<IconStyle, Pair<Int, Int>>,
) {
    companion object {
        fun of(
            @DrawableRes glyph: Int,
            @ColorRes defaultBackground: Int,
            @ColorRes lightBackground: Int,
            @ColorRes lightForeground: Int,
            @ColorRes darkBackground: Int,
            @ColorRes darkForeground: Int,
            @ColorRes defaultForeground: Int = android.R.color.white,
        ) = LauncherIcon(
            glyph,
            mapOf(
                IconStyle.Default to (defaultBackground to defaultForeground),
                IconStyle.Light to (lightBackground to lightForeground),
                IconStyle.Dark to (darkBackground to darkForeground),
            ),
        )
    }
}

/** Switches the launcher icon between the built-in styles and pins custom-colour shortcuts. */
class AppIcons(
    private val context: Context,
    private val icon: LauncherIcon,
) {
    enum class PinResult { Requested, Updated, Unsupported }

    /**
     * Asks the launcher to pin a home-screen icon in [foreground] on [background], or updates the
     * one already pinned. The system shows its own confirmation.
     */
    fun pinCustom(
        foreground: Int,
        background: Int,
        label: CharSequence,
    ): PinResult {
        val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return PinResult.Unsupported
        val launch =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                // MainActivity rather than an alias, so the shortcut keeps working whichever
                // built-in icon is enabled. Shortcuts launch as this app, so it needn't be exported.
                .setClassName(context.packageName, "${context.packageName}.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val info =
            ShortcutInfo
                .Builder(context, SHORTCUT_ID)
                .setShortLabel(label)
                .setIcon(Icon.createWithAdaptiveBitmap(render(foreground, background)))
                .setIntent(launch)
                .build()
        if (shortcuts.pinnedShortcuts.any { it.id == SHORTCUT_ID }) {
            shortcuts.updateShortcuts(listOf(info))
            return PinResult.Updated
        }
        if (!shortcuts.isRequestPinShortcutSupported) return PinResult.Unsupported
        shortcuts.requestPinShortcut(info, null)
        return PinResult.Requested
    }

    /** True if a custom icon is on the home screen. */
    fun hasPinnedCustom(): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.pinnedShortcuts?.any { it.id == SHORTCUT_ID } == true

    /** A full adaptive-icon bitmap (108dp square; the launcher masks it to its shape). */
    private fun render(
        foreground: Int,
        background: Int,
    ): Bitmap {
        val size = (ADAPTIVE_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        ContextCompat.getDrawable(context, icon.glyph)?.mutate()?.apply {
            setTint(foreground)
            setBounds(0, 0, size, size)
            draw(canvas)
        }
        return bitmap
    }

    private companion object {
        const val SHORTCUT_ID = "tessera_custom_icon"
        const val ADAPTIVE_DP = 108
    }
}

/**
 * Enables the launcher alias for [style] and disables the others.
 *
 * Disabling the alias a task was started from makes Android close that task, so this runs only
 * when the app goes to the background (see [setTesseraContent]), never while the user is in it.
 * Launchers update within a few seconds; some drop the icon from the home screen.
 */
internal fun applyLauncherAlias(
    context: Context,
    style: IconStyle,
) {
    val pm = context.packageManager
    // Enable the new alias first so the app always has a launcher entry.
    LauncherAliases.states(context.packageName, style).sortedByDescending { it.second }.forEach { (name, enabled) ->
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = ComponentName(context.packageName, name)
        if (pm.getComponentEnabledSetting(component) != state) {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
