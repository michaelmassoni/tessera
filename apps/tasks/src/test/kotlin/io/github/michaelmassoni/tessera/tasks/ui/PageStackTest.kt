package io.github.michaelmassoni.tessera.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageStackTest {
    private val parents = mapOf("sub" to "parent")
    private val forgotten = mutableListOf<String>()
    private val pages = PageStack { forgotten += it }

    private fun stack(open: String?) = taskPages(open) { parents[it] }

    @Test fun `a subtask sits on its parent, which sits on the list`() {
        assertEquals(listOf(LIST_PAGE), stack(null))
        assertEquals(listOf(LIST_PAGE, "task:parent"), stack("parent"))
        assertEquals(listOf(LIST_PAGE, "task:parent", "task:sub"), stack("sub"))
    }

    @Test fun `closing a subtask keeps its parent, closing the parent forgets it`() {
        pages.show(stack("parent"))
        pages.show(stack("sub"))
        pages.show(stack("parent"))
        assertEquals(listOf("task:sub"), forgotten)
        pages.show(stack(null))
        assertEquals(listOf("task:sub", "task:parent"), forgotten)
    }

    @Test fun `a parent loop does not hang`() {
        val loop = taskPages("a") { if (it == "a") "b" else "a" }
        assertEquals(LIST_PAGE, loop.first())
        assertEquals("task:a", loop.last())
    }
}
