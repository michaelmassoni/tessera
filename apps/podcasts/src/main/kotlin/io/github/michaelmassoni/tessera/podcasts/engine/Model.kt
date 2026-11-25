package io.github.michaelmassoni.tessera.podcasts.engine

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** A podcast: followed, or kept because one of its episodes is in Up Next. */
@Serializable
data class Show(
    /** Made from the feed URL it was first added with; stays when the feed moves. */
    val id: String,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val link: String? = null,
    val followed: Boolean = false,
    val etag: String? = null,
    val lastModified: String? = null,
    /** When the feed was last fetched successfully (ms), 0 for never. */
    val refreshedAt: Long = 0,
    /** The newest episode's date, for sorting the library. */
    val latestAt: Long = 0,
)

/** One episode, as the feed describes it. Show notes are kept apart (see [EpisodeNotes]). */
@Serializable
data class Episode(
    /** `<show id>:<hash of guid>`. */
    val id: String,
    val showId: String,
    val title: String,
    /** Plain text, short: for lists. */
    val summary: String = "",
    val publishedAt: Long = 0,
    /** From the feed; 0 when it doesn't say (the player learns it). */
    val durationMs: Long = 0,
    val audioUrl: String,
    val audioType: String? = null,
    val audioBytes: Long = 0,
    /** The episode's own cover, when it has one. */
    val artworkUrl: String? = null,
    val link: String? = null,
)

/** What the person has done with an episode. */
@Serializable
data class EpisodeState(
    val positionMs: Long = 0,
    /** Learnt while playing, when the feed doesn't say. */
    val durationMs: Long = 0,
    val played: Boolean = false,
    val playedAt: Long = 0,
    /** File name of the download in the downloads folder. */
    val download: String? = null,
)

/** The person's podcasts: what's kept in `library.json`. */
@Serializable
data class Library(
    val shows: List<Show> = emptyList(),
    /** By episode id. Only episodes with something to remember. */
    val states: Map<String, EpisodeState> = emptyMap(),
    /** Episode ids: the episode playing (or paused) first, then the queue. */
    val upNext: List<String> = emptyList(),
    val speed: Float = 1f,
    /** When every feed was last refreshed together (ms). */
    val refreshedAt: Long = 0,
) {
    fun show(id: String) = shows.firstOrNull { it.id == id }

    fun state(episodeId: String) = states[episodeId] ?: EpisodeState()

    val followed: List<Show> get() = shows.filter { it.followed }

    fun withState(
        episodeId: String,
        change: (EpisodeState) -> EpisodeState,
    ): Library {
        val updated = change(state(episodeId))
        return copy(states = if (updated == EpisodeState()) states - episodeId else states + (episodeId to updated))
    }
}

object Ids {
    fun show(feedUrl: String) = hash(feedUrl.trim()).take(12)

    fun episode(
        showId: String,
        guid: String,
    ) = "$showId:${hash(guid).take(16)}"

    fun showOf(episodeId: String) = episodeId.substringBefore(':')

    private fun hash(text: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
