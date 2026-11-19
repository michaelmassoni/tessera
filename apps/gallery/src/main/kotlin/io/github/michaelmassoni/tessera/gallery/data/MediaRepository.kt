package io.github.michaelmassoni.tessera.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.exifinterface.media.ExifInterface
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.engine.MediaOrder
import java.io.IOException

/** Camera details read from a photo's EXIF, each null when absent. */
data class ExifDetails(
    val camera: String?,
    val aperture: String?,
    val exposure: String?,
    val iso: String?,
    val focalLength: String?,
)

/** Reads photos and videos from MediaStore. Blocking: call off the main thread. */
class MediaRepository(
    private val resolver: ContentResolver,
) {
    private val collection: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val projection =
        arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.MIME_TYPE,
            FileColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            FileColumns.DATE_ADDED,
            FileColumns.BUCKET_ID,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns.SIZE,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            FileColumns.DURATION,
            FileColumns.IS_FAVORITE,
            FileColumns.RELATIVE_PATH,
            FileColumns.DATE_EXPIRES,
        )

    /** Every visible (not trashed, not pending) photo and video. */
    fun loadAll(): List<MediaItem> = query(MediaStore.MATCH_EXCLUDE)

    /** Items in the system trash. */
    fun loadTrash(): List<MediaItem> = query(MediaStore.MATCH_ONLY)

    private fun query(trashed: Int): List<MediaItem> {
        val args =
            Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})",
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, trashed)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_TAKEN))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            }
        val items = mutableListOf<MediaItem>()
        resolver.query(collection, projection, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val video = c.getInt(1) == FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(4)
                items +=
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        isVideo = video,
                        mimeType = c.getString(2).orEmpty(),
                        name = c.getString(3).orEmpty(),
                        date = if (taken > 0) taken else c.getLong(5) * 1000,
                        bucketId = c.getLong(6),
                        bucketName = c.getString(7).orEmpty(),
                        size = c.getLong(8),
                        width = c.getInt(9),
                        height = c.getInt(10),
                        durationMillis = c.getLong(11),
                        favorite = c.getInt(12) != 0,
                        relativePath = c.getString(13).orEmpty(),
                        expires = c.getLong(14) * 1000,
                    )
            }
        }
        return items.sortedWith(MediaOrder)
    }

    fun exif(uri: Uri): ExifDetails? =
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                val camera =
                    when {
                        model == null -> make
                        make == null || model.startsWith(make, ignoreCase = true) -> model
                        else -> "$make $model"
                    }
                ExifDetails(
                    camera = camera,
                    aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "ƒ/%.1f".format(it) },
                    exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { exposure(it) },
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
                    focalLength =
                        exif
                            .getAttributeDouble(
                                ExifInterface.TAG_FOCAL_LENGTH,
                                0.0,
                            ).takeIf { it > 0 }
                            ?.let { "%.1f mm".format(it) },
                ).takeIf { listOf(it.camera, it.aperture, it.exposure, it.iso, it.focalLength).any { v -> v != null } }
            }
        } catch (_: IOException) {
            null
        }

    private fun exposure(seconds: Double): String = if (seconds >= 1) "%.1f s".format(seconds) else "1/%d s".format(Math.round(1 / seconds))
}
