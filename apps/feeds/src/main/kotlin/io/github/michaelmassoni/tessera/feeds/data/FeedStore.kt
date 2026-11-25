package io.github.michaelmassoni.tessera.feeds.data

import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * The one copy of folders, feeds and article metadata in this process, shared by the app, the
 * widget and sync. Changes apply at once to [data] and are written to `feeds.json` in order,
 * atomically (as Tasks' store).
 */
class FeedStore(
    private val file: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writes = Any()
    private val state = MutableStateFlow(load())
    val data: StateFlow<FeedData> = state.asStateFlow()

    private fun load(): FeedData =
        try {
            if (file.exists()) json.decodeFromString(FeedData.serializer(), file.readText()) else FeedData()
        } catch (_: IOException) {
            FeedData()
        } catch (_: SerializationException) {
            FeedData()
        } catch (_: IllegalArgumentException) {
            FeedData()
        }

    /** Applies [change] and saves. Thread-safe; changes apply in the order made. */
    fun update(change: (FeedData) -> FeedData) {
        var updated: FeedData
        synchronized(writes) {
            updated = change(state.value)
            if (updated == state.value) return
            state.value = updated
        }
        scope.launch(Dispatchers.IO) { save(updated) }
    }

    private fun save(updated: FeedData) {
        synchronized(writes) {
            // A later change may have been written already: write only the latest.
            if (state.value != updated) return
            try {
                val temp = File(file.path + ".tmp")
                temp.writeText(json.encodeToString(FeedData.serializer(), updated))
                temp.renameTo(file)
            } catch (_: IOException) {
                // The next change writes everything again.
            }
        }
    }

    /** Re-reads the file (after a restore). */
    fun reload() {
        synchronized(writes) { state.value = load() }
    }
}

/** Each article's HTML, in `files/articles/<id>.html`, kept apart so the list stays small. */
class ContentStore(
    private val dir: File,
) {
    private fun file(id: String) = File(dir, "${id.filter { it.isLetterOrDigit() }}.html")

    fun read(id: String): String? =
        try {
            file(id).takeIf { it.exists() }?.readText()
        } catch (_: IOException) {
            null
        }

    fun write(
        id: String,
        html: String,
    ) {
        try {
            dir.mkdirs()
            file(id).writeText(html)
        } catch (_: IOException) {
            // Shown as the snippet instead.
        }
    }

    /** Removes the content of articles not in [keep]. */
    fun retain(keep: Set<String>) {
        val names = keep.map { file(it).name }.toSet()
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}

/** Feed icons, in `files/icons/<feed id>`. */
class IconStore(
    private val dir: File,
) {
    fun file(feedId: String) = File(dir, feedId.filter { it.isLetterOrDigit() })

    fun has(feedId: String) = file(feedId).exists()

    fun write(
        feedId: String,
        bytes: ByteArray,
    ) {
        try {
            dir.mkdirs()
            val f = file(feedId)
            if (f.exists() && f.readBytes().contentEquals(bytes)) return
            f.writeBytes(bytes)
        } catch (_: IOException) {
            // The feed shows its initial instead.
        }
    }

    fun retain(feedIds: Set<String>) {
        val names = feedIds.map { file(it).name }.toSet()
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }
}
