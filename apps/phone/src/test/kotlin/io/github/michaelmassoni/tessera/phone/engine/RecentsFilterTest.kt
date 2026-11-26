package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecentsFilterTest {
    private val today = LocalDate.of(2026, 11, 27)
    private val yesterday = today.minusDays(1)

    private fun group(
        id: Long,
        name: String?,
        number: String,
        type: CallType,
    ) = CallGroup(CallEntry(id, number, name, type, 0, 0), 1)

    private val sections =
        listOf(
            today to listOf(group(1, "Zoë Adams", "0412 345 678", CallType.Missed), group(2, null, "0400111222", CallType.Outgoing)),
            yesterday to listOf(group(3, "Bob", "0499 000 111", CallType.Incoming), group(4, "Zoe Adams", "0412345678", CallType.Rejected)),
        )

    private fun ids(result: List<Pair<LocalDate, List<CallGroup>>>) = result.flatMap { (_, g) -> g.map { it.latest.id } }

    @Test
    fun `nothing to filter returns everything`() {
        assertEquals(sections, RecentsFilter.filter(sections, "  ", missedOnly = false))
    }

    @Test
    fun `names match ignoring case and accents`() {
        assertEquals(listOf(1L, 4L), ids(RecentsFilter.filter(sections, "zoe", missedOnly = false)))
    }

    @Test
    fun `numbers match by digits ignoring spaces`() {
        assertEquals(listOf(1L, 4L), ids(RecentsFilter.filter(sections, "2345", missedOnly = false)))
        assertEquals(listOf(2L), ids(RecentsFilter.filter(sections, "0400 111", missedOnly = false)))
    }

    @Test
    fun `missed only keeps missed and declined calls and drops empty days`() {
        assertEquals(listOf(1L, 4L), ids(RecentsFilter.filter(sections, "", missedOnly = true)))
        val bob = RecentsFilter.filter(sections, "bob", missedOnly = true)
        assertEquals(emptyList<Long>(), ids(bob))
        assertEquals(0, bob.size)
    }
}
