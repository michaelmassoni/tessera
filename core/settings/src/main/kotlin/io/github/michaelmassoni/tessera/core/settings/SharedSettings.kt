package io.github.michaelmassoni.tessera.core.settings

/** Light, dark, or follow the system. Applied per app through `UiModeManager.setApplicationNightMode`. */
enum class ThemeMode { System, Light, Dark }

/** Appearance shared by every screen of an app, and optionally by every Tessera app. */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    /** Material You colours from the wallpaper. Off uses the Tessera baseline palette. */
    val dynamicColor: Boolean = true,
    /** Pure black backgrounds in dark theme, for OLED screens. */
    val pureBlack: Boolean = false,
)

/**
 * The part of the settings that can be shared between Tessera apps.
 *
 * [updatedAt] is a wall-clock timestamp of the last change made by the user in any app; the
 * newest state wins when apps exchange settings. [syncAll] is itself shared, so switching
 * "same theme in all apps" on or off in one app does the same in every other.
 */
data class SharedState(
    val theme: ThemeSettings = ThemeSettings(),
    val syncAll: Boolean = false,
    val updatedAt: Long = 0,
)

/** How states from other apps are folded into this app's state. Pure, so it is unit tested. */
object SyncRules {
    /**
     * @return the new local state after receiving [remote], or null if nothing changes.
     *
     * - An older or equal change is ignored (last writer wins).
     * - `syncAll = true` adopts the remote theme and turns syncing on.
     * - `syncAll = false` only turns syncing off. Each app keeps the theme it has.
     */
    fun receive(
        local: SharedState,
        remote: SharedState,
    ): SharedState? {
        if (remote.updatedAt <= local.updatedAt) return null
        val next =
            if (remote.syncAll) {
                remote
            } else {
                local.copy(syncAll = false, updatedAt = remote.updatedAt)
            }
        return next.takeIf { it != local }
    }

    /**
     * On start, an app looks at every other Tessera app's state. Returns the state to adopt
     * (the newest one that has syncing on), or null to keep the local one. This is how an app
     * installed later joins an existing shared theme.
     */
    fun pull(
        local: SharedState,
        remotes: List<SharedState>,
    ): SharedState? {
        val newest = remotes.filter { it.syncAll }.maxByOrNull { it.updatedAt } ?: return null
        return receive(local, newest)
    }
}
