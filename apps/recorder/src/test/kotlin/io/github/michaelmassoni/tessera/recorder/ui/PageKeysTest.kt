package io.github.michaelmassoni.tessera.recorder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PageKeysTest {
    @Test
    fun `each recording's player has its own key, apart from the list's`() {
        assertEquals(playerKey(7), playerKey(7))
        assertNotEquals(playerKey(7), playerKey(8))
        assertNotEquals("list", playerKey(0))
        assertNotEquals("recording", playerKey(0))
    }
}
