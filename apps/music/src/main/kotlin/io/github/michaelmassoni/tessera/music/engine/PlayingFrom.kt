package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable

/** Where the queue came from, for "Playing from" on the player. */
@Serializable
data class PlayingFrom(
    val kind: Kind,
    val name: String = "",
    /** The album, artist or playlist id, for going back to it from the player (0 if unknown). */
    val id: Long = 0,
) {
    enum class Kind { Songs, Album, Artist, Playlist, Search, Genre }
}

/**
 * The album, artist or playlist id the queue came from, for going back to it from the player: by
 * [PlayingFrom.id], else by a name only one of them has (a queue saved before ids were kept). Null
 * for all songs and search, or when it's no longer in the library.
 */
fun PlayingFrom.sourceId(
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
): Long? {
    fun <T> find(
        items: List<T>,
        id: (T) -> Long,
        name: (T) -> String,
    ): Long? =
        items.firstOrNull { this.id != 0L && id(it) == this.id }?.let(id)
            ?: items
                .filter { Library.normalize(name(it).trim()) == Library.normalize(this.name.trim()) }
                .map(id)
                .distinct()
                .singleOrNull()
    return when (kind) {
        PlayingFrom.Kind.Album -> find(albums, { it.id }, { it.title })

        PlayingFrom.Kind.Artist -> find(artists, { it.id }, { it.name })

        PlayingFrom.Kind.Playlist -> find(playlists, { it.id }, { it.name })

        PlayingFrom.Kind.Songs, PlayingFrom.Kind.Search -> null

        // Genres aren't stored: the page shows nothing if the genre has gone.
        PlayingFrom.Kind.Genre -> id.takeIf { it != 0L }
    }
}
