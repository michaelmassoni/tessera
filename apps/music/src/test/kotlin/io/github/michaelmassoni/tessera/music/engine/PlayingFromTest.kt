package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayingFromTest {
    private fun song(
        id: Long,
        artist: String,
        album: String,
        albumId: Long,
    ) = Song(id, "content://$id", "Song $id", artist, artist.hashCode().toLong(), album, albumId, 180_000)

    private val songs =
        listOf(
            song(1, "Juniper", "Fold", 30),
            song(2, "Aurora Bay", "Greatest Hits", 40),
            song(3, "The Night Shift", "Greatest Hits", 50),
        )
    private val albums = Library.albums(songs)
    private val artists = Library.artists(songs)
    private val playlists = listOf(Playlist(7, "Road trip", listOf(1, 2)))

    @Test fun `the id leads back to the page`() {
        assertEquals(50L, PlayingFrom(PlayingFrom.Kind.Album, "Greatest Hits", 50).sourceId(albums, artists, playlists))
        assertEquals(7L, PlayingFrom(PlayingFrom.Kind.Playlist, "Renamed since", 7).sourceId(albums, artists, playlists))
        val juniper = artists.first { it.name == "Juniper" }.id
        assertEquals(juniper, PlayingFrom(PlayingFrom.Kind.Artist, "Juniper", juniper).sourceId(albums, artists, playlists))
    }

    @Test fun `without an id, only a name one item has`() {
        assertEquals(30L, PlayingFrom(PlayingFrom.Kind.Album, "fold").sourceId(albums, artists, playlists))
        assertEquals(7L, PlayingFrom(PlayingFrom.Kind.Playlist, "Road trip").sourceId(albums, artists, playlists))
        // Two albums called Greatest Hits: no guessing.
        assertNull(PlayingFrom(PlayingFrom.Kind.Album, "Greatest Hits").sourceId(albums, artists, playlists))
    }

    @Test fun `songs, search and things gone lead nowhere`() {
        assertNull(PlayingFrom(PlayingFrom.Kind.Songs).sourceId(albums, artists, playlists))
        assertNull(PlayingFrom(PlayingFrom.Kind.Search, "fold").sourceId(albums, artists, playlists))
        assertNull(PlayingFrom(PlayingFrom.Kind.Playlist, "Deleted", 99).sourceId(albums, artists, playlists))
    }
}
