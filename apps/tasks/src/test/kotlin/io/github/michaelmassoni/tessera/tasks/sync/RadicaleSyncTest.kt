package io.github.michaelmassoni.tessera.tasks.sync

import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.data.TaskStore
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import io.github.michaelmassoni.tessera.tasks.engine.VTodo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.time.ZoneId
import java.util.UUID

/**
 * Sync against a real Radicale (what OpenCloud serves CalDAV with). Skipped unless RADICALE_URL
 * (e.g. http://127.0.0.1:5232) names a server with user tester / sesame-test.
 */
class RadicaleSyncTest {
    private val url = System.getenv("RADICALE_URL")
    private val zone = ZoneId.of("Australia/Melbourne")

    @Test
    fun `round trips with a real server`() {
        assumeTrue("RADICALE_URL not set", url != null)
        val client = CalDavClient("tester", "sesame-test")
        val home = client.findHome(url!!)
        assertTrue(home, home.endsWith("/tester/"))

        // A new list on the server, and another client's task in it.
        val id = UUID.randomUUID().toString()
        val collection = client.makeList(home, id, "Integration")
        assertTrue(client.taskCollections(home).any { it.href == collection && it.displayName == "Integration" })
        val theirs = Task("their-task", "x", "From Tasks.org", due = Due("2026-11-23", "08:00"), repeat = Repeat(Repeat.Frequency.Daily))
        assertEquals(201, client.put("${collection}their-task.ics", VTodo.write(theirs, 0, zone), null).code)

        val file = Files.createTempFile("tasks", ".json").toFile().apply { delete() }
        val store = TaskStore(file, CoroutineScope(Dispatchers.Unconfined))
        store.update { TaskData(lists = listOf(TaskList("L", "Integration", href = collection))) }
        val syncer = Syncer(store, client, { zone })

        // Pull: the other client's task arrives.
        syncer.sync("L")
        val pulled = store.data.value.task("their-task")
        assertNotNull(pulled)
        assertEquals("From Tasks.org", pulled!!.title)
        assertFalse(pulled.dirty)

        // Push: a new task goes up.
        store.update { d -> d.copy(tasks = d.tasks + Task("mine", "L", "Buy milk", createdAt = 1)) }
        syncer.sync("L")
        assertEquals(2, client.etags(collection).size)
        assertFalse(
            store.data.value
                .task("mine")!!
                .dirty,
        )

        // Both sides change the same task: each keeps its field.
        val serverCopy = store.data.value.task("mine")!!
        client.put(serverCopy.href!!, VTodo.write(serverCopy.copy(notes = "Oat"), 2, zone), serverCopy.etag)
        store.update { d -> d.copy(tasks = d.tasks.map { if (it.id == "mine") it.copy(title = "Buy milk!", dirty = true) else it }) }
        syncer.sync("L")
        val merged = store.data.value.task("mine")!!
        assertEquals("Buy milk!", merged.title)
        assertEquals("Oat", merged.notes)
        assertFalse(merged.dirty)
        val onServer = client.fetch(collection, listOf(merged.href!!)).single().calendarData!!
        assertTrue(onServer, onServer.contains("SUMMARY:Buy milk!") && onServer.contains("DESCRIPTION:Oat"))

        // Ticking the daily task here moves it on the server.
        store.update { d ->
            d.copy(
                tasks =
                    d.tasks.map {
                        if (it.id ==
                            "their-task"
                        ) {
                            TaskOps.complete(it, 1_795_400_000_000, zone)
                        } else {
                            it
                        }
                    },
            )
        }
        syncer.sync("L")
        val moved =
            client
                .fetch(
                    collection,
                    listOf(
                        store.data.value
                            .task("their-task")!!
                            .href!!,
                    ),
                ).single()
                .calendarData!!
        assertTrue(moved, moved.contains("DUE:20261124T080000") && moved.contains("STATUS:NEEDS-ACTION"))

        // Deleted here: gone there. Deleted there: gone here.
        store.update { d -> d.copy(tasks = d.tasks.map { if (it.id == "mine") it.copy(deleted = true) else it }) }
        syncer.sync("L")
        assertEquals(1, client.etags(collection).size)
        val their = store.data.value.task("their-task")!!
        client.delete(their.href!!, null)
        syncer.sync("L")
        assertTrue(
            store.data.value.tasks
                .isEmpty(),
        )

        client.deleteList(collection)
        file.delete()
    }
}
