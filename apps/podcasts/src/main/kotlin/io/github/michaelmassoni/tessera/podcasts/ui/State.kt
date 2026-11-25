package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.runtime.Immutable
import io.github.michaelmassoni.tessera.podcasts.SleepTimer
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.EpisodeState
import io.github.michaelmassoni.tessera.podcasts.engine.Library
import io.github.michaelmassoni.tessera.podcasts.engine.SearchResult
import io.github.michaelmassoni.tessera.podcasts.engine.Show

/** Everything the screens show. */
@Immutable
data class PodcastsState(
    val loaded: Boolean = true,
    val library: Library = Library(),
    /** Episodes of library shows and previews, by show id. */
    val episodes: Map<String, List<Episode>> = emptyMap(),
    /** Shows opened from Search, not in the library. */
    val previews: Map<String, Show> = emptyMap(),
    /** Downloads under way: episode id to percent (-1 while waiting to start). */
    val downloading: Map<String, Int> = emptyMap(),
    val refreshing: Boolean = false,
    val player: PlayerUi = PlayerUi(),
    /** "Now", for "new" and dates; fixed in previews. */
    val now: Long = System.currentTimeMillis(),
) {
    fun show(id: String): Show? = library.show(id) ?: previews[id]

    fun episode(id: String): Episode? = episodes[id.substringBefore(':')]?.firstOrNull { it.id == id }

    fun state(id: String): EpisodeState = library.state(id)
}

/** The player as the screens see it. */
@Immutable
data class PlayerUi(
    val episodeId: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val speed: Float = 1f,
    val queue: List<String> = emptyList(),
    val sleep: SleepTimer.Sleep = SleepTimer.Sleep.Off,
)

/** What the screens can do. Suspending ones report problems by throwing. */
interface PodcastsActions {
    fun play(episode: Episode)

    fun togglePlay()

    fun playNext(episode: Episode)

    fun addToQueue(episode: Episode)

    fun removeFromQueue(id: String)

    /** Indices into the whole queue (the episode playing is 0). */
    fun moveInQueue(
        from: Int,
        to: Int,
    )

    fun seekTo(positionMs: Long)

    fun back()

    fun forward()

    fun setSpeed(speed: Float)

    fun setSleep(sleep: SleepTimer.Sleep)

    fun markPlayed(
        id: String,
        played: Boolean,
    )

    fun download(id: String)

    fun cancelDownload(id: String)

    fun deleteDownload(id: String)

    fun follow(showId: String)

    fun unfollow(showId: String)

    fun refresh()

    fun openSettings()

    /** Searches the directories. @throws java.io.IOException when offline. */
    suspend fun search(terms: String): List<SearchResult>

    /** Opens the show at [feedUrl] (fetching it if it's new). @return its id. @throws io.github.michaelmassoni.tessera.podcasts.data.AddException */
    suspend fun openFeed(feedUrl: String): String

    /** "Add by URL". @return the show's id. @throws io.github.michaelmassoni.tessera.podcasts.data.AddException */
    suspend fun openAddress(input: String): String

    /** The episode's show notes (HTML), or "". */
    suspend fun notes(episodeId: String): String
}
