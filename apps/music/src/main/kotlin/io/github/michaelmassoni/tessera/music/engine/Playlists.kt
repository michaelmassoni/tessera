package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A playlist of library songs, by MediaStore id. Kept by the app, not in MediaStore. */
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
)

/** Editing playlists. Pure: every change returns the new list. */
object Playlists {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Playlist.serializer())

    fun encode(playlists: List<Playlist>): String = json.encodeToString(serializer, playlists)

    fun decode(text: String): List<Playlist> =
        try {
            json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    fun create(
        playlists: List<Playlist>,
        name: String,
        songIds: List<Long> = emptyList(),
    ): Pair<List<Playlist>, Playlist> {
        val playlist = Playlist((playlists.maxOfOrNull { it.id } ?: 0) + 1, name.trim(), songIds.distinct())
        return playlists + playlist to playlist
    }

    fun rename(
        playlists: List<Playlist>,
        id: Long,
        name: String,
    ) = playlists.map { if (it.id == id) it.copy(name = name.trim()) else it }

    fun delete(
        playlists: List<Playlist>,
        id: Long,
    ) = playlists.filterNot { it.id == id }

    /** Adds songs at the end, skipping ones already in the playlist. */
    fun add(
        playlists: List<Playlist>,
        id: Long,
        songIds: List<Long>,
    ) = playlists.map { p -> if (p.id == id) p.copy(songIds = p.songIds + songIds.filterNot { it in p.songIds }.distinct()) else p }

    fun remove(
        playlists: List<Playlist>,
        id: Long,
        index: Int,
    ) = playlists.map { p ->
        if (p.id == id &&
            index in p.songIds.indices
        ) {
            p.copy(songIds = p.songIds.filterIndexed { i, _ -> i != index })
        } else {
            p
        }
    }

    fun move(
        playlists: List<Playlist>,
        id: Long,
        from: Int,
        to: Int,
    ) = playlists.map { p ->
        if (p.id != id || from !in p.songIds.indices || to !in p.songIds.indices) {
            p
        } else {
            p.copy(songIds = p.songIds.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** The playlist's songs that are still in the library, in playlist order. */
    fun songs(
        playlist: Playlist,
        library: Map<Long, Song>,
    ): List<Song> = playlist.songIds.mapNotNull(library::get)

    /** Playlists sorted for display. */
    fun sorted(playlists: List<Playlist>) = playlists.sortedBy { Library.sortKey(it.name) }
}
