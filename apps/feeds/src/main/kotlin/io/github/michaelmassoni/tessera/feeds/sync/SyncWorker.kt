package io.github.michaelmassoni.tessera.feeds.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.dav.SyncSchedule
import io.github.michaelmassoni.tessera.feeds.widget.LatestWidget

/** One sync or refresh. Retried later when the server or the network can't be reached. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val problem = Syncer(applicationContext).sync()
        LatestWidget.refresh(applicationContext)
        return when (problem) {
            null -> Result.success()
            SyncProblem.Unreachable -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        /** Periodic (as Settings say), soon after changes, and now. */
        val schedule = SyncSchedule(SyncWorker::class.java)
    }
}
