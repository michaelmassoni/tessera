package io.github.michaelmassoni.tessera.tasks.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** One sync of every server list. Retried later when the server can't be reached. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val accounts = AccountStore(applicationContext)
            val account = accounts.account ?: return@withContext Result.success()
            val password = accounts.password() ?: return@withContext Result.success()
            try {
                Syncer(applicationContext.tasks, CalDavClient(account.username, password)).syncAll()
                accounts.lastSync = System.currentTimeMillis()
                accounts.lastProblem = null
                Result.success()
            } catch (e: SyncException) {
                accounts.lastProblem = e.problem.name
                if (e.problem == SyncProblem.Unreachable) Result.retry() else Result.failure()
            }
        }

    companion object {
        private const val PERIODIC = "sync-periodic"
        private const val SOON = "sync-soon"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes while there's an account (the shortest Android allows). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** A few seconds after a change (a burst of changes makes one sync), or now. */
        fun soon(
            context: Context,
            delaySeconds: Long = 5,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(network)
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(SOON)
        }
    }
}
