package io.github.michaelmassoni.tessera.podcasts.engine

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** One chapter of an episode. */
data class Chapter(
    val startMs: Long,
    val title: String,
    val imageUrl: String? = null,
    val url: String? = null,
)

/**
 * Chapters come from the feed's Podcasting 2.0 chapters file (JSON) or from ID3 `CHAP` frames in
 * the audio itself (docs/podcasts.md, Chapters); either way they end up as a sorted list here.
 */
object Chapters {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * A Podcasting 2.0 chapters file: `{"chapters":[{"startTime":0,"title":…,"img":…,"url":…}]}`.
     * Chapters marked `"toc": false` are only for showing art along the way, not for the list,
     * and are left out. Anything unreadable gives no chapters.
     */
    fun parseJson(text: String): List<Chapter> {
        val root =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return emptyList()
        val list = root["chapters"] as? JsonArray ?: return emptyList()
        return tidy(
            list.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if ((o["toc"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
                val start = (o["startTime"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                Chapter(
                    startMs = (start * 1000).toLong(),
                    title = o.string("title").orEmpty(),
                    imageUrl = o.string("img"),
                    url = o.string("url"),
                )
            },
        )
    }

    /** Sorted by start, one per start time, none before 0; untitled ones get "Chapter n" from the screen. */
    fun tidy(chapters: List<Chapter>): List<Chapter> =
        chapters
            .filter { it.startMs >= 0 }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }

    /** The chapter playing at [positionMs]: the last to have started; -1 before the first or with none. */
    fun current(
        chapters: List<Chapter>,
        positionMs: Long,
    ): Int = chapters.indexOfLast { it.startMs <= positionMs }

    /** Where each chapter after the first begins, as a fraction of [durationMs], for marks on the seek bar. */
    fun marks(
        chapters: List<Chapter>,
        durationMs: Long,
    ): List<Float> =
        if (durationMs <= 0) {
            emptyList()
        } else {
            chapters.map { it.startMs.toFloat() / durationMs }.filter { it > 0f && it < 1f }
        }

    private fun JsonObject.string(key: String) =
        (this[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
