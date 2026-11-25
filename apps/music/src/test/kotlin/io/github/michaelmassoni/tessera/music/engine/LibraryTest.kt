package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTest {
    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        albumId: Long,
        track: Int = 0,
        artistId: Long = artist.hashCode().toLong(),
        year: Int = 2000,
    ) = Song(id, "content://$id", title, artist, artistId, album, albumId, 180_000, track, year)

    private val songs =
        listOf(
            song(1, "Come Together", "The Beatles", "Abbey Road", 10, track = 1001, year = 1969),
            song(2, "Something", "The Beatles", "Abbey Road", 10, track = 1002, year = 1969),
            song(3, "Here Comes the Sun", "The Beatles", "Abbey Road", 10, track = 2001, year = 1969),
            song(4, "Árvore", "Ana Moura", "Casa Guilhermina", 20),
            song(5, "Yesterday", "The Beatles", "Help!", 30, track = 13, year = 1965),
            song(6, "Apple", "Charli xcx", "Brat", 40, track = 5),
        )

    @Test fun `sorting ignores leading articles, accents and case`() {
        assertEquals(
            listOf("Apple", "Árvore", "Come Together", "Here Comes the Sun", "Something", "Yesterday"),
            Library.songs(songs).map {
                it.title
            },
        )
        assertEquals("beatles", Library.sortKey("The Beatles"))
    }

    @Test fun `albums keep disc and track order and pick the main artist`() {
        val abbey = Library.albums(songs).first { it.id == 10L }
        assertEquals(listOf("Come Together", "Something", "Here Comes the Sun"), abbey.songs.map { it.title })
        assertEquals("The Beatles", abbey.artist)
        assertEquals(1969, abbey.year)
        assertEquals(listOf("Abbey Road", "Brat", "Casa Guilhermina", "Help!"), Library.albums(songs).map { it.title })
    }

    @Test fun `artists list their albums newest first and all songs`() {
        val beatles = Library.artists(songs).first { it.name == "The Beatles" }
        assertEquals(listOf("Abbey Road", "Help!"), beatles.albums.map { it.title })
        assertEquals(4, beatles.songs.size)
        assertEquals(listOf("Ana Moura", "The Beatles", "Charli xcx"), Library.artists(songs).map { it.name })
    }

    @Test fun `search matches title, artist and album words`() {
        assertEquals(listOf("Árvore"), Library.search(songs, "arvore").map { it.title })
        assertEquals(listOf("Here Comes the Sun"), Library.search(songs, "beatles sun").map { it.title })
        assertEquals(1, Library.search(songs, "brat").size)
        assertEquals(emptyList<Song>(), Library.search(songs, " "))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test fun `the song playing leads to its album and track artist`() {
        val albums = Library.albums(songs)
        val artists = Library.artists(songs)
        val playing = songs[4]
        assertEquals("Help!", Library.albumOf(playing, 0, albums)?.title)
        assertEquals("The Beatles", Library.artistOf(playing, "", artists)?.name)
        // Not in the library by id (a saved queue from before a rescan): the album id and the artist's name.
        assertEquals("Brat", Library.albumOf(null, 40, albums)?.title)
        assertEquals("Ana Moura", Library.artistOf(null, " ana moura", artists)?.name)
        assertEquals(null, Library.albumOf(null, 99, albums))
        assertEquals(null, Library.artistOf(null, "", artists))
    }
}
