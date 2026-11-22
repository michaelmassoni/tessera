package io.github.michaelmassoni.tessera.core.dav

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** How often to sync, as DAVx5 offers. */
enum class SyncInterval(
    /** Minutes, or 0 for manually only. */
    val minutes: Long,
) {
    Manual(0),
    Min15(15),
    Min30(30),
    Hour1(60),
    Hour2(120),
    Hour4(240),
    Day(1440),
}

/** The sync settings an app keeps (docs/sync.md). */
data class SyncPrefs(
    val interval: SyncInterval = SyncInterval.Hour4,
    /** Sync a few seconds after a change on the phone. */
    val onLocalChange: Boolean = true,
    /** Only over unmetered networks (Wi-Fi). */
    val wifiOnly: Boolean = false,
) {
    companion object {
        private const val FILE = "sync_settings"

        fun load(context: Context): SyncPrefs {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return SyncPrefs(
                interval =
                    p.getString("interval", null)?.let { n -> SyncInterval.entries.firstOrNull { it.name == n } } ?: SyncInterval.Hour4,
                onLocalChange = p.getBoolean("on_change", true),
                wifiOnly = p.getBoolean("wifi_only", false),
            )
        }

        fun save(
            context: Context,
            prefs: SyncPrefs,
        ) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString("interval", prefs.interval.name)
            putBoolean("on_change", prefs.onLocalChange)
            putBoolean("wifi_only", prefs.wifiOnly)
        }
    }
}

/** Schedules an app's sync worker with WorkManager, following its [SyncPrefs]. */
class SyncSchedule(
    private val worker: Class<out ListenableWorker>,
) {
    private val periodic = "sync-periodic"
    private val soon = "sync-soon"

    private fun constraints(prefs: SyncPrefs) =
        Constraints.Builder().setRequiredNetworkType(if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()

    /** (Re)schedules the periodic sync after a change of settings or sign-in; none when manual. */
    fun apply(context: Context) {
        val prefs = SyncPrefs.load(context)
        val wm = WorkManager.getInstance(context)
        if (prefs.interval == SyncInterval.Manual) {
            wm.cancelUniqueWork(periodic)
            return
        }
        val request =
            PeriodicWorkRequest
                .Builder(worker, prefs.interval.minutes, TimeUnit.MINUTES)
                .setConstraints(constraints(prefs))
                .build()
        wm.enqueueUniquePeriodicWork(periodic, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** A sync [delaySeconds] from now (a burst of changes makes one sync). */
    fun soon(
        context: Context,
        delaySeconds: Long = 5,
    ) {
        val request =
            OneTimeWorkRequest
                .Builder(worker)
                .setConstraints(constraints(SyncPrefs.load(context)))
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(soon, ExistingWorkPolicy.REPLACE, request)
    }

    /** After a change on the phone: soon, if the person wants that. */
    fun afterLocalChange(context: Context) {
        if (SyncPrefs.load(context).onLocalChange) soon(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(periodic)
        WorkManager.getInstance(context).cancelUniqueWork(soon)
    }

    /** The name the "sync now" work runs under, to watch for progress. */
    val soonName: String get() = soon
}
