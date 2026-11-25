package io.github.michaelmassoni.tessera.podcasts.data

import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.FeedContent
import io.github.michaelmassoni.tessera.podcasts.engine.Library
import io.github.michaelmassoni.tessera.podcasts.engine.Show
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** A show being looked at that isn't in the library (from Search). Kept in memory only. */
data class Preview(
    val show: Show,
    val content: FeedContent,
)

/**
 * The one copy of the podcasts in this process, shared by the app, the player and background work.
 *
 * - [library]: shows, the person's episode states, Up Next, speed (`files/library.json`).
 * - [episodes]: each library show's episodes (`files/feeds/<show>.json`), loaded in the background
 *   at start ([loaded] turns true).
 * - Show notes (`files/notes/<show>.json`) are read when asked for.
 */
class PodcastStore(
    private val dir: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writes = Any()
    private val libraryFile = File(dir, "library.json")
    private val feedsDir = File(dir, "feeds")
    private val notesDir = File(dir, "notes")

    private val _library = MutableStateFlow(read(libraryFile, Library.serializer()) ?: Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _episodes = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    val episodes: StateFlow<Map<String, List<Episode>>> = _episodes.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, Preview>>(emptyMap())
    val previews: StateFlow<Map<String, Preview>> = _previews.asStateFlow()

    private val episodeList = ListSerializer(Episode.serializer())
    private val notesMap = MapSerializer(String.serializer(), String.serializer())

    init {
        scope.launch(Dispatchers.IO) {
            val loaded =
                _library.value.shows.associate { show ->
                    show.id to (read(File(feedsDir, "${show.id}.json"), episodeList) ?: emptyList())
                }
            // Anything saved meanwhile (a refresh) is newer than the files just read.
            _episodes.update { saved -> loaded + saved }
            _loaded.value = true
        }
    }

    /** Applies [change] to the library and saves it. Thread-safe; changes apply in the order made. */
    fun update(change: (Library) -> Library) {
        var updated: Library
        synchronized(writes) {
            updated = change(_library.value)
            if (updated == _library.value) return
            _library.value = updated
        }
        scope.launch(Dispatchers.IO) {
            synchronized(writes) {
                // A later change may have been written already: write only the latest.
                if (_library.value != updated) return@synchronized
                write(libraryFile, json.encodeToString(Library.serializer(), updated))
            }
        }
    }

    /** Every episode the store knows, library and previews, by id. */
    fun episode(id: String): Episode? {
        val showId = id.substringBefore(':')
        return (_episodes.value[showId] ?: _previews.value[showId]?.content?.episodes)?.firstOrNull { it.id == id }
    }

    fun episodesOf(showId: String): List<Episode> =
        _episodes.value[showId] ?: _previews.value[showId]
            ?.content
            ?.episodes
            .orEmpty()

    /** Saves a library show's episodes and notes. Blocking: call off the main thread. */
    fun saveFeed(
        showId: String,
        content: FeedContent,
    ) {
        _episodes.update { it + (showId to content.episodes) }
        feedsDir.mkdirs()
        notesDir.mkdirs()
        write(File(feedsDir, "$showId.json"), json.encodeToString(episodeList, content.episodes))
        write(File(notesDir, "$showId.json"), json.encodeToString(notesMap, content.notes))
    }

    /** A show's notes by episode id. Blocking. */
    fun notes(showId: String): Map<String, String> =
        _previews.value[showId]?.content?.notes ?: read(File(notesDir, "$showId.json"), notesMap) ?: emptyMap()

    fun forgetFeed(showId: String) {
        _episodes.update { it - showId }
        File(feedsDir, "$showId.json").delete()
        File(notesDir, "$showId.json").delete()
    }

    fun putPreview(preview: Preview) = _previews.update { it + (preview.show.id to preview) }

    fun dropPreview(showId: String) = _previews.update { it - showId }

    private fun <T> read(
        file: File,
        serializer: KSerializer<T>,
    ): T? =
        try {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else null
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun write(
        file: File,
        text: String,
    ) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(text)
            temp.renameTo(file)
        } catch (_: IOException) {
            // The next change writes everything again.
        }
    }
}
