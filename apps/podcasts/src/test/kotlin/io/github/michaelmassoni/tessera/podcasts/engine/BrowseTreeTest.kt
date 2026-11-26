package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTreeTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    private val radio = Show("radio", "https://radio.example/feed", "Radio Lab", author = "WNYC", followed = true, latestAt = now - day)
    private val quiet = Show("quiet", "https://quiet.example/feed", "Quiet Hours", followed = true, latestAt = now - 5 * day)
    private val kept = Show("kept", "https://kept.example/feed", "Kept Only", followed = false)

    private fun ep(
        show: Show,
        n: Int,
        ageDays: Long,
    ) = Episode(
        "${show.id}:$n",
        show.id,
        "${show.title} $n",
        publishedAt = now - ageDays * day,
        durationMs = 1_000_000,
        audioUrl = "https://x/$n.mp3",
    )

    private val r1 = ep(radio, 1, 30)
    private val r2 = ep(radio, 2, 1)
    private val q1 = ep(quiet, 1, 5)
    private val k1 = ep(kept, 1, 2)

    private val episodes = mapOf(radio.id to listOf(r1, r2), quiet.id to listOf(q1), kept.id to listOf(k1))

    private val library =
        Library(
            shows = listOf(quiet, radio, kept),
            states =
                mapOf(
                    r1.id to EpisodeState(positionMs = 250_000),
                    q1.id to EpisodeState(played = true, download = "q1.mp3"),
                ),
            upNext = listOf(k1.id, r1.id),
        )

    private val tree = BrowseTree(library, episodes, now)

    private fun ids(parent: String) = tree.children(parent)!!.map { it.id }

    @Test
    fun `root lists the four sections`() {
        assertEquals(listOf("upnext", "new", "shows", "downloads"), ids(BrowseTree.ROOT))
        assertTrue(tree.children(BrowseTree.ROOT)!!.none { it.playable })
    }

    @Test
    fun `sections list episodes and shows`() {
        assertEquals(listOf("ep/kept:1", "ep/radio:1"), ids(BrowseTree.UP_NEXT))
        // Unplayed and recent, followed shows only, newest first.
        assertEquals(listOf("ep/radio:2"), ids(BrowseTree.NEW))
        // Followed shows, latest episode first; a show that's only kept isn't listed.
        assertEquals(listOf("show/radio", "show/quiet"), ids(BrowseTree.SHOWS))
        assertEquals(listOf("ep/quiet:1"), ids(BrowseTree.DOWNLOADS))
        assertEquals(listOf("ep/radio:2", "ep/radio:1"), ids("show/radio"))
        assertNull(tree.children("show/nope"))
        assertNull(tree.children("ep/radio:1"))
    }

    @Test
    fun `episodes carry their show and progress`() {
        val upNext = tree.children(BrowseTree.UP_NEXT)!!
        assertEquals("Radio Lab", upNext[1].subtitle)
        assertEquals(0.25f, upNext[1].progress, 0.001f)
        assertEquals(1f, tree.children(BrowseTree.DOWNLOADS)!!.single().progress)
        // Inside a show, the show's name isn't repeated.
        assertEquals("", tree.children("show/radio")!!.first().subtitle)
        assertTrue(tree.children(BrowseTree.SHOWS)!!.all { it.grid && !it.playable })
    }

    @Test
    fun `picking an episode plays it now and keeps the rest queued`() {
        assertEquals(listOf("radio:2", "kept:1", "radio:1"), tree.playNow("ep/radio:2"))
        assertEquals(listOf("radio:1", "kept:1"), tree.playNow("ep/radio:1"))
        assertNull(tree.playNow("ep/radio:9"))
        assertEquals("radio:2", BrowseTree.episodeId("ep/radio:2"))
        assertEquals("radio:2", BrowseTree.episodeId("radio:2"))
    }

    @Test
    fun `voice search`() {
        // "Play Podcasts": Up Next resumes.
        assertEquals(library.upNext, tree.search(""))
        // A show's name: its newest unheard episode.
        assertEquals("radio:2", tree.search("radio lab")!!.first())
        // Every episode heard: its newest.
        assertEquals("quiet:1", tree.search("quiet")!!.first())
        // An episode's title.
        assertEquals("radio:1", tree.search("Lab 1")!!.first())
        // A show that's only kept (its episode is in Up Next) is found too.
        assertEquals("kept:1", tree.search("kept only")!!.first())
        assertNull(tree.search("nothing like this"))
        assertEquals(listOf("show/radio", "ep/radio:2", "ep/radio:1"), tree.searchNodes("radio").map { it.id })
    }
}
