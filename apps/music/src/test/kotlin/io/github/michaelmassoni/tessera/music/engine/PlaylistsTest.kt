package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistsTest {
    private fun song(id: Long) = Song(id, "content://$id", "Song $id", "Artist", 1, "Album", 1, 1000)

    @Test fun `create, add without duplicates, remove, move, rename and delete`() {
        var (lists, road) = Playlists.create(emptyList(), "  Road trip ")
        assertEquals("Road trip", road.name)
        val (more, gym) = Playlists.create(lists, "Gym", listOf(5, 5, 6))
        lists = more
        assertEquals(listOf(5L, 6L), gym.songIds)
        assertEquals(2L, gym.id)
        lists = Playlists.add(lists, road.id, listOf(1, 2, 3, 2))
        lists = Playlists.add(lists, road.id, listOf(3, 4))
        assertEquals(listOf(1L, 2L, 3L, 4L), lists[0].songIds)
        lists = Playlists.remove(lists, road.id, 1)
        assertEquals(listOf(1L, 3L, 4L), lists[0].songIds)
        lists = Playlists.move(lists, road.id, 0, 2)
        assertEquals(listOf(3L, 4L, 1L), lists[0].songIds)
        lists = Playlists.rename(lists, gym.id, "Lifting")
        assertEquals(listOf("Lifting", "Road trip"), Playlists.sorted(lists).map { it.name })
        lists = Playlists.delete(lists, road.id)
        assertEquals(listOf(gym.id), lists.map { it.id })
        assertEquals(lists, Playlists.decode(Playlists.encode(lists)))
    }

    @Test fun `songs that left the library are skipped`() {
        val p = Playlist(1, "Mix", listOf(3, 9, 1))
        assertEquals(listOf(3L, 1L), Playlists.songs(p, listOf(song(1), song(3)).associateBy { it.id }).map { it.id })
    }

    @Test fun `saved queue round trip, clamped, and junk ignored`() {
        val q = SavedQueue(listOf(SavedItem("1", "content://1", "A")), index = 4, positionMs = -5, shuffle = true, repeatMode = 2)
        val back = SavedQueue.decode(q.encode())!!
        assertEquals(0, back.index)
        assertEquals(0L, back.positionMs)
        assertEquals(true, back.shuffle)
        assertNull(SavedQueue.decode("{nope"))
        assertNull(SavedQueue.decode(SavedQueue(emptyList()).encode()))
    }
}
