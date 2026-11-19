package io.github.michaelmassoni.tessera.gallery.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.gallery.Collection
import io.github.michaelmassoni.tessera.gallery.GalleryState
import io.github.michaelmassoni.tessera.gallery.data.ExifDetails
import io.github.michaelmassoni.tessera.gallery.engine.Albums
import io.github.michaelmassoni.tessera.gallery.engine.DaySection
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem

// Fixed "now" (2026-11-19 12:00 UTC) so the day headings in screenshots never change.
private const val NOW = 1_795_089_600_000L
private const val HOUR = 3_600_000L

private val sample =
    (0 until 24).map { i ->
        MediaItem(
            id = i.toLong(),
            uri = "content://preview/$i",
            isVideo = i % 7 == 3,
            mimeType = "image/jpeg",
            name = "IMG_$i.jpg",
            date = NOW - i * 9 * HOUR,
            bucketId = (i % 3).toLong(),
            bucketName = listOf("Camera", "Screenshots", "Download")[i % 3],
            durationMillis = 42_000,
            favorite = i % 5 == 0,
            relativePath = listOf("DCIM/Camera/", "Pictures/Screenshots/", "Download/")[i % 3],
        )
    }

private val sampleState = GalleryState(loaded = true, items = sample, albums = Albums.fromItems(sample))

private object NoGrid : GridActions {
    override fun toggle(item: MediaItem) = Unit

    override fun toggleDay(section: DaySection) = Unit

    override fun clearSelection() = Unit

    override fun shareSelection() = Unit

    override fun favoriteSelection() = Unit

    override fun trashSelection() = Unit

    override fun restoreSelection() = Unit

    override fun deleteSelectionForever() = Unit

    override fun openSettings() = Unit

    override fun pick(items: List<MediaItem>) = Unit
}

private object NoViewer : ViewerActions {
    override fun close() = Unit

    override fun share(item: MediaItem) = Unit

    override fun toggleFavorite(item: MediaItem) = Unit

    override fun edit(item: MediaItem) = Unit

    override fun canEdit(item: MediaItem) = true

    override fun setWallpaper(
        item: MediaItem,
        target: WallpaperTarget,
    ) = Unit

    override fun trash(item: MediaItem) = Unit

    override fun exif(item: MediaItem): ExifDetails? = null
}

private fun itemsOf(c: Collection) = if (c == Collection.Videos) sample.filter { it.isVideo } else sample

@TesseraPreviews
@Composable
private fun PhotosPreview() {
    TesseraTheme(dynamicColor = false) {
        GalleryApp(sampleState, ::itemsOf, NoGrid, NoViewer, dynamicColor = false, now = { NOW })
    }
}

@TesseraPreviews
@Composable
private fun SelectionPreview() {
    TesseraTheme(dynamicColor = false) {
        GalleryApp(sampleState.copy(selection = setOf(1, 2, 5)), ::itemsOf, NoGrid, NoViewer, dynamicColor = false, now = { NOW })
    }
}

@TesseraPreviews
@Composable
private fun GalleryPermissionPreview() {
    TesseraTheme(dynamicColor = false) {
        PermissionScreen(denied = false, onGrant = {}, onSettings = {})
    }
}
