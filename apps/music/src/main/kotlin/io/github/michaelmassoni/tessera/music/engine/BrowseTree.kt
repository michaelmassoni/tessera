package io.github.michaelmassoni.tessera.music.engine

/**
 * The library as Android Auto (and other media browsers) sees it, down to playable songs. Auto
 * shows at most four root entries as tabs, so the root is Recent (recently played and recently
 * added albums), Playlists (Liked songs and All songs first), Albums and Artists. A song's id says
 * where it was picked from, so playing it queues the rest of that album, playlist or artist after
 * it. Pure, so it's unit tested.
 */
class BrowseTree(
    songs: List<Song>,
    private val playlists: List<Playlist>,
    albumArtists: Boolean,
    /** This phone's play history (song id to ms), for Recently played. */
    private val history: Map<Long, Long> = emptyMap(),
    /** This phone's likes, for Liked songs ([Likes]). */
    likes: Map<Long, LikeChange> = emptyMap(),
) {
    /** One entry in a browse list. */
    data class Node(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val playable: Boolean = false,
        val albumId: Long = 0,
        val song: Song? = null,
        /** Shown as a grid of covers (albums) rather than a list. */
        val grid: Boolean = false,
        /** The heading this entry is listed under ([RECENTLY_PLAYED], [RECENTLY_ADDED]), blank for none. */
        val group: String = "",
    )

    private val allSongs = Library.songs(songs)
    private val albums = Library.albums(songs)
    private val artists = if (albumArtists) Library.albumArtists(songs) else Library.artists(songs)
    private val byId = songs.associateBy { it.id }
    private val liked = Likes.songs(songs, likes)

    /** A song by id, for its like. */
    fun song(id: Long): Song? = byId[id]

    /** Titles are replaced with localised ones by the service (by id). */
    fun root(): List<Node> =
        listOf(
            Node(RECENT, "Recent", grid = true),
            Node(PLAYLISTS, "Playlists"),
            Node(ALBUMS, "Albums", grid = true),
            Node(ARTISTS, "Artists"),
        )

    /** Recently played albums (that have been played), then recently added ones, [RECENT_COUNT] of each. */
    private fun recent(): List<Node> {
        // An album can be under both headings: Auto keys a list by id, so the second one gets a suffix.
        fun node(
            a: Album,
            group: String,
        ) = Node(
            "$ALBUM${a.id}" +
                if (group ==
                    RECENTLY_ADDED
                ) {
                    ADDED_SUFFIX
                } else {
                    ""
                },
            a.title,
            a.artist,
            albumId = a.id,
            grid = true,
            group = group,
        )
        val played =
            Sorts
                .albums(albums, SortOrder.RecentlyPlayed, history)
                .filter { a -> a.songs.any { maxOf(it.playedAt, history[it.id] ?: 0) > 0 } }
                .take(RECENT_COUNT)
        val added = Sorts.albums(albums, SortOrder.RecentlyAdded, history).filter { it.addedAt > 0 }.take(RECENT_COUNT)
        return played.map { node(it, RECENTLY_PLAYED) } + added.map { node(it, RECENTLY_ADDED) }
    }

    /** The entries under [id], or null for an id that isn't a list. */
    fun children(id: String): List<Node>? =
        when {
            id == ROOT -> {
                root()
            }

            id == RECENT -> {
                recent()
            }

            id == PLAYLISTS -> {
                listOf(
                    Node(LIKED, "Liked songs", albumId = liked.firstOrNull()?.albumId ?: 0),
                    Node(SONGS, "All songs"),
                ) + Playlists.sorted(playlists).map { Node("$PLAYLIST${it.id}", it.name, albumId = cover(it)) }
            }

            id == LIKED -> {
                withShuffle("liked", liked)
            }

            id == ALBUMS -> {
                albums.map { Node("$ALBUM${it.id}", it.title, it.artist, albumId = it.id, grid = true) }
            }

            id == ARTISTS -> {
                artists.map { a -> Node("$ARTIST${a.id}", a.name, albumId = a.albums.firstOrNull()?.id ?: 0) }
            }

            id == SONGS -> {
                withShuffle("all", allSongs)
            }

            id.startsWith(ALBUM) -> {
                albums.firstOrNull { "$ALBUM${it.id}" == id.removeSuffix(ADDED_SUFFIX) }?.let { playable("album:${it.id}", it.songs) }
            }

            id.startsWith(ARTIST) -> {
                artists.firstOrNull { "$ARTIST${it.id}" == id }?.let { withShuffle("artist:${it.id}", it.songs) }
            }

            id.startsWith(PLAYLIST) -> {
                playlists.firstOrNull { "$PLAYLIST${it.id}" == id }?.let { withShuffle("playlist:${it.id}", songsOf(it)) }
            }

            else -> {
                null
            }
        }

    private fun cover(p: Playlist) = p.songIds.firstNotNullOfOrNull { byId[it]?.albumId } ?: 0

    private fun songsOf(p: Playlist) = p.songIds.mapNotNull(byId::get)

    private fun playable(
        context: String,
        songs: List<Song>,
    ) = songs.map { s -> Node("$SONG$context/${s.id}", s.title, s.artist, playable = true, albumId = s.albumId, song = s) }

    private fun withShuffle(
        context: String,
        songs: List<Song>,
    ) = if (songs.isEmpty()) {
        emptyList()
    } else {
        listOf(Node("$SHUFFLE$context", SHUFFLE_TITLE, playable = true)) + playable(context, songs)
    }

    /** What to queue for a playable id: the songs of its list, where to start, and whether to shuffle. */
    data class Queue(
        val songs: List<Song>,
        val start: Int,
        val shuffle: Boolean,
        val from: PlayingFrom,
    )

    fun queueFor(id: String): Queue? {
        val shuffle = id.startsWith(SHUFFLE)
        val rest =
            when {
                shuffle -> id.removePrefix(SHUFFLE)
                id.startsWith(SONG) -> id.removePrefix(SONG)
                else -> return null
            }
        val context = if (shuffle) rest else rest.substringBeforeLast('/')
        val songId = if (shuffle) null else rest.substringAfterLast('/').toLongOrNull()
        val (songs, from) =
            when {
                context == "all" -> {
                    allSongs to PlayingFrom(PlayingFrom.Kind.Songs)
                }

                context == "liked" -> {
                    liked to PlayingFrom(PlayingFrom.Kind.Playlist, LIKED_TITLE)
                }

                context.startsWith("album:") -> {
                    albums.firstOrNull { it.id.toString() == context.removePrefix("album:") }?.let {
                        it.songs to PlayingFrom(PlayingFrom.Kind.Album, it.title, it.id)
                    } ?: return null
                }

                context.startsWith("artist:") -> {
                    artists.firstOrNull { it.id.toString() == context.removePrefix("artist:") }?.let {
                        it.songs to PlayingFrom(PlayingFrom.Kind.Artist, it.name, it.id)
                    } ?: return null
                }

                context.startsWith("playlist:") -> {
                    playlists.firstOrNull { it.id.toString() == context.removePrefix("playlist:") }?.let {
                        songsOf(it) to PlayingFrom(PlayingFrom.Kind.Playlist, it.name, it.id)
                    } ?: return null
                }

                else -> {
                    return null
                }
            }
        if (songs.isEmpty()) return null
        val start = if (songId == null) 0 else songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
        return Queue(songs, start, shuffle, from)
    }

    /**
     * A voice request ("play Lanterns", "play Juniper"): an album or artist whose name matches
     * plays whole; otherwise the songs that match. Empty means everything, shuffled.
     */
    fun search(query: String): Queue? {
        val q = Library.normalize(query.trim())
        if (q.isEmpty()) return Queue(allSongs, 0, true, PlayingFrom(PlayingFrom.Kind.Songs))
        albums
            .firstOrNull {
                Library.normalize(it.title) == q
            }?.let { return Queue(it.songs, 0, false, PlayingFrom(PlayingFrom.Kind.Album, it.title, it.id)) }
        artists
            .firstOrNull {
                Library.normalize(it.name) == q
            }?.let { return Queue(it.songs, 0, true, PlayingFrom(PlayingFrom.Kind.Artist, it.name, it.id)) }
        val songs = Library.search(allSongs, query)
        return if (songs.isEmpty()) null else Queue(songs, 0, false, PlayingFrom(PlayingFrom.Kind.Search, query))
    }

    /** Songs matching [query], for the browser's search screen. */
    fun searchNodes(query: String) = playable("search", Library.search(allSongs, query))

    companion object {
        const val ROOT = "root"
        const val RECENT = "recent"
        const val LIKED = "liked"
        const val RECENTLY_PLAYED = "recently_played"
        const val RECENTLY_ADDED = "recently_added"
        const val RECENT_COUNT = 12
        private const val ADDED_SUFFIX = "#added"

        /** Replaced with the localised "Liked songs" by the service and the app. */
        const val LIKED_TITLE = "Liked songs"
        const val PLAYLISTS = "playlists"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val SONGS = "songs"
        const val ALBUM = "album/"
        const val ARTIST = "artist/"
        const val PLAYLIST = "playlist/"
        const val SONG = "song/"
        const val SHUFFLE = "shuffle/"

        /** Replaced with the localised "Shuffle" by the service. */
        const val SHUFFLE_TITLE = "Shuffle"
    }
}
