package io.github.michaelmassoni.tessera.tasks.data

import android.content.Context
import io.github.michaelmassoni.tessera.core.settings.ServerAddress
import io.github.michaelmassoni.tessera.tasks.engine.Dav
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.sync.CalDavClient
import io.github.michaelmassoni.tessera.tasks.sync.SyncException
import io.github.michaelmassoni.tessera.tasks.sync.SyncProblem
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import io.github.michaelmassoni.tessera.tasks.tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** A task list on the server, offered when signing in. */
data class RemoteList(
    val href: String,
    val name: String,
    val color: Int?,
)

/** What went wrong signing in, for the person. */
enum class SignInProblem { BadAddress, Insecure, Unreachable, WrongLogin, NotCalDav, Other }

/** Signing in to a CalDAV server, choosing its lists, and signing out. */
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
    ): Result<List<RemoteList>> =
        withContext(Dispatchers.IO) {
            try {
                val base = ServerAddress.normalize(address)
                val client = CalDavClient(username.trim(), password)
                val home = client.findHome(base)
                val lists =
                    client.taskCollections(home).map {
                        RemoteList(
                            it.href,
                            it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'),
                            Dav.color(it.color),
                        )
                    }
                AccountStore(context).save(CalDavAccount(base, username.trim(), home), password)
                Result.success(lists)
            } catch (e: ServerAddress.Invalid) {
                Result.failure(
                    SignInError(
                        if (e.problem ==
                            ServerAddress.Problem.Insecure
                        ) {
                            SignInProblem.Insecure
                        } else {
                            SignInProblem.BadAddress
                        },
                    ),
                )
            } catch (e: SyncException) {
                Result.failure(
                    SignInError(
                        when (e.problem) {
                            SyncProblem.Unreachable -> SignInProblem.Unreachable
                            SyncProblem.WrongLogin -> SignInProblem.WrongLogin
                            SyncProblem.NotCalDav -> SignInProblem.NotCalDav
                            SyncProblem.Other -> SignInProblem.Other
                        },
                    ),
                )
            }
        }

    class SignInError(
        val problem: SignInProblem,
    ) : Exception(problem.name)

    /** The server's task lists now (for choosing more later). */
    suspend fun remoteLists(context: Context): List<RemoteList> =
        withContext(Dispatchers.IO) {
            val store = AccountStore(context)
            val account = store.account ?: return@withContext emptyList()
            val password = store.password() ?: return@withContext emptyList()
            CalDavClient(account.username, password).taskCollections(account.home).map {
                RemoteList(it.href, it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'), Dav.color(it.color))
            }
        }

    /**
     * Syncs [chosen] server lists (adding any not here yet) and syncs now. An empty list that was
     * only ever on this phone ("My Tasks") is dropped once there are server lists.
     */
    fun useLists(
        context: Context,
        chosen: List<RemoteList>,
    ) {
        context.tasks.update { d ->
            val have = d.lists.mapNotNull { it.href }.toSet()
            val added = chosen.filter { it.href !in have }.map { TaskList(UUID.randomUUID().toString(), it.name, it.color, it.href) }
            val lists = d.lists + added
            val keep = lists.filter { l -> l.synced || d.tasksOf(l.id).isNotEmpty() || lists.none { it.synced } }
            d.copy(lists = keep.mapIndexed { i, l -> l.copy(order = i) })
        }
        SyncWorker.schedule(context)
        SyncWorker.soon(context, delaySeconds = 0)
    }

    /** Makes a list on the server. */
    suspend fun createRemoteList(
        context: Context,
        name: String,
    ): TaskList? =
        withContext(Dispatchers.IO) {
            val store = AccountStore(context)
            val account = store.account ?: return@withContext null
            val password = store.password() ?: return@withContext null
            val id = UUID.randomUUID().toString()
            runCatching {
                val href = CalDavClient(account.username, password).makeList(account.home, id, name)
                TaskList(id, name, href = href)
            }.getOrNull()
        }

    /** Signs out; the server's lists stay on this phone as phone-only lists. */
    fun signOut(context: Context) {
        AccountStore(context).signOut()
        SyncWorker.cancel(context)
        context.tasks.update { d ->
            d.copy(
                lists = d.lists.map { it.copy(href = null, syncToken = null) },
                tasks = d.tasks.filterNot { it.deleted }.map { it.copy(href = null, etag = null, ical = null, dirty = false) },
            )
        }
    }
}
