package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SeriesSplitTest {
    private val nineAm = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun `the old series ends the second before, without COUNT`() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=WE;UNTIL=20261125T085959Z",
            SeriesSplit.endBefore("FREQ=WEEKLY;BYDAY=WE;COUNT=10", nineAm, allDay = false),
        )
        assertEquals(
            "FREQ=DAILY;UNTIL=20261124T085959Z",
            SeriesSplit.endBefore("FREQ=DAILY;UNTIL=20261231T000000Z", nineAm - 86_400_000, false),
        )
    }

    @Test fun `all-day series end the day before`() {
        val day =
            LocalDate
                .of(2026, 11, 25)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        assertEquals("FREQ=YEARLY;UNTIL=20261124", SeriesSplit.endBefore("RRULE:FREQ=YEARLY", day, allDay = true))
    }

    @Test fun `the new series keeps what's left of a COUNT`() {
        assertEquals("FREQ=WEEKLY;COUNT=7", SeriesSplit.continueFrom("FREQ=WEEKLY;COUNT=10", before = 3))
        assertEquals("FREQ=WEEKLY;COUNT=1", SeriesSplit.continueFrom("FREQ=WEEKLY;COUNT=2", before = 5))
        assertEquals("FREQ=DAILY;UNTIL=20261231T000000Z", SeriesSplit.continueFrom("FREQ=DAILY;UNTIL=20261231T000000Z", before = 3))
    }

    @Test fun `excluding adds a UTC stamp once`() {
        assertEquals("20261125T090000Z", SeriesSplit.exclude(null, nineAm))
        assertEquals("20261101T090000Z,20261125T090000Z", SeriesSplit.exclude("20261101T090000Z", nineAm))
        assertEquals("20261125T090000Z", SeriesSplit.exclude("20261125T090000Z", nineAm))
    }
}
