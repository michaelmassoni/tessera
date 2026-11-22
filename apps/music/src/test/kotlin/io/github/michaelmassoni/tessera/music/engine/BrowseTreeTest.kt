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
        assertEquals(listOf("playlists", "albums", "artists", "songs"), tree.root().map { it.id })
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
        assertEquals(PlayingFrom(PlayingFrom.Kind.Album, "Northern Lights"), q.from)
    }

    @Test
    fun `playlists keep their order and offer shuffle`() {
        val items = tree.children(tree.children(BrowseTree.PLAYLISTS)!!.single().id)!!
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
}
