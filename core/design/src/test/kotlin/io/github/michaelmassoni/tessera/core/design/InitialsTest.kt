package io.github.michaelmassoni.tessera.core.design

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test fun `initials and colour slots`() {
        assertEquals("AL", Initials.of("ada Lovelace"))
        assertEquals("ÉZ", Initials.of("Émile  de Zola"))
        assertEquals("B", Initials.of("Bob"))
        assertEquals("#", Initials.of("123"))
        assertEquals(Initials.slot("abc", 5), Initials.slot("abc", 5))
        (0..100).forEach { assertEquals(true, Initials.slot("key$it", 5) in 0..4) }
    }
}
