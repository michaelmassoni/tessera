package io.github.michaelmassoni.tessera.recorder.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class NamesTest {
    private val at = LocalDateTime.of(2026, 11, 23, 14, 5)

    @Test
    fun `default title in 24 and 12 hour time`() {
        assertEquals("Recording 23 Nov 14:05", Names.defaultTitle("Recording", at, Locale.UK, is24Hour = true))
        assertEquals("Recording 23 Nov 2:05 PM", Names.defaultTitle("Recording", at, Locale.US, is24Hour = false))
    }

    @Test
    fun `clean title is one line and never blank`() {
        assertEquals("Team call", Names.cleanTitle("  Team \n call ", "x"))
        assertEquals("fallback", Names.cleanTitle("   ", "fallback"))
    }

    @Test
    fun `file names avoid clashes, case-insensitively`() {
        assertEquals("Interview.m4a", Names.fileName("Interview", emptySet()))
        assertEquals("Interview (2).m4a", Names.fileName("Interview", setOf("interview.m4a")))
        assertEquals("Interview (3).m4a", Names.fileName("Interview", setOf("Interview.m4a", "Interview (2).m4a")))
    }

    @Test
    fun `file names drop characters file systems refuse`() {
        assertEquals("Q-A 1.2.m4a", Names.fileName("Q/A 1:2", emptySet()))
        assertEquals("Recording.m4a", Names.fileName("...", emptySet()))
        assertEquals("Notes.m4a", Names.fileName(" Notes. ", emptySet()))
    }

    @Test
    fun durations() {
        assertEquals("0:07", Durations.clock(7_900))
        assertEquals("3:12", Durations.clock(192_000))
        assertEquals("1:04:09", Durations.clock(3_849_000))
        assertEquals("0:00", Durations.clock(-5))
        assertEquals("3:12.4", Durations.clockTenths(192_450))
    }
}

class WaveformTest {
    @Test
    fun `levels are logarithmic from -50 dB`() {
        assertEquals(0, Waveform.levelOf(0))
        assertEquals(255, Waveform.levelOf(32767))
        // -50 dB and quieter is silence; -25 dB is half height.
        assertEquals(0, Waveform.levelOf(100))
        assertEquals(128, Waveform.levelOf(1843))
    }

    @Test
    fun `bars keep each stretch's peak`() {
        val w = Waveform(byteArrayOf(0, 10, 0, 0, (255).toByte(), 0, 0, 0))
        assertArrayEquals(floatArrayOf(10 / 255f, 1f), w.bars(2), 0.0001f)
        assertEquals(8, w.bars(8).size)
        // More bars than levels repeats levels rather than failing.
        assertEquals(16, w.bars(16).size)
        assertEquals(0, Waveform(ByteArray(0)).bars(4).count { it > 0 })
    }

    @Test
    fun `encode and decode round trip`() {
        val w = Waveform(byteArrayOf(1, 2, (200).toByte()))
        val back = Waveform.decode(w.encode())!!
        assertArrayEquals(w.levels, back.levels)
        assertEquals(150L, back.durationMs)
        assertNull(Waveform.decode("nope".toByteArray()))
        assertNull(Waveform.decode(ByteArray(0)))
    }

    @Test
    fun `builder grows and gives the tail`() {
        val b = WaveformBuilder()
        repeat(3000) { b.add(it % 256) }
        assertEquals(3000, b.size)
        assertEquals(3000, b.build().size)
        val tail = b.tail(2)
        assertArrayEquals(floatArrayOf((2998 % 256) / 255f, (2999 % 256) / 255f), tail, 0.0001f)
        assertEquals(255, WaveformBuilder().apply { add(300) }.build().level(0))
    }
}

class SessionTest {
    @Test
    fun `elapsed counts only while recording`() {
        val s = Session(now = 1_000)
        assertEquals(500, s.elapsed(1_500))
        assertTrue(s.pause(2_000))
        assertFalse(s.pause(2_100))
        assertEquals(1_000, s.elapsed(9_000))
        assertTrue(s.resume(10_000))
        assertEquals(1_500, s.elapsed(10_500))
    }

    @Test
    fun `silenced shows only while recording`() {
        val s = Session(now = 0)
        assertEquals(RecorderState.Active(1_000, paused = false, silenced = true), s.state(1_000, silenced = true))
        s.pause(1_000)
        assertTrue(s.paused)
        assertEquals(RecorderState.Active(1_000, paused = true, silenced = false), s.state(2_000, silenced = true))
        assertTrue(s.resume(2_500))
        assertFalse(s.resume(3_000))
    }
}
