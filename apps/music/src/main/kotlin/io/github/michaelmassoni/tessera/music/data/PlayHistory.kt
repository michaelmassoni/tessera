package io.github.michaelmassoni.tessera.music.data

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * When each song was last played on this phone (song id to ms), for "Recently played". Written by
 * the playback service as songs start; kept in `files/plays.json`, so it's in backups.
 */
object PlayHistory {
    private val serializer = MapSerializer(Long.serializer(), Long.serializer())
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, "plays.json")

    fun read(context: Context): Map<Long, Long> =
        synchronized(lock) {
            runCatching { Json.decodeFromString(serializer, file(context).readText()) }.getOrDefault(emptyMap())
        }

    /** Records [songId] as played now. Blocking: call off the main thread. */
    fun played(
        context: Context,
        songId: Long,
    ) {
        if (songId <= 0) return
        synchronized(lock) {
            val updated = read(context) + (songId to System.currentTimeMillis())
            val f = file(context)
            val temp = File(f.path + ".tmp")
            runCatching {
                temp.writeText(Json.encodeToString(serializer, updated))
                temp.renameTo(f)
            }
        }
    }
}
