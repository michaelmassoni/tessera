package io.github.michaelmassoni.tessera.files.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.michaelmassoni.tessera.files.engine.FileEntry
import io.github.michaelmassoni.tessera.files.engine.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image and video thumbnails, loaded off the main thread and kept in a small memory cache. */
object Thumbnails {
    private val cache =
        object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ) = value.allocationByteCount
        }

    private fun key(entry: FileEntry) = "${entry.file.path}:${entry.lastModified}"

    fun load(entry: FileEntry): Bitmap? {
        cache.get(key(entry))?.let { return it }
        val size = Size(256, 256)
        val bitmap =
            runCatching {
                when (entry.type) {
                    FileType.Image -> ThumbnailUtils.createImageThumbnail(entry.file, size, null)
                    FileType.Video -> ThumbnailUtils.createVideoThumbnail(entry.file, size, null)
                    else -> null
                }
            }.getOrNull() ?: return null
        cache.put(key(entry), bitmap)
        return bitmap
    }
}

@Composable
fun rememberThumbnail(entry: FileEntry): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, entry.file.path, entry.lastModified) {
        if (entry.type == FileType.Image || entry.type == FileType.Video) {
            value = withContext(Dispatchers.IO) { Thumbnails.load(entry) }
        }
    }
