package io.github.michaelmassoni.tessera.contacts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import io.github.michaelmassoni.tessera.contacts.engine.PhotoCrop
import java.io.File
import java.io.IOException

/**
 * Turns a picked picture into a contact photo ([PhotoCrop]: the centred square, at most 720 px) and
 * saves it as a JPEG in the cache until the contact is saved. Blocking: call off the main thread.
 */
object PhotoPrep {
    /** @return the prepared photo as a file:// URI, or null if the picture can't be read. */
    fun prepare(
        context: Context,
        picked: Uri,
    ): String? =
        try {
            // ImageDecoder applies the picture's EXIF rotation; software, so the pixels can be cropped.
            val full =
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, picked)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            val crop = PhotoCrop.square(full.width, full.height)
            val square = Bitmap.createBitmap(full, crop.left, crop.top, crop.side, crop.side)
            val photo = if (crop.target < crop.side) square.scale(crop.target, crop.target) else square
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            // Earlier picks from this or other edits aren't needed any more.
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            Uri.fromFile(file).toString()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }

    private const val QUALITY = 90
}
