package io.github.michaelmassoni.tessera.launcher.ui

import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.launcher.engine.App
import io.github.michaelmassoni.tessera.launcher.engine.AppKey

private val apps =
    listOf("Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Music", "Notes", "Settings")
        .map { App(AppKey("p.${it.lowercase()}", "Main", 0), it) }

private object NoActions : LauncherActions {
    override fun launch(app: App) = Unit

    override fun icon(app: App): Pair<Drawable?, Boolean> = null to false

    override fun shortcuts(app: App): List<ShortcutInfo> = emptyList()

    override fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = null

    override fun startShortcut(shortcut: ShortcutInfo) = Unit

    override fun pin(app: App) = Unit

    override fun unpin(app: App) = Unit

    override fun move(
        app: App,
        delta: Int,
    ) = Unit

    override fun appInfo(app: App) = Unit

    override fun uninstall(app: App) = Unit

    override fun openClock() = Unit

    override fun openCalendar() = Unit

    override fun makeDefault() = Unit

    override fun openSettings() = Unit
}

@TesseraPreviews
@Composable
private fun DrawerPreview() {
    TesseraTheme(dynamicColor = false) {
        LauncherScreen(LauncherState(apps = apps, pinned = apps.take(4)), NoActions, drawerOpenInitially = true)
    }
}
