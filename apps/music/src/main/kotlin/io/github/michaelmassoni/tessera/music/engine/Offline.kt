package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable

/** What's downloaded for offline listening: albums and playlists (Liked songs is [Offline.LIKED]). */
@Serializable
data class OfflinePins(
    val albums: Set<Long> = emptySet(),
    val playlists: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = albums.isEmpty() && playlists.isEmpty()

    fun has(
        kind: OfflineKind,
        id: Long,
    ) = if (kind == OfflineKind.Album) id in albums else id in playlists

    fun with(
        kind: OfflineKind,
        id: Long,
        on: Boolean,
    ): OfflinePins =
        if (kind == OfflineKind.Album) {
            copy(albums = if (on) albums + id else albums - id)
        } else {
            copy(playlists = if (on) playlists + id else playlists - id)
        }
}

enum class OfflineKind { Album, Playlist }

/** What a download pass does: fetch these songs (in order), delete these server ids. */
data class OfflinePlan(
    val download: List<Song>,
    val delete: Set<String>,
)

/**
 * Downloads for offline listening (docs/music.md, Downloads). Pure: which server songs the pins
 * need, and what to fetch and delete to get there. A song needed by two pins is kept until neither
 * needs it.
 */
object Offline {
    /** The id of the Liked songs list (not a server playlist), so it can be pinned like one. */
    const val LIKED = -1L

    /** The server songs [pins] need, each once, in pin order (albums, then playlists). */
    fun wanted(
        pins: OfflinePins,
        songs: List<Song>,
        playlists: List<Playlist>,
        likes: Map<Long, LikeChange>,
    ): List<Song> {
        val byId = songs.associateBy { it.id }
        val out = LinkedHashMap<Long, Song>()
        pins.albums.forEach { album ->
            songs
                .filter { it.albumId == album }
                .sortedWith(compareBy({ it.track }, { Library.sortKey(it.title) }))
                .forEach { out.putIfAbsent(it.id, it) }
        }
        pins.playlists.forEach { id ->
            val list =
                if (id == LIKED) {
                    Likes.songs(songs, likes)
                } else {
                    playlists.firstOrNull { it.id == id }?.let { Playlists.songs(it, byId) }.orEmpty()
                }
            list.forEach { out.putIfAbsent(it.id, it) }
        }
        return out.values.filter { serverId(it) != null }
    }

    /** What to fetch (wanted, not on the phone) and delete (on the phone, no longer wanted). */
    fun plan(
        wanted: List<Song>,
        onPhone: Set<String>,
    ): OfflinePlan {
        val wantedIds = wanted.mapNotNull(::serverId).toSet()
        return OfflinePlan(
            download = wanted.filter { serverId(it) !in onPhone },
            delete = onPhone - wantedIds,
        )
    }

    /** How many of [songs] are on the phone. */
    fun downloadedCount(
        songs: List<Song>,
        onPhone: Set<String>,
    ) = songs.count { serverId(it) in onPhone }

    fun isDownloaded(
        song: Song,
        onPhone: Set<String>,
    ) = serverId(song)?.let { it in onPhone } == true

    /** The server's id for [song], or null for a song on the phone. */
    fun serverId(song: Song): String? = Subsonic.songId(song.uri)
}
