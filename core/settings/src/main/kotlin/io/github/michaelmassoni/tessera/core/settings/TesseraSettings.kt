package io.github.michaelmassoni.tessera.core.settings

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appearance settings for one app, persisted in device-protected storage so they can be read
 * before the first unlock (Clock shows its ringing screen then). One instance per process; get it
 * with [TesseraSettings.get].
 *
 * Changes made by the user go through [setTheme] / [setSyncAll], which stamp them and share them
 * with the other Tessera apps when syncing is on. Changes from other apps arrive through
 * [SharedSettingsProvider] and [receive].
 */
class TesseraSettings internal constructor(
    private val prefs: SharedPreferences,
    private val nightMode: (ThemeMode) -> Unit,
    private val peers: Peers,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _shared = MutableStateFlow(readShared())
    val shared: StateFlow<SharedState> = _shared.asStateFlow()

    private val _icon = MutableStateFlow(readIcon())
    val icon: StateFlow<IconSettings> = _icon.asStateFlow()

    fun setTheme(theme: ThemeSettings) = userChange(_shared.value.copy(theme = theme))

    fun setSyncAll(enabled: Boolean) = userChange(_shared.value.copy(syncAll = enabled))

    private fun userChange(next: SharedState) {
        val previous = _shared.value
        // Never go backwards in time, even if the clock was set back since the last change.
        val stamped = next.copy(updatedAt = maxOf(now(), previous.updatedAt + 1))
        store(stamped)
        // Turning syncing off is shared too, so every app stops at the same time.
        if (stamped.syncAll || previous.syncAll) peers.pushAll(stamped)
    }

    /** A state sent by another Tessera app. @return true if it was applied. */
    fun receive(remote: SharedState): Boolean {
        val next = SyncRules.receive(_shared.value, remote) ?: return false
        store(next)
        return true
    }

    /**
     * Whether to ask the other apps for their theme at start-up. Changes are pushed to every app
     * when they happen, so this is only needed once per installed version: the first launch (the
     * app may have been installed after the theme was chosen), and after an update (a push may
     * have arrived mid-install).
     * Asking starts every other Tessera app's process, so it isn't done on every launch.
     */
    fun needsPull(versionCode: Long): Boolean = prefs.getLong(KEY_PULLED_VERSION, -1) != versionCode

    /** Adopt the shared theme of the other apps, if they have one. Blocking: call off the main thread. */
    fun pullFromPeers(versionCode: Long = -1) {
        SyncRules.pull(_shared.value, peers.readAll())?.let(::store)
        prefs.edit { putLong(KEY_PULLED_VERSION, versionCode) }
    }

    fun setIcon(icon: IconSettings) {
        prefs.edit {
            putString(KEY_ICON_STYLE, icon.style.name)
            putInt(KEY_ICON_FG, icon.foreground)
            putInt(KEY_ICON_BG, icon.background)
            putBoolean(KEY_ICON_CHOSEN, icon.colorsChosen)
        }
        _icon.value = icon
    }

    private fun store(state: SharedState) {
        val modeChanged = state.theme.mode != _shared.value.theme.mode
        prefs.edit {
            putString(KEY_MODE, state.theme.mode.name)
            putBoolean(KEY_DYNAMIC, state.theme.dynamicColor)
            putBoolean(KEY_BLACK, state.theme.pureBlack)
            putBoolean(KEY_SYNC, state.syncAll)
            putLong(KEY_UPDATED, state.updatedAt)
        }
        _shared.value = state
        if (modeChanged) nightMode(state.theme.mode)
    }

    private fun readShared() =
        SharedState(
            theme =
                ThemeSettings(
                    mode = enumOr(prefs.getString(KEY_MODE, null), ThemeMode.System),
                    dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
                    pureBlack = prefs.getBoolean(KEY_BLACK, false),
                ),
            syncAll = prefs.getBoolean(KEY_SYNC, false),
            updatedAt = prefs.getLong(KEY_UPDATED, 0),
        )

    private fun readIcon() =
        IconSettings(
            style = enumOr(prefs.getString(KEY_ICON_STYLE, null), IconStyle.Default),
            foreground = prefs.getInt(KEY_ICON_FG, IconSettings.DEFAULT_FOREGROUND),
            background = prefs.getInt(KEY_ICON_BG, IconSettings.DEFAULT_BACKGROUND),
            colorsChosen = prefs.getBoolean(KEY_ICON_CHOSEN, false),
        )

    /** The other installed Tessera apps. */
    interface Peers {
        fun readAll(): List<SharedState>

        fun pushAll(state: SharedState)
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_BLACK = "pure_black"
        private const val KEY_SYNC = "sync_all"
        private const val KEY_UPDATED = "updated_at"
        private const val KEY_PULLED_VERSION = "pulled_version"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_ICON_FG = "icon_foreground"
        private const val KEY_ICON_BG = "icon_background"
        private const val KEY_ICON_CHOSEN = "icon_colors_chosen"

        @Volatile private var instance: TesseraSettings? = null

        fun get(context: Context): TesseraSettings =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(app: Context): TesseraSettings {
            val prefs =
                app.createDeviceProtectedStorageContext().getSharedPreferences("tessera_settings", Context.MODE_PRIVATE)
            val uiMode = app.getSystemService(UiModeManager::class.java)
            return TesseraSettings(
                prefs = prefs,
                nightMode = { mode -> uiMode?.setApplicationNightMode(mode.toUiMode()) },
                peers = ProviderPeers(app),
            )
        }

        internal fun ThemeMode.toUiMode() =
            when (this) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            }

        private inline fun <reified E : Enum<E>> enumOr(
            name: String?,
            default: E,
        ): E = enumValues<E>().firstOrNull { it.name == name } ?: default

        // Bundle encoding used between apps. Unknown or missing fields fall back to defaults,
        // so older and newer app versions can talk to each other.
        internal fun SharedState.toBundle() =
            Bundle().apply {
                putString(KEY_MODE, theme.mode.name)
                putBoolean(KEY_DYNAMIC, theme.dynamicColor)
                putBoolean(KEY_BLACK, theme.pureBlack)
                putBoolean(KEY_SYNC, syncAll)
                putLong(KEY_UPDATED, updatedAt)
            }

        internal fun Bundle.toSharedState() =
            SharedState(
                theme =
                    ThemeSettings(
                        mode = enumOr(getString(KEY_MODE), ThemeMode.System),
                        dynamicColor = getBoolean(KEY_DYNAMIC, true),
                        pureBlack = getBoolean(KEY_BLACK, false),
                    ),
                syncAll = getBoolean(KEY_SYNC, false),
                updatedAt = getLong(KEY_UPDATED, 0),
            )
    }
}
