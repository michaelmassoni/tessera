package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTest {
    private val earpiece = AudioRoute("e", Route.Earpiece)
    private val speaker = AudioRoute("s", Route.Speaker)
    private val wired = AudioRoute("w", Route.Wired)
    private val buds = AudioRoute("b", Route.Bluetooth, "Pixel Buds")

    @Test
    fun `earpiece and speaker only is a toggle`() {
        val audio = AudioInfo(current = Route.Earpiece, available = listOf(earpiece, speaker))
        assertFalse(audio.needsPicker)
        assertEquals(speaker, audio.toggleTarget())
        assertEquals(earpiece, audio.copy(current = Route.Speaker).toggleTarget())
    }

    @Test
    fun `a headset or bluetooth needs a picker`() {
        assertTrue(AudioInfo(available = listOf(earpiece, speaker, buds)).needsPicker)
        assertTrue(AudioInfo(available = listOf(wired, speaker)).needsPicker)
    }

    @Test
    fun `speaker off goes back to a wired headset before the earpiece`() {
        val audio = AudioInfo(current = Route.Speaker, available = listOf(speaker, wired))
        assertEquals(wired, audio.toggleTarget())
        assertNull(AudioInfo(current = Route.Earpiece, available = listOf(earpiece)).toggleTarget())
    }

    @Test
    fun `screen goes off near the ear only for a live call on the earpiece`() {
        assertTrue(Proximity.wanted(liveCall = true, route = Route.Earpiece))
        assertFalse(Proximity.wanted(liveCall = true, route = Route.Speaker))
        assertFalse(Proximity.wanted(liveCall = true, route = Route.Bluetooth))
        assertFalse(Proximity.wanted(liveCall = false, route = Route.Earpiece))
    }
}
