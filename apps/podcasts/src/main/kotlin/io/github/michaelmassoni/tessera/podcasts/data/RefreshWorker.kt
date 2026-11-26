package io.github.michaelmassoni.tessera.podcasts.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.podcasts
import java.util.concurrent.TimeUnit

/** Every 6 hours: refresh the followed shows and download their new episodes (if Settings say so). */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val podcasts = applicationContext.podcasts
        val added = podcasts.refresh()
        autoDownload(applicationContext, added)
        NewEpisodeNotifier.notify(applicationContext, added, podcasts.store.library.value)
        return Result.success()
    }

    companion object {
        private const val NAME = "refresh"

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Queues downloads of [added] episodes of followed shows, when "Download new episodes" is on. */
        fun autoDownload(
            context: Context,
            added: List<Episode>,
        ) {
            if (!Prefs.autoDownload(context)) return
            added.forEach { Downloads.enqueue(context, it.id, manual = false) }
        }
    }
}
