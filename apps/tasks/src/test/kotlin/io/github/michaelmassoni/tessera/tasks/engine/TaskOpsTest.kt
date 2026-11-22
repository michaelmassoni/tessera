package io.github.michaelmassoni.tessera.tasks.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskOpsTest {
    private val zone = ZoneId.of("Australia/Melbourne")
    private val now =
        LocalDateTime
            .parse("2026-11-23T08:05")
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun task(
        id: String,
        order: Long = 0,
        parent: String? = null,
        done: Long? = null,
        due: Due? = null,
        starred: Boolean = false,
    ) = Task(id, "l", id, parentId = parent, completedAt = done, order = order, due = due, starred = starred, dirty = false)

    @Test
    fun `ticking a daily medication moves it to tomorrow`() {
        val meds = task("meds", due = Due("2026-11-23", "08:00")).copy(repeat = Repeat(Repeat.Frequency.Daily))
        val ticked = TaskOps.complete(meds, now, zone)
        assertNull(ticked.completedAt)
        assertEquals(Due("2026-11-24", "08:00"), ticked.due)
        assertTrue(ticked.dirty)
        val once = TaskOps.complete(task("milk"), now, zone)
        assertEquals(now, once.completedAt)
    }

    @Test
    fun `subtasks follow their parent, completed are flat and newest first`() {
        val tasks =
            listOf(
                task("b", order = 2),
                task("a", order = 1),
                task("a2", order = 2, parent = "a"),
                task("a1", order = 1, parent = "a"),
                task("orphan", order = 3, parent = "done"),
                task("done", done = 100),
                task("done2", done = 200),
            )
        val v = TaskOps.view(tasks, Sort.MyOrder)
        assertEquals(listOf("a", "a1", "a2", "b", "orphan"), v.open.map { it.task.id })
        assertEquals(listOf(0, 1, 1, 0, 0), v.open.map { it.depth })
        assertEquals(listOf("done2", "done"), v.completed.map { it.id })
    }

    @Test
    fun `date sort puts undated last and starred view is across lists`() {
        val tasks = listOf(task("x"), task("y", due = Due("2026-11-25")), task("z", due = Due("2026-11-24", "09:00"), starred = true))
        assertEquals(listOf("z", "y", "x"), TaskOps.view(tasks, Sort.Date).open.map { it.task.id })
        assertEquals(listOf("z"), TaskOps.starred(tasks).map { it.task.id })
    }

    @Test
    fun `moving renumbers and returns only changed tasks`() {
        val tasks = listOf(task("a", 1024), task("b", 2048), task("c", 3072))
        val changed = TaskOps.move(tasks, "c", 0, now)
        assertEquals(listOf("c", "a", "b"), changed.sortedBy { it.order }.map { it.id })
        assertTrue(TaskOps.move(tasks, "a", 0, now).isEmpty())
        assertEquals(1023L, TaskOps.newOrder(tasks))
    }

    @Test
    fun overdue() {
        val at = LocalDateTime.parse("2026-11-23T10:00")
        assertTrue(TaskOps.overdue(task("t", due = Due("2026-11-23", "09:00")), at))
        assertTrue(!TaskOps.overdue(task("t", due = Due("2026-11-23")), at))
        assertTrue(TaskOps.overdue(task("t", due = Due("2026-11-22")), at))
    }
}
