package io.github.michaelmassoni.tessera.clock.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test fun timer() {
        assertEquals("5:00", DurationFormat.timer(300_000))
        assertEquals("0:01", DurationFormat.timer(1)) // rounds up while counting down
        assertEquals("0:00", DurationFormat.timer(0))
        assertEquals("1:02:03", DurationFormat.timer(3_723_000))
        assertEquals("−0:05", DurationFormat.timer(-5_400))
    }

    @Test fun stopwatch() {
        assertEquals("0:00.00", DurationFormat.stopwatch(0))
        assertEquals("1:05.43", DurationFormat.stopwatch(65_432))
        assertEquals("1:00:00.00", DurationFormat.stopwatch(3_600_000))
    }
}
