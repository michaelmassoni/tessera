package io.github.michaelmassoni.tessera.gallery.data

import android.util.Size
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * A grid thumbnail of a MediaStore photo or video. Loading one asks MediaStore for its cached
 * thumbnail instead of decoding the original (or pulling a frame out of a video), which keeps
 * scrolling a large library smooth. The viewer still loads the full item by its URI.
 */
data class Thumbnail(
    val uri: String,
)

class ThumbnailFetcher(
    private val data: Thumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // The cell size Coil resolved from the layout, within MediaStore's own thumbnail range.
        val side =
            maxOf(options.size.width.pxOrElse { DEFAULT }, options.size.height.pxOrElse { DEFAULT })
                .coerceIn(MIN, MAX)
        val bitmap = options.context.contentResolver.loadThumbnail(data.uri.toUri(), Size(side, side), null)
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Thumbnail> {
        override fun create(
            data: Thumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ThumbnailFetcher(data, options)
    }

    private companion object {
        const val DEFAULT = 384
        const val MIN = 96
        const val MAX = 640
    }
}

/** Thumbnails are cached apart from full images of the same item. */
class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(
        data: Thumbnail,
        options: Options,
    ): String = "thumbnail:${data.uri}"
}
