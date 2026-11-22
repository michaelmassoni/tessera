package io.github.michaelmassoni.tessera.music.engine

import java.text.Normalizer
import java.util.Locale

/** One track: from MediaStore.Audio, or from a Subsonic server ([uri] says which). */
@kotlinx.serialization.Serializable
data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMillis: Long,
    /** MediaStore TRACK: disc × 1000 + track number, 0 if unknown. */
    val track: Int = 0,
    val year: Int = 0,
    /** A server song's cover art id (`getCoverArt`); null for local songs, whose art comes from MediaStore. */
    val art: String? = null,
    /** The album's artist (blank when untagged: the album's most common track artist is used). */
    val albumArtist: String = "",
    /** When it was added to the library, ms since 1970 (0 if unknown). */
    val addedAt: Long = 0,
    /** When the server last saw it played, ms (0 if never or unknown). */
    val playedAt: Long = 0,
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
    /** The newest song's [Song.addedAt]. */
    val addedAt: Long = 0,
)

data class Artist(
    val id: Long,
    val name: String,
    val albums: List<Album>,
    val songs: List<Song>,
)

/** Grouping, ordering and searching of the music library. Pure, so it is unit tested. */
object Library {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val ARTICLE = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)

    /** "The Beatles" sorts as "beatles"; accents and case are ignored. */
    fun sortKey(text: String): String = normalize(text.trim()).replace(ARTICLE, "")

    fun songs(all: List<Song>): List<Song> = all.sortedWith(compareBy<Song> { sortKey(it.title) }.thenBy { sortKey(it.artist) })

    /** Disc then track number, then title for untagged files. */
    fun albumOrder(songs: List<Song>): List<Song> =
        songs.sortedWith(
            compareBy<Song> {
                if (it.track >
                    0
                ) {
                    it.track / 1000
                } else {
                    Int.MAX_VALUE
                }
            }.thenBy {
                if (it.track >
                    0
                ) {
                    it.track % 1000
                } else {
                    Int.MAX_VALUE
                }
            }.thenBy { sortKey(it.title) },
        )

    fun albums(all: List<Song>): List<Album> =
        all
            .groupBy { it.albumId }
            .map { (id, songs) ->
                // The tagged album artist; untagged, the most common track artist ("Various artists"
                // albums show the first).
                val artist =
                    mostCommon(songs.map { it.albumArtist }.filter { it.isNotBlank() })
                        ?: mostCommon(songs.map { it.artist }).orEmpty()
                Album(id, songs.first().album, artist, songs.maxOf { it.year }, albumOrder(songs), songs.maxOf { it.addedAt })
            }.sortedWith(compareBy<Album> { sortKey(it.title) }.thenBy { sortKey(it.artist) })

    private fun mostCommon(values: List<String>): String? =
        values
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    /**
     * Artists by album artist: each album once, under the artist it's credited to, so featured artists
     * and compilations don't each become an artist of their own.
     */
    fun albumArtists(all: List<Song>): List<Artist> =
        albums(all)
            .groupBy { normalize(it.artist.trim()) }
            .map { (key, albums) ->
                Artist(
                    id = albumArtistId(key),
                    name = albums.first().artist,
                    albums = albums.sortedByDescending { it.year },
                    songs = songs(albums.flatMap { it.songs }),
                )
            }.sortedBy { sortKey(it.name) }

    /** A stable id for an album artist (FNV-1a of the name), apart from track artists' ids. */
    fun albumArtistId(name: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (b in "albumartist/$name".toByteArray()) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return (hash and Long.MAX_VALUE).coerceAtLeast(1)
    }

    fun artists(all: List<Song>): List<Artist> {
        val albums = albums(all)
        return all
            .groupBy { it.artistId }
            .map { (id, songs) ->
                val albumIds = songs.map { it.albumId }.toSet()
                Artist(
                    id = id,
                    name = songs.first().artist,
                    albums = albums.filter { it.id in albumIds }.sortedByDescending { it.year },
                    songs = songs(songs),
                )
            }.sortedBy { sortKey(it.name) }
    }

    /** Songs whose title, artist or album contains every word of [query]. */
    fun search(
        all: List<Song>,
        query: String,
    ): List<Song> {
        val terms = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return songs(all.filter { s -> normalize("${s.title} ${s.artist} ${s.album}").let { text -> terms.all { it in text } } })
    }
}

/** "3:05", "1:02:03". */
fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
