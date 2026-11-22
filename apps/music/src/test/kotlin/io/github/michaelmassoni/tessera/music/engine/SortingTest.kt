package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private fun song(
        id: Long,
        title: String,
        artist: String = "A",
        album: String = "X",
        albumId: Long = 1,
        albumArtist: String = "",
        addedAt: Long = 0,
        playedAt: Long = 0,
        year: Int = 0,
    ) = Song(
        id,
        "u$id",
        title,
        artist,
        artist.hashCode().toLong(),
        album,
        albumId,
        1000,
        year = year,
        albumArtist = albumArtist,
        addedAt = addedAt,
        playedAt = playedAt,
    )

    private val songs =
        listOf(
            song(1, "Bravo", artist = "Zed", album = "Hits", albumId = 1, albumArtist = "Various Artists", addedAt = 10, playedAt = 500),
            song(2, "Alpha", artist = "Amy feat. Zed", album = "Hits", albumId = 1, albumArtist = "Various Artists", addedAt = 30),
            song(3, "Charlie", artist = "Amy", album = "Solo", albumId = 2, addedAt = 20, playedAt = 100, year = 2020),
        )

    @Test
    fun `album artists group each album once under its credited artist`() {
        val artists = Library.albumArtists(songs)
        assertEquals(artists.size, artists.map { it.id }.toSet().size)
        assertEquals(listOf("Amy", "Various Artists"), artists.map { it.name })
        assertEquals(listOf("Hits"), artists.last().albums.map { it.title })
        assertEquals(2, artists.last().songs.size)
    }

    @Test
    fun `an untagged album takes its most common track artist`() {
        assertEquals("Amy", Library.albums(songs).first { it.title == "Solo" }.artist)
    }

    @Test
    fun `songs by recently added and played, local history counts`() {
        assertEquals(listOf(2L, 3L, 1L), Sorts.songs(songs, SortOrder.RecentlyAdded, emptyMap()).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), Sorts.songs(songs, SortOrder.RecentlyPlayed, emptyMap()).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), Sorts.songs(songs, SortOrder.RecentlyPlayed, mapOf(2L to 900L)).map { it.id })
    }

    @Test
    fun `albums by recently added use their newest song`() {
        val albums = Library.albums(songs)
        assertEquals(listOf("Hits", "Solo"), Sorts.albums(albums, SortOrder.RecentlyAdded, emptyMap()).map { it.title })
        assertEquals(listOf("Solo", "Hits"), Sorts.albums(albums, SortOrder.Year, emptyMap()).map { it.title })
    }
}
