package io.github.michaelmassoni.tessera.music.engine

/** A genre and its songs in album order (docs/music.md, Genres). */
data class SongGroup(
    /** Stable across launches (from [key]), so a page can be reopened after rotation. */
    val id: Long,
    /** The genre, normalised (case and accents folded). */
    val key: String,
    /** What to show: the most common spelling. */
    val name: String,
    val songs: List<Song>,
)

object Groups {
    /**
     * Genres, A to Z ignoring case and accents. Tags that differ only in case or spacing are one
     * genre, shown as its most common spelling; songs with several genres ("Rock; Pop", "Rock/Pop")
     * appear under each. Untagged songs are left out.
     */
    fun genres(songs: List<Song>): List<SongGroup> {
        val byKey = linkedMapOf<String, MutableList<Pair<String, Song>>>()
        songs.forEach { s ->
            splitGenres(s.genre).forEach { g -> byKey.getOrPut(Library.normalize(g)) { mutableListOf() } += g to s }
        }
        return byKey
            .map { (key, tagged) ->
                val name =
                    tagged
                        .groupingBy { it.first }
                        .eachCount()
                        .maxBy { it.value }
                        .key
                SongGroup(idOf("genre", key), key, name, ordered(tagged.map { it.second }.distinctBy { it.id }))
            }.sortedBy { Library.sortKey(it.name) }
    }

    fun splitGenres(tag: String): List<String> =
        tag
            .split(';', '/', '\u0000')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { Library.normalize(it) }

    /** Album by album (by album title), then track number, then title. */
    private fun ordered(songs: List<Song>) =
        songs.sortedWith(
            compareBy<Song> { Library.sortKey(it.album) }
                .thenBy { it.albumId }
                .thenBy { it.track }
                .thenBy { Library.sortKey(it.title) },
        )

    // FNV-1a, 64-bit: stable, unlike String.hashCode's 32 bits.
    private fun idOf(
        kind: String,
        key: String,
    ): Long {
        var h = -0x340d631b7bdddcdbL
        "$kind:$key".forEach { c ->
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }
}
