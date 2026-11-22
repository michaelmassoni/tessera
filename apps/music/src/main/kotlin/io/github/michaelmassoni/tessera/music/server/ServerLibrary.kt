package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import io.github.michaelmassoni.tessera.music.engine.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The server's songs as last fetched, saved on the phone so the library shows at once and works
 * offline, plus album art fetched from the server.
 */
object ServerLibrary {
    @Serializable
    private data class Saved(
        val address: String,
        val username: String,
        val songs: List<Song>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Album id to cover art id, for art wherever only an album id is known (widget, queue). */
    private val artByAlbum = ConcurrentHashMap<Long, String>()

    @Volatile private var indexed = false

    private fun file(context: Context) = File(context.filesDir, "server-library.json")

    /** The saved songs for [account], or null when there are none (or they're another account's). */
    fun load(
        context: Context,
        account: ServerAccount,
    ): List<Song>? =
        try {
            val f = file(context)
            if (!f.exists()) {
                null
            } else {
                json
                    .decodeFromString(Saved.serializer(), f.readText())
                    .takeIf { it.address == account.address && it.username == account.username }
                    ?.songs
                    ?.also(::index)
            }
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun save(
        context: Context,
        account: ServerAccount,
        songs: List<Song>,
    ) {
        index(songs)
        val f = file(context)
        val temp = File(f.path + ".tmp")
        try {
            temp.writeText(json.encodeToString(Saved.serializer(), Saved(account.address, account.username, songs)))
            temp.renameTo(f)
        } catch (_: IOException) {
            // The next fetch tries again.
        }
    }

    fun clear(context: Context) {
        file(context).delete()
        artByAlbum.clear()
        indexed = false
    }

    private fun index(songs: List<Song>) {
        artByAlbum.clear()
        songs.forEach { s -> s.art?.let { artByAlbum.putIfAbsent(s.albumId, it) } }
        indexed = true
    }

    /** The cover art id of a server album, reading the saved library if this process hasn't yet. */
    fun artFor(
        context: Context,
        albumId: Long,
    ): String? {
        if (!indexed) {
            val store = ServerStore(context)
            if (store.source == MusicSource.Server) store.account?.let { load(context, it) }
            indexed = true
        }
        return artByAlbum[albumId]
    }

    /**
     * Cover art at [px] pixels: from `cache/art/` when fetched before, else from the server
     * (which scales it). Blocking; null when there is none or the server can't be reached.
     */
    fun art(
        context: Context,
        art: String,
        px: Int,
    ): Bitmap? {
        // Sizes are rounded up to a few steps, so lists and the player share files.
        val size = SIZES.firstOrNull { it >= px } ?: SIZES.last()
        val name = MessageDigest.getInstance("SHA-1").digest("$art@$size".toByteArray()).joinToString("") { "%02x".format(it) }
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val cached = File(dir, name)
        if (cached.exists()) BitmapFactory.decodeFile(cached.path)?.let { return it }
        val store = ServerStore(context)
        val account = store.account ?: return null
        val password = store.password() ?: return null
        return try {
            val connection = URL(SubsonicClient(account, password).coverArtUrl(art, size)).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                if (connection.responseCode !in 200..299 || connection.contentType?.startsWith("image/") != true) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val temp = File(dir, "$name.tmp")
                temp.writeBytes(bytes)
                temp.renameTo(cached)
                bitmap
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }
    }

    private val SIZES = listOf(128, 256, 512, 1024)
}
