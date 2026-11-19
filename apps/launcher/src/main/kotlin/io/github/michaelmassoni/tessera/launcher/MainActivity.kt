package io.github.michaelmassoni.tessera.launcher

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.launcher.data.AppRepository
import io.github.michaelmassoni.tessera.launcher.engine.App
import io.github.michaelmassoni.tessera.launcher.engine.AppIndex
import io.github.michaelmassoni.tessera.launcher.engine.Pins
import io.github.michaelmassoni.tessera.launcher.ui.LauncherActions
import io.github.michaelmassoni.tessera.launcher.ui.LauncherScreen
import io.github.michaelmassoni.tessera.launcher.ui.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val repository by lazy { AppRepository(this) }
    private var state by mutableStateOf(LauncherState())
    private var pins = Pins()
    private var callback: LauncherApps.Callback? = null
    private var wallpaperListener: WallpaperManager.OnColorsChangedListener? = null

    private val requestRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshDefault() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pins = Pins.parse(prefs().getString(KEY_PINS, null))
        callback = repository.watch { load() }
        val wallpaper = getSystemService(WallpaperManager::class.java)
        wallpaperListener =
            WallpaperManager
                .OnColorsChangedListener { colors, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) state = state.copy(darkText = darkText(colors))
                }.also { wallpaper.addOnColorsChangedListener(it, Handler(Looper.getMainLooper())) }
        state = state.copy(darkText = darkText(wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)))
        val actions = Actions()
        setTesseraContent { LauncherScreen(state, actions) }
        load()
    }

    override fun onResume() {
        super.onResume()
        refreshDefault()
    }

    override fun onDestroy() {
        callback?.let(repository::unwatch)
        wallpaperListener?.let { getSystemService(WallpaperManager::class.java).removeOnColorsChangedListener(it) }
        super.onDestroy()
    }

    private fun darkText(colors: WallpaperColors?) = colors != null && colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0

    private fun refreshDefault() {
        val held = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        if (held != state.isDefault) state = state.copy(isDefault = held)
    }

    private fun load() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { AppIndex.sorted(repository.apps(), resources.configuration.locales[0]) }
            pins = pins.retain(apps.map { it.key }.toSet()).takeIf { apps.isNotEmpty() } ?: pins
            savePins()
            state = state.copy(apps = apps, pinned = pinned(apps))
        }
    }

    private fun pinned(apps: List<App>): List<App> {
        val byKey = apps.associateBy { it.key }
        return pins.keys.mapNotNull { byKey[it] }
    }

    private fun prefs() = getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun savePins() = prefs().edit { putString(KEY_PINS, pins.serialize()) }

    private fun updatePins(next: Pins) {
        pins = next
        savePins()
        state = state.copy(pinned = pinned(state.apps))
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No clock or calendar app: nothing to open.
        }
    }

    private inner class Actions : LauncherActions {
        override fun launch(app: App) = repository.launch(app.key, null)

        override fun icon(app: App): Pair<Drawable?, Boolean> = repository.icon(app.key, themedIcons)

        override fun shortcuts(app: App): List<ShortcutInfo> = repository.shortcuts(app.key)

        override fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = repository.shortcutIcon(shortcut)

        override fun startShortcut(shortcut: ShortcutInfo) = repository.startShortcut(shortcut)

        override fun pin(app: App) = updatePins(pins.add(app.key))

        override fun unpin(app: App) = updatePins(pins.remove(app.key))

        override fun move(
            app: App,
            delta: Int,
        ) = updatePins(pins.move(app.key, delta))

        override fun appInfo(app: App) = repository.appInfo(app.key)

        override fun uninstall(app: App) = repository.uninstall(app.key)

        override fun openClock() = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))

        override fun openCalendar() =
            start(
                Intent(Intent.ACTION_VIEW).setData(
                    CalendarContract.CONTENT_URI
                        .buildUpon()
                        .appendPath("time")
                        .appendPath(System.currentTimeMillis().toString())
                        .build(),
                ),
            )

        override fun makeDefault() {
            getSystemService(RoleManager::class.java)?.let { requestRole.launch(it.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }

    private companion object {
        const val KEY_PINS = "pins"
    }
}

/** Draw apps that have a monochrome icon layer in theme colours (on by default). */
var Context.themedIcons: Boolean
    get() = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("themed_icons", true)
    set(value) = getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("themed_icons", value) }
