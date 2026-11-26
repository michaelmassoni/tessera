package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikesTest {
    private fun song(
        id: Long,
        starredAt: Long = 0,
        server: Boolean = true,
    ) = Song(id, if (server) Subsonic.songUri("s$id") else "content://media/$id", "Song $id", "A", 1, "B", 2, 1000, starredAt = starredAt)

    @Test
    fun `this phone's change wins over the server's star`() {
        val starred = song(1, starredAt = 10)
        assertTrue(Likes.isLiked(starred, emptyMap()))
        assertFalse(Likes.isLiked(starred, mapOf(1L to LikeChange(false, 20))))
        assertTrue(Likes.isLiked(song(2), mapOf(2L to LikeChange(true, 20))))
        assertEquals(20L, Likes.likedAt(song(2), mapOf(2L to LikeChange(true, 20))))
    }

    @Test
    fun `liked songs, last liked first`() {
        val songs = listOf(song(1, starredAt = 10), song(2), song(3, starredAt = 30))
        val changes = mapOf(2L to LikeChange(true, 20), 3L to LikeChange(false, 40))
        assertEquals(listOf(2L, 1L), Likes.songs(songs, changes).map { it.id })
    }

    @Test
    fun `changes the server agrees with are settled, the phone's own songs never`() {
        val songs = listOf(song(1, starredAt = 10), song(2), song(3, server = false))
        val changes =
            mapOf(
                1L to LikeChange(true, 5), // the server has it starred now
                2L to LikeChange(true, 5), // not yet: still to send
                3L to LikeChange(true, 5), // on the phone: this is the only record
            )
        assertEquals(setOf(2L, 3L), Likes.settled(songs, changes).keys)
    }
}
