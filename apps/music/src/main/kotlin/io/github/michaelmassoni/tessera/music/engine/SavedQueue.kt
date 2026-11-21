package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One queued track, with enough to show and play it without the library. */
@Serializable
data class SavedItem(
    val id: String,
    val uri: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0,
)

/** The queue and where playback was, kept across the app being closed. */
@Serializable
data class SavedQueue(
    val items: List<SavedItem>,
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
) {
    /** Index and position made safe for [items] (an empty queue is never saved). */
    fun clamped(): SavedQueue = copy(index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), positionMs = positionMs.coerceAtLeast(0))

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): SavedQueue? =
            try {
                json.decodeFromString(serializer(), text).takeIf { it.items.isNotEmpty() }?.clamped()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
