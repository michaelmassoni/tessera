package io.github.michaelmassoni.tessera.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import io.github.michaelmassoni.tessera.gallery.data.ThumbnailFetcher
import io.github.michaelmassoni.tessera.gallery.data.ThumbnailKeyer

/**
 * Coil with MediaStore thumbnails for the grid ([Thumbnail]) and video frames for the viewer. No
 * network fetcher is added: Gallery never goes online.
 */
class GalleryApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(ThumbnailFetcher.Factory())
                add(ThumbnailKeyer())
                add(VideoFrameDecoder.Factory())
            }.build()
}
