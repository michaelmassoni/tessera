package io.github.michaelmassoni.tessera.calendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.dav.SyncSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One sync of every synced calendar. Retried later when the server can't be reached. */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val store = CalendarDavStore(applicationContext)
            val account = CalendarAccounts.account(applicationContext) ?: return@withContext Result.success()
            val client = store.client() ?: return@withContext Result.success()
            try {
                // The periodic sync and "sync soon" never run at once.
                lock.withLock { CalendarSync(applicationContext, account, client).syncAll() }
                store.lastSync = System.currentTimeMillis()
                store.lastProblem = null
                Result.success()
            } catch (e: SyncException) {
                store.lastProblem = e.problem.name
                if (e.problem == SyncProblem.Unreachable) Result.retry() else Result.failure()
            }
        }

    companion object {
        private val lock = Mutex()

        /** Periodic (as Settings say), soon after changes, and now. */
        val schedule = SyncSchedule(CalendarSyncWorker::class.java)
    }
}
