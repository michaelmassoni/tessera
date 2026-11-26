package io.github.michaelmassoni.tessera.podcasts

import androidx.annotation.OptIn
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import io.github.michaelmassoni.tessera.podcasts.data.Net
import io.github.michaelmassoni.tessera.podcasts.engine.Chapter
import io.github.michaelmassoni.tessera.podcasts.engine.Chapters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Request
import java.io.IOException

/**
 * The chapters of episodes played this session (docs/podcasts.md, Chapters): found by
 * [PlaybackService], shown by the player (same process). The feed's chapters file wins over
 * chapters in the audio, which only the phone's player sees as it reads the file.
 */
object EpisodeChapters {
    private val _byEpisode = MutableStateFlow<Map<String, List<Chapter>>>(emptyMap())
    val byEpisode: StateFlow<Map<String, List<Chapter>>> = _byEpisode.asStateFlow()

    private val fromFeed = mutableSetOf<String>()
    private val fetching = mutableSetOf<String>()

    /** Whether [id]'s chapters file still needs fetching; marks it as being fetched. */
    @Synchronized
    fun shouldFetch(id: String): Boolean = id !in fromFeed && fetching.add(id)

    @Synchronized
    fun fetched(
        id: String,
        chapters: List<Chapter>?,
    ) {
        fetching.remove(id)
        if (chapters.isNullOrEmpty()) return
        fromFeed += id
        _byEpisode.update { it + (id to chapters) }
    }

    /** Chapters read from the audio; kept unless the feed's file gave some. */
    @Synchronized
    fun fromAudio(
        id: String,
        chapters: List<Chapter>,
    ) {
        if (chapters.isEmpty() || id in fromFeed) return
        _byEpisode.update { it + (id to chapters) }
    }

    /** Fetches and reads a chapters file. Blocking: from Dispatchers.IO. Null on any failure. */
    fun fetch(url: String): List<Chapter>? =
        try {
            Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else Chapters.parseJson(r.body.string())
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null // not a URL
        }

    /** ID3 `CHAP` frames in the tracks the player read: each one's start and its `TIT2` title. */
    @OptIn(UnstableApi::class)
    fun id3(tracks: Tracks): List<Chapter> {
        val frames =
            tracks.groups.flatMap { group ->
                (0 until group.length).flatMap { i ->
                    val metadata = group.getTrackFormat(i).metadata ?: return@flatMap emptyList()
                    (0 until metadata.length()).mapNotNull { metadata.get(it) as? ChapterFrame }
                }
            }
        return Chapters.tidy(
            frames.map { frame ->
                val title =
                    (0 until frame.subFrameCount)
                        .map(frame::getSubFrame)
                        .filterIsInstance<TextInformationFrame>()
                        .firstOrNull { it.id == "TIT2" }
                        ?.values
                        ?.firstOrNull()
                        .orEmpty()
                Chapter(frame.startTimeMs.toLong(), title.trim())
            },
        )
    }
}
