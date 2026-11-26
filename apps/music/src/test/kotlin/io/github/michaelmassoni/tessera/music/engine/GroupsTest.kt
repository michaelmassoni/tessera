package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GroupsTest {
    private fun song(
        id: Long,
        genre: String = "",
        folder: String = "",
        album: String = "A",
        track: Int = 0,
    ) = Song(id, "u$id", "Song $id", "Artist", 1, album, album.hashCode().toLong(), 1000, track = track, genre = genre, folder = folder)

    @Test
    fun `genres merge case and spacing, use the common spelling, and skip untagged`() {
        val songs = listOf(song(1, "Rock"), song(2, "rock "), song(3, "Rock"), song(4, "Électro"), song(5))
        val g = Groups.genres(songs)
        assertEquals(listOf("Électro", "Rock"), g.map { it.name })
        assertEquals(listOf(1L, 2L, 3L), g[1].songs.map { it.id })
    }

    @Test
    fun `a song with several genres is under each`() {
        val g = Groups.genres(listOf(song(1, "Rock; Pop"), song(2, "Pop/Rock"), song(3, "Jazz")))
        assertEquals(listOf("Jazz", "Pop", "Rock"), g.map { it.name })
        assertEquals(listOf(1L, 2L), g.first { it.name == "Pop" }.songs.map { it.id })
    }

    @Test
    fun `folders group by path, name by last part, songs in album and track order`() {
        val songs =
            listOf(
                song(1, folder = "Music/B/", album = "Z", track = 2),
                song(2, folder = "Music/B", album = "Z", track = 1),
                song(3, folder = "Music/a"),
                song(4),
            )
        val f = Groups.folders(songs)
        assertEquals(listOf("Music/a", "Music/B"), f.map { it.key })
        assertEquals(listOf("a", "B"), f.map { it.name })
        assertEquals(listOf(2L, 1L), f[1].songs.map { it.id })
    }

    @Test
    fun `ids are stable and differ between genres and folders`() {
        val a = Groups.genres(listOf(song(1, "Rock"))).single().id
        val b = Groups.genres(listOf(song(9, "ROCK"))).single().id
        assertEquals(a, b)
        assertNotEquals(a, Groups.folders(listOf(song(1, folder = "rock"))).single().id)
    }
}
