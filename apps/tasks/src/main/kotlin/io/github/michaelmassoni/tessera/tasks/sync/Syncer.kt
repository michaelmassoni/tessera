package io.github.michaelmassoni.tessera.tasks.sync

import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.DavResponse
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.data.TaskStore
import io.github.michaelmassoni.tessera.tasks.engine.Merge
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.VTodo
import java.net.URLEncoder
import java.time.ZoneId

/**
 * Syncs the store's server lists with their CalDAV collections: this phone's changes go up, the
 * server's come down, and a task changed on both sides is merged field by field ([Merge]).
 */
class Syncer(
    private val store: TaskStore,
    private val client: DavClient,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun syncAll() {
        store.data.value.lists
            .filter { it.synced }
            .forEach { sync(it.id) }
    }

    fun sync(listId: String) {
        push(listId)
        pull(listId)
        // A merge leaves this phone's side of a conflict to send.
        push(listId)
    }

    private fun collectionOf(listId: String) =
        store.data.value
            .list(listId)
            ?.href

    private fun push(listId: String) {
        val collection = collectionOf(listId) ?: return
        val pending =
            store.data.value.tasks
                .filter { it.listId == listId && (it.dirty || it.deleted) }
        for (t in pending) {
            if (t.deleted) {
                t.href?.let { href ->
                    // Deleting wins over an edit made elsewhere meanwhile.
                    if (client.delete(href, t.etag).code == 412) client.delete(href, null)
                }
                store.update { d -> d.copy(tasks = d.tasks.filterNot { it.id == t.id && it.listId == listId && it.deleted }) }
                continue
            }
            // A task moved here from another list is created here, then removed there.
            val moved = t.href != null && !t.href.startsWith(collection)
            val href = if (t.href == null || moved) collection.trimEnd('/') + "/" + URLEncoder.encode(t.id, "UTF-8") + ".ics" else t.href
            val body = VTodo.write(t, now(), zone())
            val reply = client.put(href, body, if (t.href == null || moved) null else t.etag)
            when {
                reply.code in 200..299 -> {
                    if (moved) runCatching { client.delete(t.href, t.etag) }
                    store.update { d ->
                        d.replace(t.id) { current ->
                            current.copy(
                                href = href,
                                etag = reply.etag,
                                ical = body,
                                dirty =
                                    current != t,
                            )
                        }
                    }
                }

                // Changed on the server since we last saw it (or a task with this id is already there):
                // the pull fetches it and merges.
                reply.code == 412 -> {
                    store.update { d -> d.replace(t.id) { it.copy(href = href) } }
                }

                else -> {
                    throw SyncException(SyncProblem.Other, "PUT ${reply.code}")
                }
            }
        }
    }

    private fun pull(listId: String) {
        val list = store.data.value.list(listId) ?: return
        val collection = list.href ?: return
        // What changed since the last sync; with no usable token, every task and its etag.
        var changes = client.changes(collection, list.syncToken)
        var full = list.syncToken == null
        if (changes == null && list.syncToken != null) {
            // The server no longer knows the token: start again from nothing.
            changes = client.changes(collection, null)
            full = true
        }
        if (changes == null) full = true
        val present: List<DavResponse> = changes?.responses?.filter { it.status in 200..299 } ?: client.etags(collection)
        val gone =
            changes
                ?.responses
                ?.filter { it.status == 404 }
                ?.map { it.href }
                .orEmpty()
        val known =
            store.data.value.tasks
                .filter { it.listId == listId }
                .associateBy { it.href }
        val toFetch = present.filter { r -> r.etag == null || known[r.href]?.etag != r.etag }.map { it.href }
        val fetched = if (toFetch.isEmpty()) emptyList() else client.fetch(collection, toFetch)
        val serverHrefs = present.map { it.href }.toSet()
        store.update { d ->
            var tasks = d.tasks
            for (r in fetched) {
                val text = r.data ?: continue
                val remote = VTodo.read(text, listId, zone())?.copy(href = r.href, etag = r.etag) ?: continue
                val local = tasks.firstOrNull { it.href == r.href } ?: tasks.firstOrNull { it.id == remote.id && it.listId == listId }
                tasks =
                    when {
                        local == null -> {
                            tasks + remote
                        }

                        // Deleted here: the next push removes it from the server.
                        local.deleted -> {
                            tasks
                        }

                        local.dirty -> {
                            val base = local.ical?.let { VTodo.read(it, listId, zone()) }
                            tasks.map { if (it === local) Merge.merge(base, local, remote).copy(id = local.id) else it }
                        }

                        else -> {
                            tasks.map { if (it === local) remote.copy(id = local.id, snoozedUntil = local.snoozedUntil) else it }
                        }
                    }
            }

            // Deleted on the server: gone here too, unless changed here since (then it's made again).
            fun removedOnServer(t: Task) = t.listId == listId && t.href != null && (t.href in gone || (full && t.href !in serverHrefs))
            tasks =
                tasks.mapNotNull { t ->
                    when {
                        !removedOnServer(t) -> t
                        t.dirty && !t.deleted -> t.copy(href = null, etag = null, ical = null)
                        else -> null
                    }
                }
            d.copy(tasks = tasks, lists = d.lists.map { if (it.id == listId) it.copy(syncToken = changes?.syncToken) else it })
        }
    }

    private fun TaskData.replace(
        id: String,
        change: (Task) -> Task,
    ) = copy(tasks = tasks.map { if (it.id == id) change(it) else it })
}
