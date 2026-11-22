package io.github.michaelmassoni.tessera.tasks.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {
    private val base = Task("t", "l", "Buy milk", notes = "2L", starred = false, dirty = false)

    @Test
    fun `each side keeps the fields it changed`() {
        val local = base.copy(title = "Buy oat milk")
        val remote = base.copy(notes = "1L", starred = true, etag = "\"new\"")
        val m = Merge.merge(base, local, remote)
        assertEquals("Buy oat milk", m.title)
        assertEquals("1L", m.notes)
        assertTrue(m.starred)
        assertEquals("\"new\"", m.etag)
        assertTrue(m.dirty)
    }

    @Test
    fun `nothing of ours left to send`() {
        val m = Merge.merge(base, base.copy(title = "Same"), base.copy(title = "Same"))
        assertFalse(m.dirty)
    }
}
