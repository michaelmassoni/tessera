package io.github.michaelmassoni.tessera.clock.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    @Test fun `runs, pauses and resumes`() {
        val s = Stopwatch().start(1_000)
        assertEquals(500L, s.elapsed(1_500))
        val paused = s.pause(2_000)
        assertEquals(1_000L, paused.elapsed(99_000))
        assertEquals(1_250L, paused.start(10_000).elapsed(10_250))
    }

    @Test fun `laps and fastest and slowest`() {
        val s =
            Stopwatch()
                .start(0)
                .lap(10_000)
                .lap(15_000)
                .lap(27_000)
        assertEquals(listOf(10_000L, 5_000L, 12_000L), s.laps)
        assertEquals(1, s.fastestLap)
        assertEquals(2, s.slowestLap)
        assertNull(Stopwatch().start(0).lap(5).fastestLap)
    }

    @Test fun `lap only while running and reset clears everything`() {
        val paused = Stopwatch().start(0).pause(100)
        assertEquals(paused, paused.lap(200))
        assertTrue(paused.reset().isReset)
    }
}
