package io.github.michaelmassoni.tessera.recorder.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import io.github.michaelmassoni.tessera.recorder.engine.Names
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/** One saved recording. */
data class Recording(
    val id: Long,
    val uri: Uri,
    val title: String,
    val fileName: String,
    /** When it was made, ms since the epoch. */
    val dateMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
)

/**
 * Recordings in the shared Recordings/Tessera folder, through MediaStore: the app needs no
 * storage permission to create, rename and trash its own files there (docs/recorder.md).
 */
class Recordings(
    context: Context,
) {
    private val context = context.applicationContext
    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val waves = File(context.filesDir, "waves")

    /** Newest first. Pending (being recorded) and trashed ones are left out. */
    fun list(): List<Recording> = query(includePending = false).filter { !it.second }.map { it.first }

    /** Emits whenever MediaStore changes, starting with a first emission. */
    fun changes(): Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            resolver.registerContentObserver(collection, true, observer)
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }

    /** A new, pending recording to write into; it's hidden from other apps until [publish]. */
    fun create(title: String): Uri? {
        val name = Names.fileName(title, takenNames())
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        return resolver.insert(collection, values)
    }

    /** Makes a finished recording visible, and keeps its waveform. */
    fun publish(
        uri: Uri,
        waveform: Waveform,
    ) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        saveWaveform(ContentUris.parseId(uri), waveform)
    }

    fun delete(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
        waveFile(ContentUris.parseId(uri)).delete()
    }

    /** Renames the file to [title]; false if Android refused. */
    fun rename(
        recording: Recording,
        title: String,
    ): Boolean {
        val clean = Names.cleanTitle(title, recording.title)
        if (clean == recording.title) return true
        val name = Names.fileName(clean, takenNames() - recording.fileName)
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
        return runCatching { resolver.update(recording.uri, values, null, null) > 0 }.getOrDefault(false)
    }

    /** Moves to the system trash (kept 30 days); [untrash] brings it back. */
    fun trash(uri: Uri): Boolean = setTrashed(uri, true)

    fun untrash(uri: Uri): Boolean = setTrashed(uri, false)

    fun waveform(id: Long): Waveform? = runCatching { Waveform.decode(waveFile(id).readBytes()) }.getOrNull()

    /**
     * Recordings left pending because the app died while recording: those that play are
     * published (without a waveform), the rest deleted. Returns how many were lost.
     */
    fun recoverPending(except: Uri?): Int {
        var lost = 0
        query(includePending = true).filter { it.second && it.first.uri != except }.forEach { (r, _) ->
            if (playable(r.uri)) {
                resolver.update(r.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } else {
                delete(r.uri)
                lost++
            }
        }
        return lost
    }

    private fun playable(uri: Uri): Boolean =
        runCatching {
            MediaMetadataRetriever().use {
                it.setDataSource(context, uri)
                (it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0
            }
        }.getOrDefault(false)

    private fun setTrashed(
        uri: Uri,
        trashed: Boolean,
    ): Boolean =
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0) }, null, null) > 0
        }.getOrDefault(false)

    private fun takenNames(): Set<String> = query(includePending = true).map { it.first.fileName }.toSet()

    private fun saveWaveform(
        id: Long,
        waveform: Waveform,
    ) {
        waves.mkdirs()
        val tmp = File(waves, "$id.tmp")
        tmp.writeBytes(waveform.encode())
        tmp.renameTo(waveFile(id))
    }

    private fun waveFile(id: Long) = File(waves, "$id.bin")

    /** Our recordings, each with whether it's still pending. */
    private fun query(includePending: Boolean): List<Pair<Recording, Boolean>> {
        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.IS_PENDING,
            )
        val args =
            Bundle().apply {
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?")
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(FOLDER))
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
                if (includePending) putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
        val out = mutableListOf<Pair<Recording, Boolean>>()
        runCatching {
            resolver.query(collection, projection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1).orEmpty()
                    val recording =
                        Recording(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = name.substringBeforeLast('.'),
                            fileName = name,
                            dateMs = c.getLong(2) * 1000,
                            durationMs = c.getLong(3).takeIf { it > 0 } ?: (waveform(id)?.durationMs ?: 0),
                            sizeBytes = c.getLong(4),
                        )
                    out += recording to (c.getInt(5) == 1)
                }
            }
        }
        return out
    }

    companion object {
        const val FOLDER = "Recordings/Tessera/"
        const val MIME = "audio/mp4"
    }
}
