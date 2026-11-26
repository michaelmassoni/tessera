package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PageKeysTest {
    @Test
    fun `each level is keyed by its path`() {
        assertEquals(listOf("tab:1"), PageKeys.levels("tab:1", emptyList()))
        assertEquals(
            listOf("tab:1", "tab:1/album:5", "tab:1/album:5/artist:2", "tab:1/album:5/artist:2/album:5"),
            PageKeys.levels("tab:1", listOf("album:5", "artist:2", "album:5")),
        )
    }

    @Test
    fun `back closes only the page on top`() {
        val old = PageKeys.levels("tab:1", listOf("album:5", "artist:2"))
        val new = PageKeys.levels("tab:1", listOf("album:5"))
        assertEquals(listOf("tab:1/album:5/artist:2"), PageKeys.closed(old, new))
    }

    @Test
    fun `opening a page closes nothing`() {
        val old = PageKeys.levels("tab:1", listOf("album:5"))
        assertEquals(emptyList<String>(), PageKeys.closed(old, PageKeys.levels("tab:1", listOf("album:5", "artist:2"))))
    }

    @Test
    fun `another tab closes the pages but keeps the tab`() {
        val old = PageKeys.levels("tab:1", listOf("album:5", "artist:2"))
        assertEquals(listOf("tab:1/album:5", "tab:1/album:5/artist:2"), PageKeys.closed(old, PageKeys.levels("tab:0", emptyList())))
    }
}
