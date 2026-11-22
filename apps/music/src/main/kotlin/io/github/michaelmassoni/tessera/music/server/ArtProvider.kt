package io.github.michaelmassoni.tessera.music.server

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException

/**
 * Cover art of server songs as `content://…music.art/<art id>`, for the media notification, lock
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
        val art = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: throw FileNotFoundException()
        val file = File(context.cacheDir, "art/provided-${art.hashCode().toUInt()}.jpg")
        if (!file.exists()) {
            val bitmap = ServerLibrary.art(context, art, SIZE) ?: throw FileNotFoundException(art)
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
    }
}
