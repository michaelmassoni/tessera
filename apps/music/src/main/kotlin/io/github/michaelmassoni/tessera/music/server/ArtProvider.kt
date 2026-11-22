package io.github.michaelmassoni.tessera.music.server

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.music.data.MusicRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Cover art as `content://…music.art/<art id>` (server songs) or `…/local/<album id>` (the phone's), for the media notification, lock
 * screen, Bluetooth and other media controllers, which load the artwork URI themselves. Read-only
 * and images only: exported because those controllers are other apps, but all it can hand out is
 * album covers from the signed-in server (never an address or a token).
 */
class ArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/jpeg"

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read-only")
        val context = context ?: throw FileNotFoundException()
        val segments = uri.pathSegments
        val local = segments.size == 2 && segments[0] == LOCAL
        val art = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: throw FileNotFoundException()
        val name = if (local) "local-$art" else "provided-${art.hashCode().toUInt()}"
        val file = File(context.cacheDir, "art/$name.jpg")
        if (!file.exists()) {
            val bitmap =
                if (local) {
                    // Music on the phone: the system's album thumbnail, which Android Auto can't read itself.
                    val albumId = art.toLongOrNull() ?: throw FileNotFoundException(art)
                    try {
                        context.contentResolver.loadThumbnail(MusicRepository.albumUri(albumId), Size(SIZE, SIZE), null)
                    } catch (_: IOException) {
                        throw FileNotFoundException(art)
                    }
                } else {
                    ServerLibrary.art(context, art, SIZE) ?: throw FileNotFoundException(art)
                }
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            temp.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        private const val SIZE = 512
        const val AUTHORITY = "io.github.michaelmassoni.tessera.music.art"

        fun uri(art: String): Uri = "content://$AUTHORITY/${Uri.encode(art)}".toUri()

        private const val LOCAL = "local"

        /** A phone album's cover. */
        fun localUri(albumId: Long): Uri = "content://$AUTHORITY/$LOCAL/$albumId".toUri()
    }
}
