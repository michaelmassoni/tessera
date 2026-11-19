package io.github.michaelmassoni.tessera.gallery.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import io.github.michaelmassoni.tessera.gallery.engine.EditColor
import io.github.michaelmassoni.tessera.gallery.engine.EditGeometry
import io.github.michaelmassoni.tessera.gallery.engine.EditState
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Reading photos for the editor and writing the result. Blocking: call off the main thread.
 * Both the preview and the saved photo go through [render], so what you see is what's saved.
 */
class EditorIO(
    private val resolver: ContentResolver,
) {
    /** The photo, upright (EXIF orientation applied), at most [maxSide] px on its long side. */
    fun preview(
        uri: Uri,
        maxSide: Int = PREVIEW_SIDE,
    ): Bitmap = decode(uri) { w, h -> max(w, h).toFloat() / maxSide }

    /** A 256-bin luminance histogram, from a sample of [bitmap]'s pixels. */
    fun histogram(bitmap: Bitmap): IntArray {
        val bins = IntArray(256)
        val step = max(1, (bitmap.width * bitmap.height / HISTOGRAM_SAMPLES).let { sqrt(it.toDouble()).toInt() })
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height step step) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in 0 until bitmap.width step step) {
                val c = row[x]
                val l = (0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)).toInt()
                bins[l.coerceIn(0, 255)]++
            }
        }
        return bins
    }

    /** [source] with [state] applied: turned, flipped, straightened, cropped and adjusted. */
    fun render(
        source: Bitmap,
        state: EditState,
    ): Bitmap {
        val (affine, size) = EditGeometry.toOutput(source.width, source.height, state)
        val out = createBitmap(size.first, size.second)
        val paint =
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                if (!state.adjust.isIdentity) colorFilter = ColorMatrixColorFilter(ColorMatrix(EditColor.matrix(state.adjust)))
            }
        Canvas(out).drawBitmap(source, Matrix().apply { setValues(affine.values()) }, paint)
        return out
    }

    /**
     * Saves the edit as a new JPEG next to [item]: same folder, same date taken, so it sits beside
     * the original in the timeline. Returns the new photo's URI.
     */
    fun saveCopy(
        item: MediaItem,
        state: EditState,
    ): Uri {
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, copyName(item.name))
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, item.relativePath.ifEmpty { "Pictures/" })
                put(MediaStore.MediaColumns.DATE_TAKEN, item.date)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw IOException("MediaStore refused the new photo")
        try {
            write(item.uri.toUri(), uri, state)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Replaces [item] with the edit. Call only after the user allowed it (`createWriteRequest`). */
    fun overwrite(
        item: MediaItem,
        state: EditState,
    ) {
        val uri = item.uri.toUri()
        write(uri, uri, state, mode = "rwt")
    }

    private fun write(
        source: Uri,
        target: Uri,
        state: EditState,
        mode: String = "rw",
    ) {
        val exif = readExif(source)
        // Very large photos are halved so the full-size render fits in memory.
        val full = decode(source) { w, h -> if (w.toLong() * h > MAX_PIXELS) 2f else 1f }
        val edited = render(full, state)
        full.recycle()
        resolver.openFileDescriptor(target, mode)?.use { fd ->
            java.io.FileOutputStream(fd.fileDescriptor).use { out -> compress(edited, out) }
        } ?: throw IOException("Can't write $target")
        edited.recycle()
        resolver.openFileDescriptor(target, "rw")?.use { fd ->
            val out = ExifInterface(fd.fileDescriptor)
            exif.forEach { (tag, value) -> out.setAttribute(tag, value) }
            out.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            out.saveAttributes()
        }
    }

    private fun compress(
        bitmap: Bitmap,
        out: OutputStream,
    ) {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Couldn't encode the photo")
    }

    /** The details worth keeping on an edited copy: when, with what, and where. */
    private fun readExif(uri: Uri): Map<String, String> =
        try {
            resolver
                .openInputStream(uri)
                ?.use { stream ->
                    val exif = ExifInterface(stream)
                    KEPT_TAGS.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()
                }.orEmpty()
        } catch (_: IOException) {
            emptyMap()
        }

    /** Decodes [uri] upright, in software (so it can be drawn and read), downscaled by [factor]. */
    private fun decode(
        uri: Uri,
        factor: (Int, Int) -> Float,
    ): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val f = factor(info.size.width, info.size.height)
            if (f >
                1f
            ) {
                decoder.setTargetSize((info.size.width / f).toInt().coerceAtLeast(1), (info.size.height / f).toInt().coerceAtLeast(1))
            }
        }

    companion object {
        const val PREVIEW_SIDE = 2048
        private const val HISTOGRAM_SAMPLES = 40_000
        private const val JPEG_QUALITY = 95
        private const val MAX_PIXELS = 48_000_000L

        private val KEPT_TAGS =
            listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
            )

        /** "IMG_1234.heic" → "IMG_1234-edited.jpg". MediaStore adds " (1)" if it's taken. */
        fun copyName(original: String): String = "${original.substringBeforeLast('.').ifEmpty { "Photo" }}-edited.jpg"
    }
}
