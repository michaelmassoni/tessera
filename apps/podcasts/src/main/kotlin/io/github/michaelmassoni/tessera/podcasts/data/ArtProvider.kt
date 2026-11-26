package io.github.michaelmassoni.tessera.podcasts.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.podcasts.podcasts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Covers as `content://…podcasts.art/show/<show id>` or `…/episode/<episode id>`, for Android
 * Auto, which loads browse artwork itself and won't fetch a publisher's http(s) URL. Read-only and
 * images only: exported because Auto is another app, but it hands out only the covers of shows in
 * the library (looked up by id, so it can't be pointed at an arbitrary address). Downscaled and
 * cached in the cache folder.
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
        if (segments.size != 2) throw FileNotFoundException(uri.toString())
        val (kind, id) = segments
        val podcasts = context.podcasts
        // Called on a binder thread: waiting for the library to load is fine here.
        runBlocking { withTimeoutOrNull(LOAD_TIMEOUT_MS) { podcasts.store.loaded.first { it } } }
        val url =
            when (kind) {
                SHOW -> {
                    podcasts.show(id)?.artworkUrl
                }

                EPISODE -> {
                    val episode = podcasts.store.episode(id)
                    episode?.artworkUrl ?: episode?.showId?.let(podcasts::show)?.artworkUrl
                }

                else -> {
                    null
                }
            } ?: throw FileNotFoundException(uri.toString())
        val file = File(context.cacheDir, "art/${url.hashCode().toUInt()}.jpg")
        if (!file.exists()) {
            val bitmap = fetch(url) ?: throw FileNotFoundException(url)
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            temp.renameTo(file)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** The image at [url], scaled down to about [SIZE] px (podcast covers are often 3000 px). */
    private fun fetch(url: String): Bitmap? =
        try {
            val bytes =
                Net.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body.bytes()
                }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
            val decoded =
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return null
            if (decoded.width <= SIZE) {
                decoded
            } else {
                decoded.scale(SIZE, decoded.height * SIZE / decoded.width)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null // not a URL OkHttp accepts
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
        private const val QUALITY = 90
        private const val LOAD_TIMEOUT_MS = 5_000L
        private const val SHOW = "show"
        private const val EPISODE = "episode"
        const val AUTHORITY = "io.github.michaelmassoni.tessera.podcasts.art"

        fun show(id: String): Uri = "content://$AUTHORITY/$SHOW/${Uri.encode(id)}".toUri()

        fun episode(id: String): Uri = "content://$AUTHORITY/$EPISODE/${Uri.encode(id)}".toUri()
    }
}
