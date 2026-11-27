package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTest {
    private fun song(
        id: Long,
        album: Long,
        track: Int = 0,
        server: Boolean = true,
        starred: Long = 0,
    ) = Song(
        id,
        if (server) Subsonic.songUri("s$id") else "content://media/$id",
        "Song $id",
        "Artist",
        1,
        "Album $album",
        album,
        1000,
        track = track,
        starredAt = starred,
    )

    private val songs =
        listOf(
            song(1, album = 10, track = 2),
            song(2, album = 10, track = 1),
            song(3, album = 20, starred = 5),
            song(4, album = 20, server = false),
        )
    private val playlists = listOf(Playlist(100, "Road trip", songIds = listOf(3, 1)))

    @Test
    fun `albums in track order, then playlists, each song once, server songs only`() {
        val pins = OfflinePins(albums = setOf(10, 20), playlists = setOf(100))
        assertEquals(listOf(2L, 1L, 3L), Offline.wanted(pins, songs, playlists, emptyMap()).map { it.id })
    }

    @Test
    fun `liked songs pin follows likes, including changes made on the phone`() {
        val pins = OfflinePins(playlists = setOf(Offline.LIKED))
        assertEquals(listOf(3L), Offline.wanted(pins, songs, playlists, emptyMap()).map { it.id })
        val likedHere = mapOf(1L to LikeChange(liked = true, at = 9), 3L to LikeChange(liked = false, at = 9))
        assertEquals(listOf(1L), Offline.wanted(pins, songs, playlists, likedHere).map { it.id })
    }

    @Test
    fun `plan fetches what is missing and deletes what nothing needs`() {
        val wanted = Offline.wanted(OfflinePins(albums = setOf(10)), songs, playlists, emptyMap())
        val plan = Offline.plan(wanted, onPhone = setOf("s1", "s3"))
        assertEquals(listOf(2L), plan.download.map { it.id })
        assertEquals(setOf("s3"), plan.delete)
    }

    @Test
    fun `a song two pins need stays until neither does`() {
        val both = OfflinePins(albums = setOf(10), playlists = setOf(100))
        val albumOnly = both.with(OfflineKind.Playlist, 100, on = false)
        val onPhone = setOf("s1", "s2", "s3")
        assertEquals(setOf("s3"), Offline.plan(Offline.wanted(albumOnly, songs, playlists, emptyMap()), onPhone).delete)
        assertEquals(emptySet<String>(), Offline.plan(Offline.wanted(both, songs, playlists, emptyMap()), onPhone).delete)
    }

    @Test
    fun `pins toggle and report what they hold`() {
        val pins = OfflinePins().with(OfflineKind.Album, 10, on = true)
        assertTrue(pins.has(OfflineKind.Album, 10))
        assertFalse(pins.has(OfflineKind.Playlist, 10))
        assertTrue(pins.with(OfflineKind.Album, 10, on = false).isEmpty)
        assertEquals(1, Offline.downloadedCount(songs, setOf("s1")))
        assertTrue(Offline.isDownloaded(songs[0], setOf("s1")))
    }
}
