package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageJumpTest {
    @Test fun `pages count from 1 for people and from 0 for the viewer`() {
        assertEquals(0, PageJump.parse("1", 12))
        assertEquals(11, PageJump.parse(" 12 ", 12))
    }

    @Test fun `anything that isn't a page of the document is refused`() {
        assertNull(PageJump.parse("0", 12))
        assertNull(PageJump.parse("13", 12))
        assertNull(PageJump.parse("", 12))
        assertNull(PageJump.parse("two", 12))
        assertNull(PageJump.parse("-3", 12))
    }
}

class VisiblePageTest {
    @Test fun `the page across the middle of the view is the current one`() {
        // After "Go to page 9": the end of page 8 at the top, page 9 from 300 px down a 2000 px view.
        assertEquals(8, VisiblePage.current(first = 7, tops = listOf(-1500f, 300f), middle = 1000f))
        // Page 8 still fills the middle.
        assertEquals(7, VisiblePage.current(first = 7, tops = listOf(-400f, 1400f), middle = 1000f))
        // Small pages, several in view: the last one starting above the middle.
        assertEquals(4, VisiblePage.current(first = 2, tops = listOf(0f, 400f, 800f, 1200f), middle = 1000f))
        // Nothing known yet: the first visible page.
        assertEquals(3, VisiblePage.current(first = 3, tops = emptyList(), middle = 1000f))
    }
}
