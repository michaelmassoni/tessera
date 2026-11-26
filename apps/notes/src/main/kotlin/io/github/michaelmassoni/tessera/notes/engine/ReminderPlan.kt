package io.github.michaelmassoni.tessera.notes.engine

/**
 * Which note reminders to show and when the next alarm is. A reminder ("note@time") is shown once,
 * and only if it was expected: one set while it was still to come. So a time already past when it's
 * set doesn't remind, but one missed while the phone was off does (up to a day late). Pure, so
 * it's unit tested.
 */
object ReminderPlan {
    const val LATE_MS = 24 * 60 * 60 * 1000L

    fun key(note: Note) = note.reminderAt?.let { "${note.id}@$it" }

    private fun active(notes: Collection<Note>) = notes.filter { it.reminderAt != null && !it.isTrashed }

    /** The reminders to expect from now: those still to come, plus earlier ones not yet shown. */
    fun expected(
        notes: Collection<Note>,
        previous: Set<String>,
        now: Long,
    ): Set<String> =
        previous.filter { (it.substringAfterLast('@').toLongOrNull() ?: 0) > now - LATE_MS }.toSet() +
            active(notes).filter { it.reminderAt!! > now }.mapNotNull(::key)

    /** When the next alarm should go off, or null for none. */
    fun nextAlarm(
        notes: Collection<Note>,
        now: Long,
    ): Long? = active(notes).mapNotNull { it.reminderAt }.filter { it > now }.minOrNull()

    /** The notes to remind about now (expected and due), and what's still expected afterwards. */
    fun due(
        notes: Collection<Note>,
        expected: Set<String>,
        now: Long,
    ): Pair<List<Note>, Set<String>> {
        val due = active(notes).filter { it.reminderAt!! <= now && key(it) in expected }
        return due to (expected - due.mapNotNull(::key).toSet())
    }
}
