package io.github.michaelmassoni.tessera.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.launcher.engine.App
import io.github.michaelmassoni.tessera.launcher.engine.AppKey

/** Launchable apps across profiles, their icons, shortcuts, and launching. Blocking calls run off the main thread. */
class AppRepository(
    private val context: Context,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val infos = mutableMapOf<AppKey, LauncherActivityInfo>()

    fun apps(): List<App> {
        val me = Process.myUserHandle()
        val found = mutableMapOf<AppKey, LauncherActivityInfo>()
        val apps =
            launcherApps.profiles.flatMap { user ->
                // A paused (quiet-mode) work profile has no activities to list.
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
                    val key = AppKey(info.componentName.packageName, info.componentName.className, users.getSerialNumberForUser(user))
                    found[key] = info
                    App(key, info.label.toString(), work = user != me)
                }
            }
        synchronized(infos) {
            infos.clear()
            infos.putAll(found)
        }
        return apps
    }

    private fun info(key: AppKey): LauncherActivityInfo? = synchronized(infos) { infos[key] }

    private fun user(key: AppKey): UserHandle? = users.getUserForSerialNumber(key.userSerial)

    /**
     * The app's icon: badged for work apps; for personal apps, the monochrome layer when [themed]
     * and the app provides one (Android 13+), so the caller can tint it.
     */
    fun icon(
        key: AppKey,
        themed: Boolean,
    ): Pair<Drawable?, Boolean> {
        val info = info(key) ?: return null to false
        val density = context.resources.displayMetrics.densityDpi
        val isWork = user(key) != Process.myUserHandle()
        if (themed && !isWork && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val icon = info.getIcon(density)
            val mono = (icon as? AdaptiveIconDrawable)?.monochrome
            if (mono != null) return mono to true
        }
        return info.getBadgedIcon(density) to false
    }

    fun launch(
        key: AppKey,
        bounds: Rect?,
    ) {
        val user = user(key) ?: return
        runCatching { launcherApps.startMainActivity(ComponentName(key.packageName, key.activity), user, bounds, null) }
    }

    fun appInfo(key: AppKey) {
        val user = user(key) ?: return
        runCatching { launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.activity), user, null, null) }
    }

    fun uninstall(key: AppKey) {
        context.startActivity(Intent(Intent.ACTION_DELETE, "package:${key.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** App shortcuts; empty unless this app is the default home app. */
    fun shortcuts(key: AppKey): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val user = user(key) ?: return emptyList()
        val query =
            LauncherApps
                .ShortcutQuery()
                .setPackage(key.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(query, user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .take(MAX_SHORTCUTS)
    }

    fun startShortcut(shortcut: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(shortcut, null, null) }
    }

    fun shortcutIcon(shortcut: ShortcutInfo): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi) }.getOrNull()

    /** Calls [onChange] (on the main thread) when apps are installed, removed or updated. */
    fun watch(onChange: () -> Unit): LauncherApps.Callback {
        val callback =
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(
                    packageName: String,
                    user: UserHandle,
                ) = onChange()

                override fun onPackageAdded(
                    packageName: String,
                    user: UserHandle,
                ) = onChange()

                override fun onPackageChanged(
                    packageName: String,
                    user: UserHandle,
                ) = onChange()

                override fun onPackagesAvailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = onChange()

                override fun onPackagesUnavailable(
                    packageNames: Array<out String>,
                    user: UserHandle,
                    replacing: Boolean,
                ) = onChange()
            }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unwatch(callback: LauncherApps.Callback) = launcherApps.unregisterCallback(callback)

    fun isWorkKey(key: AppKey) = user(key) != Process.myUserHandle()

    private companion object {
        const val MAX_SHORTCUTS = 5
    }
}
