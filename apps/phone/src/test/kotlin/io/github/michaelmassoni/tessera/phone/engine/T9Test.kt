package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    @Test fun `names become keys with accents folded`() {
        assertEquals(listOf("232", "56835223"), T9.keys("Ada Lovelace"))
        assertEquals(listOf("36453"), T9.keys("Émile"))
    }

    @Test fun `matches the start of any word, or across words`() {
        assertTrue(T9.matches("266", "Ann Lee"))
        assertTrue(T9.matches("533", "Ann Lee"))
        assertTrue(T9.matches("2665", "Ann Lee"))
        assertFalse(T9.matches("66", "Ann Lee"))
        assertFalse(T9.matches("2", "Ann Lee"))
        assertFalse(T9.matches("0266", "Ann Lee"))
        assertFalse(T9.matches("26*", "Ann Lee"))
    }
}
