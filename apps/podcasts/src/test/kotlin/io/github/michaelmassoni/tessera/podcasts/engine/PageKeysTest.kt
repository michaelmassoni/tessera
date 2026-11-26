package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PageKeysTest {
    @Test
    fun `each level is keyed by its path`() {
        assertEquals(listOf("tab:0"), PageKeys.levels("tab:0", emptyList()))
        assertEquals(
            listOf("tab:1", "tab:1/show:a", "tab:1/show:a/episode:b", "tab:1/show:a/episode:b/show:a"),
            PageKeys.levels("tab:1", listOf("show:a", "episode:b", "show:a")),
        )
    }

    @Test
    fun `back closes only the page on top`() {
        val old = PageKeys.levels("tab:1", listOf("show:a", "episode:b"))
        assertEquals(listOf("tab:1/show:a/episode:b"), PageKeys.closed(old, PageKeys.levels("tab:1", listOf("show:a"))))
    }

    @Test
    fun `opening a page closes nothing`() {
        val old = PageKeys.levels("tab:1", listOf("show:a"))
        assertEquals(emptyList<String>(), PageKeys.closed(old, PageKeys.levels("tab:1", listOf("show:a", "episode:b"))))
    }

    @Test
    fun `another tab closes the pages but keeps the tab`() {
        val old = PageKeys.levels("tab:1", listOf("downloads"))
        assertEquals(listOf("tab:1/downloads"), PageKeys.closed(old, PageKeys.levels("tab:2", emptyList())))
    }
}
