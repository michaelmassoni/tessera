package io.github.michaelmassoni.tessera.music.data

import android.content.Context
import io.github.michaelmassoni.tessera.music.engine.LikeChange
import io.github.michaelmassoni.tessera.music.engine.Likes
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.SubsonicClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * This phone's likes ([LikeChange] by song id) in `likes.json`, shared by the app and the playback
 * service (same process). A server song's like is also sent to the server as a star; one that
 * couldn't be sent stays here and is sent again after the next library refresh ([sync]).
 */
object LikeStore {
    private val serializer = MapSerializer(Long.serializer(), LikeChange.serializer())
    private val lock = Any()
    private val changes = MutableStateFlow<Map<Long, LikeChange>?>(null)

    private fun file(context: Context) = File(context.filesDir, "likes.json")

    /** The changes, read once from disk. Blocking the first time: call off the main thread. */
    fun read(context: Context): Map<Long, LikeChange> =
        changes.value ?: synchronized(lock) {
            changes.value ?: runCatching { Json.decodeFromString(serializer, file(context).readText()) }
                .getOrDefault(emptyMap())
                .also { changes.value = it }
        }

    /** Every change from now on (null until [read]). */
    val flow: StateFlow<Map<Long, LikeChange>?> = changes.asStateFlow()

    private fun write(
        context: Context,
        updated: Map<Long, LikeChange>,
    ) {
        synchronized(lock) {
            changes.value = updated
            val f = file(context)
            val temp = File(f.path + ".tmp")
            runCatching {
                temp.writeText(Json.encodeToString(serializer, updated))
                temp.renameTo(f)
            }
        }
    }

    /** Likes or unlikes [song], and stars it on the server. Blocking: call off the main thread. */
    fun set(
        context: Context,
        song: Song,
        liked: Boolean,
    ) {
        write(context, read(context) + (song.id to LikeChange(liked, System.currentTimeMillis())))
        val serverId = Subsonic.songId(song.uri) ?: return
        client(context)?.let { runCatching { it.star(serverId, liked) } }
    }

    fun isLiked(
        context: Context,
        song: Song,
    ) = Likes.isLiked(song, read(context))

    /**
     * After the server library was read again: drops the changes it agrees with and sends the rest
     * again. Blocking: call off the main thread.
     */
    fun sync(
        context: Context,
        songs: List<Song>,
        client: SubsonicClient,
    ) {
        val current = read(context)
        val left = Likes.settled(songs, current)
        val byId = songs.associateBy { it.id }
        for ((id, change) in left) {
            val serverId = byId[id]?.uri?.let(Subsonic::songId) ?: continue
            try {
                client.star(serverId, change.liked)
            } catch (_: ServerException) {
                break // offline: try again next time
            }
        }
        if (left != current) {
            synchronized(lock) {
                // Keep anything changed meanwhile.
                val now = read(context)
                write(context, now.filterKeys { it in left || now[it] != current[it] })
            }
        }
    }

    private fun client(context: Context): SubsonicClient? {
        val store = ServerStore(context)
        val account = store.account ?: return null
        val password = store.password() ?: return null
        return SubsonicClient(account, password)
    }
}
