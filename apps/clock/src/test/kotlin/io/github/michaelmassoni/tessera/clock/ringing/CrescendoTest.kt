package io.github.michaelmassoni.tessera.clock.ringing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrescendoTest {
    @Test
    fun `off means full volume at once`() {
        assertEquals(1f, Crescendo.volume(0, 0))
    }

    @Test
    fun `rises from quiet to full, never down`() {
        assertEquals(Crescendo.START, Crescendo.volume(0, 30_000))
        assertEquals(1f, Crescendo.volume(30_000, 30_000))
        assertEquals(1f, Crescendo.volume(90_000, 30_000))
        val steps = (0..30_000 step 250).map { Crescendo.volume(it.toLong(), 30_000) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a })
        // Halfway it's still well under half: loudness is heard logarithmically.
        assertTrue(Crescendo.volume(15_000, 30_000) < 0.2f)
    }
}
