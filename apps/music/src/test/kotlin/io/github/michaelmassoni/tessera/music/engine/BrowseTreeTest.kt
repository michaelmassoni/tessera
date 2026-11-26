package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTreeTest {
    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        albumId: Long,
        track: Int,
    ) = Song(id, "u$id", title, artist, artist.length.toLong(), album, albumId, 1000, track = track)

    private val songs =
        listOf(
            song(1, "Northern Lights", "Aurora Bay", "Northern Lights", 10, 1),
            song(2, "Slow Tide", "Aurora Bay", "Northern Lights", 10, 2),
            song(3, "Lanterns", "Juniper", "Paper Planes", 20, 1),
        )
    private val tree = BrowseTree(songs, listOf(Playlist(7, "Road trip", listOf(3, 2))), albumArtists = true)

    @Test
    fun `root, albums and an album's songs`() {
        assertEquals(listOf("recent", "playlists", "albums", "artists"), tree.root().map { it.id })
        val albums = tree.children(BrowseTree.ALBUMS)!!
        assertEquals(listOf("Northern Lights", "Paper Planes"), albums.map { it.title })
        val tracks = tree.children(albums.first().id)!!
        assertEquals(listOf("Northern Lights", "Slow Tide"), tracks.map { it.title })
        assertTrue(tracks.all { it.playable })
    }

    @Test
    fun `picking a song queues its album from there`() {
        val pick = tree.children(tree.children(BrowseTree.ALBUMS)!!.first().id)!![1]
        val q = tree.queueFor(pick.id)!!
        assertEquals(listOf(1L, 2L), q.songs.map { it.id })
        assertEquals(1, q.start)
        assertEquals(PlayingFrom(PlayingFrom.Kind.Album, "Northern Lights", 10), q.from)
    }

    @Test
    fun `playlists keep their order and offer shuffle`() {
        val items = tree.children("${BrowseTree.PLAYLIST}7")!!
        assertEquals(listOf("Shuffle", "Lanterns", "Slow Tide"), items.map { it.title })
        val q = tree.queueFor(items.first().id)!!
        assertTrue(q.shuffle)
        assertEquals(listOf(3L, 2L), q.songs.map { it.id })
    }

    @Test
    fun `voice search prefers an album, then an artist, then songs`() {
        assertEquals(PlayingFrom.Kind.Album, tree.search("northern lights")!!.from.kind)
        assertEquals(PlayingFrom.Kind.Artist, tree.search("Juniper")!!.from.kind)
        assertEquals(listOf("Slow Tide"), tree.search("tide")!!.songs.map { it.title })
        assertTrue(tree.search("")!!.shuffle)
        assertNull(tree.search("nothing like this"))
        assertNull(tree.queueFor("album/999"))
    }

    @Test
    fun `playlists tab starts with liked songs and all songs`() {
        assertEquals(listOf("liked", "songs", "playlist/7"), tree.children(BrowseTree.PLAYLISTS)!!.map { it.id })
        assertEquals(listOf("Shuffle", "Lanterns", "Northern Lights", "Slow Tide"), tree.children(BrowseTree.SONGS)!!.map { it.title })
        // Nothing liked yet: just a Shuffle-less empty list.
        assertEquals(emptyList<String>(), tree.children(BrowseTree.LIKED)!!.map { it.id })
    }

    @Test
    fun `liked songs, last liked first, play as a queue`() {
        val starred = songs.map { if (it.id == 3L) it.copy(starredAt = 100) else it }
        val likes = mapOf(1L to LikeChange(liked = true, at = 200))
        val t = BrowseTree(starred, emptyList(), albumArtists = true, likes = likes)
        val items = t.children(BrowseTree.LIKED)!!
        assertEquals(listOf("Shuffle", "Northern Lights", "Lanterns"), items.map { it.title })
        val q = t.queueFor(items[2].id)!!
        assertEquals(listOf(1L, 3L), q.songs.map { it.id })
        assertEquals(1, q.start)
        assertEquals(BrowseTree.LIKED_TITLE, q.from.name)
    }

    @Test
    fun `recent lists played then added albums under their headings`() {
        val dated =
            songs.map {
                when (it.id) {
                    1L -> it.copy(addedAt = 50)
                    3L -> it.copy(addedAt = 90, playedAt = 10)
                    else -> it
                }
            }
        // Played on this phone more recently than the server's record.
        val t = BrowseTree(dated, emptyList(), albumArtists = true, history = mapOf(2L to 500L))
        val recent = t.children(BrowseTree.RECENT)!!
        assertEquals(
            listOf(
                BrowseTree.RECENTLY_PLAYED to "album/10",
                BrowseTree.RECENTLY_PLAYED to "album/20",
                BrowseTree.RECENTLY_ADDED to "album/20#added",
                BrowseTree.RECENTLY_ADDED to "album/10#added",
            ),
            recent.map { it.group to it.id },
        )
        assertTrue(recent.all { it.grid && !it.playable })
        // Each opens its album.
        assertEquals(listOf("Lanterns"), t.children(recent[2].id)!!.map { it.title })
        assertEquals(recent.size, recent.map { it.id }.toSet().size)
        // Never played, never dated: nothing to show.
        assertEquals(emptyList<String>(), tree.children(BrowseTree.RECENT)!!.map { it.id })
    }
}
