package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SearchResultsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val day = 24 * hour

    // Now: 2026-11-26 12:00 UTC.
    private val now = LocalDate.of(2026, 11, 26).toEpochDay() * day + 12 * hour

    private fun at(
        id: Long,
        title: String,
        beginFromNow: Long,
        length: Long = hour,
    ) = Instance(id, now + beginFromNow, now + beginFromNow + length, title, false, 0, 1)

    @Test
    fun `upcoming soonest first, then past most recent first`() {
        val r =
            SearchResults.arrange(
                listOf(
                    at(1, "Dentist", 3 * day),
                    at(2, "Lunch", -5 * day),
                    at(3, "Flight", day),
                    at(4, "Party", -30 * day),
                ),
                now,
                zone,
            )
        assertEquals(listOf("Flight", "Dentist"), r.upcoming.flatMap { it.second }.map { it.title })
        assertEquals(listOf("Lunch", "Party"), r.past.flatMap { it.second }.map { it.title })
        assertEquals(LocalDate.of(2026, 11, 27), r.upcoming.first().first)
    }

    @Test
    fun `a repeating event shows once, at its next occurrence`() {
        val weekly = (-3..3).map { at(7, "Standup", it * 7 * day) }
        val r = SearchResults.arrange(weekly, now, zone)
        assertEquals(1, r.upcoming.sumOf { it.second.size })
        assertEquals(
            now,
            r.upcoming
                .single()
                .second
                .single()
                .begin,
        )
        assertEquals(0, r.past.size)
    }

    @Test
    fun `a series that's over shows its last occurrence, and one in progress is upcoming`() {
        val over = (1..3).map { at(8, "Course", -it * 7 * day) }
        val r = SearchResults.arrange(over + at(9, "Meeting", -hour / 2), now, zone)
        assertEquals(
            now - 7 * day,
            r.past
                .single()
                .second
                .single()
                .begin,
        )
        assertEquals(
            "Meeting",
            r.upcoming
                .single()
                .second
                .single()
                .title,
        )
    }
}
