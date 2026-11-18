package io.github.michaelmassoni.tessera.notes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UndoStackTest {
    @Test fun `typing in bursts is undone a burst at a time`() {
        val stack = UndoStack("", coalesceMillis = 1000)
        stack.push("h", 0, "body")
        stack.push("he", 100, "body")
        stack.push("hey", 200, "body")
        stack.push("hey you", 5000, "body")
        assertEquals("hey", stack.undo())
        assertEquals("", stack.undo())
        assertNull(stack.undo())
        assertEquals("hey", stack.redo())
        assertEquals("hey you", stack.redo())
        assertFalse(stack.canRedo)
    }

    @Test fun `a different field starts a new step, and a new edit clears redo`() {
        val stack = UndoStack("a", coalesceMillis = 1000)
        stack.push("ab", 0, "title")
        stack.push("abc", 10, "body")
        assertEquals("ab", stack.undo())
        stack.push("abX", 20, "body")
        assertFalse(stack.canRedo)
        assertEquals("ab", stack.undo())
        assertEquals("a", stack.undo())
    }

    @Test fun `the history is bounded`() {
        val stack = UndoStack(0, limit = 3)
        (1..10).forEach { stack.push(it, it * 10_000L) }
        var steps = 0
        while (stack.undo() != null) steps++
        assertEquals(3, steps)
        assertEquals(7, stack.current)
    }
}
