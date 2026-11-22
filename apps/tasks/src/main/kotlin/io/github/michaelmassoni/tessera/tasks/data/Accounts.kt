package io.github.michaelmassoni.tessera.tasks.data

import android.content.Context
import io.github.michaelmassoni.tessera.core.dav.Dav
import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.DavKind
import io.github.michaelmassoni.tessera.core.dav.DavSignIn
import io.github.michaelmassoni.tessera.core.dav.RemoteCollection
import io.github.michaelmassoni.tessera.core.dav.SignInError
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import io.github.michaelmassoni.tessera.tasks.tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Signing in to a CalDAV server, choosing its task lists, and signing out. */
object Accounts {
    /**
     * Checks [address] and the login, finds the calendar home, saves the account and returns the
     * server's task lists.
     */
    suspend fun signIn(
        context: Context,
        address: String,
        username: String,
        password: String,
    ): Result<List<RemoteCollection>> =
        withContext(Dispatchers.IO) {
            DavSignIn.discover(address, username, password, DavKind.CalDav).mapCatching { account ->
                val lists =
                    try {
                        taskLists(DavClient(account.username, password), account.home)
                    } catch (e: SyncException) {
                        throw SignInError(DavSignIn.problemOf(e.problem))
                    }
                AccountStore(context).save(account, password)
                lists
            }
        }

    private fun taskLists(
        client: DavClient,
        home: String,
    ) = client.calendars(home, "VTODO").map {
        RemoteCollection(it.href, it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'), Dav.color(it.color), it.writable != false)
    }

    /** The server's task lists now (for choosing more later). */
    suspend fun remoteLists(context: Context): List<RemoteCollection> =
        withContext(Dispatchers.IO) {
            val store = AccountStore(context)
            val account = store.account ?: return@withContext emptyList()
            taskLists(store.client() ?: return@withContext emptyList(), account.home)
        }

    /**
     * Syncs [chosen] server lists (adding any not here yet) and syncs now. An empty list that was
     * only ever on this phone ("My Tasks") is dropped once there are server lists.
     */
    fun useLists(
        context: Context,
        chosen: List<RemoteCollection>,
    ) {
        context.tasks.update { d ->
            val have = d.lists.mapNotNull { it.href }.toSet()
            val added = chosen.filter { it.href !in have }.map { TaskList(UUID.randomUUID().toString(), it.name, it.color, it.href) }
            val lists = d.lists + added
            val keep = lists.filter { l -> l.synced || d.tasksOf(l.id).isNotEmpty() || lists.none { it.synced } }
            d.copy(lists = keep.mapIndexed { i, l -> l.copy(order = i) })
        }
        SyncWorker.schedule.apply(context)
        SyncWorker.schedule.soon(context, delaySeconds = 0)
    }

    /** Makes a list on the server. */
    suspend fun createRemoteList(
        context: Context,
        name: String,
    ): TaskList? =
        withContext(Dispatchers.IO) {
            val store = AccountStore(context)
            val account = store.account ?: return@withContext null
            val client = store.client() ?: return@withContext null
            val id = UUID.randomUUID().toString()
            runCatching { TaskList(id, name, href = client.makeCalendar(account.home, id, name, "VTODO")) }.getOrNull()
        }

    /** Signs out; the server's lists stay on this phone as phone-only lists. */
    fun signOut(context: Context) {
        AccountStore(context).signOut()
        SyncWorker.schedule.cancel(context)
        context.tasks.update { d ->
            d.copy(
                lists = d.lists.map { it.copy(href = null, syncToken = null) },
                tasks = d.tasks.filterNot { it.deleted }.map { it.copy(href = null, etag = null, ical = null, dirty = false) },
            )
        }
    }
}
