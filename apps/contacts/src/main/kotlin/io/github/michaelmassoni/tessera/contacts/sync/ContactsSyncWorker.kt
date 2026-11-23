package io.github.michaelmassoni.tessera.contacts.sync

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

/**
 * One sync of every synced address book. A pending change of group method is applied here: local
 * changes go up the old way, then each address book is read again from the server the new way.
 * Retried later when the server can't be reached.
 */
class ContactsSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val store = ContactsDavStore(applicationContext)
            val client = store.client() ?: return@withContext Result.success()
            try {
                lock.withLock {
                    val method = ContactsAccounts.groupMethod(applicationContext)
                    val next = ContactsAccounts.pendingGroupMethod(applicationContext)
                    for (account in ContactsAccounts.accounts(applicationContext)) {
                        ContactsSync(applicationContext, account, client, method).sync()
                        if (next != null) {
                            ContactsSync(applicationContext, account, client, next).apply {
                                wipe()
                                sync()
                            }
                        }
                    }
                    if (next != null) ContactsAccounts.completeGroupMethod(applicationContext, next)
                }
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
        val schedule = SyncSchedule(ContactsSyncWorker::class.java)
    }
}
