package io.github.michaelmassoni.tessera.music.engine

/** How a list is ordered. Not every page offers every order. */
enum class SortOrder {
    /** By title (songs, albums, playlists) or name (artists). */
    Name,
    Artist,
    Album,
    Year,

    /** Newest first. */
    RecentlyAdded,

    /** Most recently played first; never played last. */
    RecentlyPlayed,
}

/** Which orders each page offers, first is the default. */
object Sorts {
    val songs = listOf(SortOrder.Name, SortOrder.Artist, SortOrder.Album, SortOrder.RecentlyAdded, SortOrder.RecentlyPlayed)
    val albums = listOf(SortOrder.Name, SortOrder.Artist, SortOrder.Year, SortOrder.RecentlyAdded, SortOrder.RecentlyPlayed)
    val artists = listOf(SortOrder.Name, SortOrder.RecentlyAdded, SortOrder.RecentlyPlayed)
    val playlists = listOf(SortOrder.Name, SortOrder.RecentlyPlayed)

    /**
     * When a song was last played: the server's record or this phone's ([history], song id to ms),
     * whichever is later.
     */
    private fun played(
        song: Song,
        history: Map<Long, Long>,
    ) = maxOf(song.playedAt, history[song.id] ?: 0)

    private fun lastPlayed(
        songs: List<Song>,
        history: Map<Long, Long>,
    ) = songs.maxOfOrNull { played(it, history) } ?: 0

    fun songs(
        songs: List<Song>,
        order: SortOrder,
        history: Map<Long, Long>,
    ): List<Song> =
        when (order) {
            SortOrder.Artist -> {
                songs.sortedWith(
                    compareBy<Song> { Library.sortKey(it.artist) }
                        .thenBy { Library.sortKey(it.album) }
                        .thenBy { it.track }
                        .thenBy { Library.sortKey(it.title) },
                )
            }

            SortOrder.Album -> {
                songs.sortedWith(compareBy<Song> { Library.sortKey(it.album) }.thenBy { it.track }.thenBy { Library.sortKey(it.title) })
            }

            SortOrder.RecentlyAdded -> {
                songs.sortedWith(compareByDescending<Song> { it.addedAt }.thenBy { Library.sortKey(it.title) })
            }

            SortOrder.RecentlyPlayed -> {
                songs.sortedWith(compareByDescending<Song> { played(it, history) }.thenBy { Library.sortKey(it.title) })
            }

            else -> {
                Library.songs(songs)
            }
        }

    fun albums(
        albums: List<Album>,
        order: SortOrder,
        history: Map<Long, Long>,
    ): List<Album> =
        when (order) {
            SortOrder.Artist -> {
                albums.sortedWith(
                    compareBy<Album> {
                        Library.sortKey(it.artist)
                    }.thenBy { it.year }.thenBy { Library.sortKey(it.title) },
                )
            }

            SortOrder.Year -> {
                albums.sortedWith(compareByDescending<Album> { it.year }.thenBy { Library.sortKey(it.title) })
            }

            SortOrder.RecentlyAdded -> {
                albums.sortedWith(compareByDescending<Album> { it.addedAt }.thenBy { Library.sortKey(it.title) })
            }

            SortOrder.RecentlyPlayed -> {
                albums.sortedWith(
                    compareByDescending<Album> {
                        lastPlayed(it.songs, history)
                    }.thenBy { Library.sortKey(it.title) },
                )
            }

            else -> {
                albums.sortedWith(compareBy<Album> { Library.sortKey(it.title) }.thenBy { Library.sortKey(it.artist) })
            }
        }

    fun artists(
        artists: List<Artist>,
        order: SortOrder,
        history: Map<Long, Long>,
    ): List<Artist> =
        when (order) {
            SortOrder.RecentlyAdded -> {
                artists.sortedWith(
                    compareByDescending<Artist> { a ->
                        a.songs.maxOfOrNull { it.addedAt } ?: 0
                    }.thenBy { Library.sortKey(it.name) },
                )
            }

            SortOrder.RecentlyPlayed -> {
                artists.sortedWith(
                    compareByDescending<Artist> {
                        lastPlayed(it.songs, history)
                    }.thenBy { Library.sortKey(it.name) },
                )
            }

            else -> {
                artists.sortedBy { Library.sortKey(it.name) }
            }
        }

    fun playlists(
        playlists: List<Playlist>,
        order: SortOrder,
        byId: Map<Long, Song>,
        history: Map<Long, Long>,
    ): List<Playlist> =
        when (order) {
            SortOrder.RecentlyPlayed -> {
                playlists.sortedWith(
                    compareByDescending<Playlist> { p ->
                        lastPlayed(p.songIds.mapNotNull(byId::get), history)
                    }.thenBy { Library.sortKey(it.name) },
                )
            }

            else -> {
                Playlists.sorted(playlists)
            }
        }
}
