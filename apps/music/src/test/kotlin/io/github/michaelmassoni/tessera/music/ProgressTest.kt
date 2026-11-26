package io.github.michaelmassoni.tessera.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {
    @Test
    fun `carries on from the sample while playing, never past the end`() {
        val p = Progress(10_000, 60_000, playing = true, sampledAt = 1_000)
        assertEquals(10_250L, p.at(1_250).positionMs)
        assertEquals(60_000L, p.at(900_000).positionMs)
        assertEquals(10_000L, p.at(500).positionMs) // a clock behind the sample
    }

    @Test
    fun `stays put when paused or never sampled`() {
        assertEquals(10_000L, Progress(10_000, 60_000, playing = false, sampledAt = 1_000).at(5_000).positionMs)
        assertEquals(10_000L, Progress(10_000, 60_000, playing = true).at(5_000).positionMs)
    }
}
