package io.github.michaelmassoni.tessera.notes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlanTest {
    private val now = 1_000_000_000L

    private fun note(
        id: String,
        at: Long?,
        trashed: Boolean = false,
    ) = Note(id, reminderAt = at, trashedAt = if (trashed) 1 else null)

    @Test
    fun `the next alarm is the soonest reminder to come`() {
        val notes = listOf(note("a", now + 60_000), note("b", now + 5_000), note("c", now - 5_000), note("d", now + 1_000, trashed = true))
        assertEquals(now + 5_000, ReminderPlan.nextAlarm(notes, now))
        assertNull(ReminderPlan.nextAlarm(listOf(note("c", now - 5_000)), now))
    }

    @Test
    fun `only reminders expected while still to come are shown`() {
        val soon = note("a", now + 1_000)
        val alreadyPast = note("b", now - 1_000)
        val expected = ReminderPlan.expected(listOf(soon, alreadyPast), emptySet(), now)
        assertEquals(setOf("a@${now + 1_000}"), expected)
        // Later, both are due: only the expected one reminds, and it isn't expected again.
        val (due, left) = ReminderPlan.due(listOf(soon, alreadyPast), expected, now + 2_000)
        assertEquals(listOf("a"), due.map { it.id })
        assertEquals(emptySet<String>(), left)
    }

    @Test
    fun `a reminder missed while the phone was off still shows, up to a day late`() {
        val missed = note("a", now - 60_000)
        val previous = setOf("a@${now - 60_000}", "old@${now - ReminderPlan.LATE_MS - 1}")
        val expected = ReminderPlan.expected(listOf(missed), previous, now)
        assertEquals(setOf("a@${now - 60_000}"), expected)
        assertEquals(listOf("a"), ReminderPlan.due(listOf(missed), expected, now).first.map { it.id })
    }

    @Test
    fun `changing the time makes it a new reminder`() {
        val before = ReminderPlan.expected(listOf(note("a", now + 1_000)), emptySet(), now)
        val moved = note("a", now + 9_000)
        val (due, _) = ReminderPlan.due(listOf(moved), ReminderPlan.expected(listOf(moved), before, now), now + 2_000)
        assertEquals(emptyList<Note>(), due)
    }
}
