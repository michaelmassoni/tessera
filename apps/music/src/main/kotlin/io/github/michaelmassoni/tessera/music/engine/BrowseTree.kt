package io.github.michaelmassoni.tessera.music.engine

/**
 * The library as Android Auto (and other media browsers) sees it: a root with Playlists, Albums,
 * Artists and Songs, down to playable songs. A song's id says where it was picked from, so
 * playing it queues the rest of that album, playlist or artist after it. Pure, so it's unit tested.
 */
class BrowseTree(
    songs: List<Song>,
    private val playlists: List<Playlist>,
    albumArtists: Boolean,
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
    )

    enum class Section { Playlists, Albums, Artists, Songs }

    private val allSongs = Library.songs(songs)
    private val albums = Library.albums(songs)
    private val artists = if (albumArtists) Library.albumArtists(songs) else Library.artists(songs)
    private val byId = songs.associateBy { it.id }

    fun root(): List<Node> =
        listOf(
            Node(PLAYLISTS, Section.Playlists.name),
            Node(ALBUMS, Section.Albums.name, grid = true),
            Node(ARTISTS, Section.Artists.name),
            Node(SONGS, Section.Songs.name),
        )

    /** The entries under [id], or null for an id that isn't a list. */
    fun children(id: String): List<Node>? =
        when {
            id == ROOT -> {
                root()
            }

            id == PLAYLISTS -> {
                Playlists.sorted(playlists).map { Node("$PLAYLIST${it.id}", it.name, albumId = cover(it)) }
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
                albums.firstOrNull { "$ALBUM${it.id}" == id }?.let { playable("album:${it.id}", it.songs) }
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

                context.startsWith("album:") -> {
                    albums.firstOrNull { it.id.toString() == context.removePrefix("album:") }?.let {
                        it.songs to PlayingFrom(PlayingFrom.Kind.Album, it.title)
                    } ?: return null
                }

                context.startsWith("artist:") -> {
                    artists.firstOrNull { it.id.toString() == context.removePrefix("artist:") }?.let {
                        it.songs to PlayingFrom(PlayingFrom.Kind.Artist, it.name)
                    } ?: return null
                }

                context.startsWith("playlist:") -> {
                    playlists.firstOrNull { it.id.toString() == context.removePrefix("playlist:") }?.let {
                        songsOf(it) to PlayingFrom(PlayingFrom.Kind.Playlist, it.name)
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
            }?.let { return Queue(it.songs, 0, false, PlayingFrom(PlayingFrom.Kind.Album, it.title)) }
        artists
            .firstOrNull {
                Library.normalize(it.name) == q
            }?.let { return Queue(it.songs, 0, true, PlayingFrom(PlayingFrom.Kind.Artist, it.name)) }
        val songs = Library.search(allSongs, query)
        return if (songs.isEmpty()) null else Queue(songs, 0, false, PlayingFrom(PlayingFrom.Kind.Search, query))
    }

    /** Songs matching [query], for the browser's search screen. */
    fun searchNodes(query: String) = playable("search", Library.search(allSongs, query))

    companion object {
        const val ROOT = "root"
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
