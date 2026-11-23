package io.github.michaelmassoni.tessera.tasks.engine

import java.time.LocalDate
import java.time.ZoneId

/** How a list's open tasks are ordered. */
enum class Sort { MyOrder, Date, Starred, Title }

/** A task as shown: subtasks are indented under their parent. */
data class Row(
    val task: Task,
    val depth: Int = 0,
)

/** What a list shows: open tasks (with subtasks under their parents), and completed ones. */
data class ListView(
    val open: List<Row>,
    val completed: List<Task>,
)

/** Completing, ordering and grouping tasks. Pure, so it's unit tested. */
object TaskOps {
    /**
     * Ticks [task]. A repeating task with a due date moves to its next date and stays open (as Google
     * Tasks does); any other is completed now. A snooze is cleared either way.
     */
    fun complete(
        task: Task,
        now: Long,
        zone: ZoneId,
    ): Task {
        val due = task.due
        val repeat = task.repeat
        if (due != null && repeat != null) {
            val next = Recurrence.next(due, repeat, now, zone)
            if (next != null) return task.copy(due = next, completedAt = null, snoozedUntil = null, modifiedAt = now, dirty = true)
        }
        return task.copy(completedAt = now, snoozedUntil = null, modifiedAt = now, dirty = true)
    }

    fun reopen(
        task: Task,
        now: Long,
    ) = task.copy(completedAt = null, modifiedAt = now, dirty = true)

    private fun dueKey(t: Task) = t.due?.let { it.date + (it.time ?: "99:99") } ?: "9999"

    private fun comparator(sort: Sort): Comparator<Task> {
        val order = compareBy<Task> { it.order }.thenBy { it.createdAt }.thenBy { it.id }
        return when (sort) {
            Sort.MyOrder -> order
            Sort.Date -> compareBy<Task> { dueKey(it) }.then(order)
            Sort.Starred -> compareByDescending<Task> { it.starred }.then(order)
            Sort.Title -> compareBy<Task> { it.title.lowercase() }.then(order)
        }
    }

    /**
     * [tasks] (one list's, or any set) as shown. Open subtasks follow their open parent in my
     * order; a subtask whose parent is done or gone stands on its own. Completed tasks are
     * newest first, flat, as Google Tasks shows them.
     */
    fun view(
        tasks: List<Task>,
        sort: Sort,
    ): ListView {
        val live = tasks.filterNot { it.deleted }
        val open = live.filter { !it.completed }
        val openIds = open.map { it.id }.toSet()
        val children = open.filter { it.parentId != null && it.parentId in openIds }.groupBy { it.parentId }
        val top = open.filter { it.parentId == null || it.parentId !in openIds }.sortedWith(comparator(sort))
        val rows =
            top.flatMap { parent ->
                listOf(Row(parent)) + children[parent.id].orEmpty().sortedWith(comparator(Sort.MyOrder)).map { Row(it, 1) }
            }
        val completed = live.filter { it.completed }.sortedByDescending { it.completedAt }
        return ListView(rows, completed)
    }

    /**
     * [rows] without tasks due after [last] (undated tasks always stay). A hidden task takes its
     * subtasks with it, so nothing shows indented under a missing parent.
     */
    fun dueBy(
        rows: List<Row>,
        last: java.time.LocalDate,
    ): List<Row> {
        var parentShown = true
        return rows.filter { r ->
            val shown = r.task.due?.let { !it.localDate.isAfter(last) } ?: true
            if (r.depth == 0) {
                parentShown = shown
                shown
            } else {
                parentShown && shown
            }
        }
    }

    /** Starred open tasks across every list, soonest due first. */
    fun starred(tasks: List<Task>): List<Row> =
        tasks.filter { it.starred && !it.completed && !it.deleted }.sortedWith(comparator(Sort.Date)).map { Row(it) }

    /** The order for a new task: above everything else in its list (or under its parent's subtasks). */
    fun newOrder(siblings: List<Task>): Long = (siblings.minOfOrNull { it.order } ?: 0) - 1

    /**
     * Moves [id] to position [to] among [siblings] (the top-level tasks, or one parent's subtasks,
     * in my order), renumbering so orders are spaced out. Returns the tasks whose order changed.
     */
    fun move(
        siblings: List<Task>,
        id: String,
        to: Int,
        now: Long,
    ): List<Task> {
        val list = siblings.sortedWith(comparator(Sort.MyOrder)).toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return emptyList()
        list.add(to.coerceIn(0, list.lastIndex), list.removeAt(from))
        return list.mapIndexedNotNull { i, t ->
            val order = (i + 1) * SPACING
            if (t.order == order) null else t.copy(order = order, modifiedAt = now, dirty = true)
        }
    }

    /** [task] and its subtasks, for deleting together. */
    fun withSubtasks(
        task: Task,
        all: List<Task>,
    ): List<Task> = listOf(task) + all.filter { it.parentId == task.id }

    /** Overdue: due before today, or before now when it has a time. */
    fun overdue(
        task: Task,
        now: java.time.LocalDateTime,
    ): Boolean {
        val due = task.due ?: return false
        if (task.completed) return false
        return if (due.time == null) due.localDate.isBefore(now.toLocalDate()) else due.dateTime.isBefore(now)
    }

    fun dueToday(
        task: Task,
        today: LocalDate,
    ) = task.due?.localDate == today

    private const val SPACING = 1024L
}
