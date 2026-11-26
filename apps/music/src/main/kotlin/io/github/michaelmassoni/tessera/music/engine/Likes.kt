package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable

/** A like or unlike made on this phone, and when. */
@Serializable
data class LikeChange(
    val liked: Boolean,
    val at: Long,
)

/**
 * Liked songs (the heart). A server song's like is the server's star (Navidrome shows it as a
 * favourite), so it's the same everywhere; this phone's changes ([LikeChange] by song id) apply on
 * top until the server's library says the same. Songs on the phone have no server, so their changes
 * are the only record. Pure, so it's unit tested.
 */
object Likes {
    /** When [song] was liked (ms), or 0 when it isn't. */
    fun likedAt(
        song: Song,
        changes: Map<Long, LikeChange>,
    ): Long = changes[song.id]?.let { if (it.liked) it.at.coerceAtLeast(1) else 0 } ?: song.starredAt

    fun isLiked(
        song: Song,
        changes: Map<Long, LikeChange>,
    ) = likedAt(song, changes) > 0

    /** The liked songs, last liked first. */
    fun songs(
        songs: List<Song>,
        changes: Map<Long, LikeChange>,
    ): List<Song> =
        songs
            .map { it to likedAt(it, changes) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Song, Long>> { it.second }.thenBy { Library.sortKey(it.first.title) })
            .map { it.first }

    /**
     * After the server's library was read again: [changes] without the server songs it now agrees
     * with. What's left (and is a server song) still has to be sent.
     */
    fun settled(
        songs: List<Song>,
        changes: Map<Long, LikeChange>,
    ): Map<Long, LikeChange> {
        val server = songs.filter { Subsonic.songId(it.uri) != null }.associateBy { it.id }
        return changes.filterNot { (id, change) -> server[id]?.let { (it.starredAt > 0) == change.liked } ?: false }
    }
}
