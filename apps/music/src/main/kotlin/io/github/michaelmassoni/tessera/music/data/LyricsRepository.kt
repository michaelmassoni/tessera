package io.github.michaelmassoni.tessera.music.data

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.music.engine.LrcLib
import io.github.michaelmassoni.tessera.music.engine.Lyrics
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.SubsonicClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What's needed to find a song's lyrics. */
data class LyricsQuery(
    val uri: String,
    val artist: String,
    val title: String,
    val album: String,
    val durationMs: Long,
)

/** Music's own settings (kept in `settings.xml`, which Android backs up). */
class MusicSettings(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Look up lyrics on LRCLIB when the song has none of its own. Off until the person turns it on. */
    var onlineLyrics: Boolean
        get() = prefs.getBoolean("online_lyrics", false)
        set(value) = prefs.edit { putBoolean("online_lyrics", value) }
}

/**
 * Lyrics for a song: the server's (for server songs), then LRCLIB if online lyrics are on. Kept in
 * `cache/lyrics/`, including "none found" for a week, so a song isn't looked up every time it plays.
 * Blocking: call off the main thread.
 */
class LyricsRepository(
    private val context: Context,
) {
    @Serializable
    private data class Cached(
        val lyrics: Lyrics? = null,
        val at: Long = 0,
        /** Whether LRCLIB was asked: a miss before online lyrics were on is worth asking again. */
        val askedOnline: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(q: LyricsQuery): File {
        val key = MessageDigest.getInstance("SHA-1").digest(q.uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "lyrics").apply { mkdirs() }, "$key.json")
    }

    fun lyrics(q: LyricsQuery): Lyrics? {
        val online = MusicSettings(context).onlineLyrics
        val f = file(q)
        val cached = runCatching { json.decodeFromString(Cached.serializer(), f.readText()) }.getOrNull()
        if (cached != null) {
            if (cached.lyrics != null) return cached.lyrics
            val fresh = System.currentTimeMillis() - cached.at < MISS_KEEP_MS
            if (fresh && (cached.askedOnline || !online)) return null
        }
        var reachedEverything = true
        var found: Lyrics? = null
        when (val server = fromServer(q)) {
            ServerAnswer.Unreachable -> reachedEverything = false
            is ServerAnswer.Found -> found = server.lyrics
        }
        if (found == null && online) {
            try {
                found = fromLrcLib(q)
            } catch (_: IOException) {
                reachedEverything = false
            }
        }
        // A miss because something couldn't be reached isn't remembered: next time may work.
        if (found != null || reachedEverything) {
            runCatching { f.writeText(json.encodeToString(Cached.serializer(), Cached(found, System.currentTimeMillis(), online))) }
        }
        return found
    }

    /** Forgets "none found" results, so turning online lyrics on looks again at once. */
    fun forgetMisses() {
        File(context.cacheDir, "lyrics").listFiles()?.forEach { f ->
            val c = runCatching { json.decodeFromString(Cached.serializer(), f.readText()) }.getOrNull()
            if (c?.lyrics == null) f.delete()
        }
    }

    private sealed interface ServerAnswer {
        data class Found(
            val lyrics: Lyrics?,
        ) : ServerAnswer

        data object Unreachable : ServerAnswer
    }

    private fun fromServer(q: LyricsQuery): ServerAnswer {
        val none = ServerAnswer.Found(null)
        val id = Subsonic.songId(q.uri) ?: return none
        val store = ServerStore(context)
        val account = store.account ?: return none
        val password = store.password() ?: return none
        return try {
            ServerAnswer.Found(SubsonicClient(account, password).lyrics(id, q.artist, q.title))
        } catch (e: ServerException) {
            if (e.problem == ServerProblem.Unreachable) ServerAnswer.Unreachable else none
        }
    }

    private fun fromLrcLib(q: LyricsQuery): Lyrics? {
        if (q.artist.isBlank() || q.title.isBlank()) return null
        val seconds = q.durationMs / 1000
        get(LrcLib.getUrl(q.artist, q.title, q.album, seconds))?.let { body -> LrcLib.get(body)?.let { return it } }
        return get(LrcLib.searchUrl(q.artist, q.title))?.let { LrcLib.bestOf(it, seconds) }
    }

    /** The body of a 200 reply, null for 404; throws IOException when LRCLIB can't be reached. */
    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        // LRCLIB asks clients to say who they are; this names the app, nothing about the person.
        c.setRequestProperty("User-Agent", "Tessera Music (https://github.com/michaelmassoni/tessera)")
        try {
            return when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> throw IOException("LRCLIB ${c.responseCode}")
            }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val MISS_KEEP_MS = 7L * 24 * 60 * 60 * 1000
    }
}
