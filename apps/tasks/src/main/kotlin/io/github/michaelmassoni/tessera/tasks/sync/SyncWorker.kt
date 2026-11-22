package io.github.michaelmassoni.tessera.tasks.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.dav.SyncSchedule
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One sync of every server list. Retried later when the server can't be reached. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val accounts = AccountStore(applicationContext)
            try {
                Syncer(applicationContext.tasks, accounts.client() ?: return@withContext Result.success()).syncAll()
                accounts.lastSync = System.currentTimeMillis()
                accounts.lastProblem = null
                Result.success()
            } catch (e: SyncException) {
                accounts.lastProblem = e.problem.name
                if (e.problem == SyncProblem.Unreachable) Result.retry() else Result.failure()
            }
        }

    companion object {
        /** Periodic (as Settings say), soon after changes, and now. */
        val schedule = SyncSchedule(SyncWorker::class.java)
    }
}
