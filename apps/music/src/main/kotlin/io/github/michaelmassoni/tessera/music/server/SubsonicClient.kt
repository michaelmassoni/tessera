package io.github.michaelmassoni.tessera.music.server

import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Talks to one Subsonic server. Blocking: call off the main thread. */
class SubsonicClient(
    val account: ServerAccount,
    private val password: String,
) {
    /** A new salt (and so a new token) for every request, as the protocol recommends. */
    private fun salt(): String {
        val bytes = ByteArray(8).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun url(
        method: String,
        params: List<Pair<String, String>> = emptyList(),
    ) = Subsonic.url(account, password, salt(), method, params)

    /** Calls [method] and returns its `subsonic-response`, or throws [ServerException]. */
    fun call(
        method: String,
        params: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        val body =
            try {
                val connection = URL(url(method, params)).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    // A proxy's login page or a 404 for /rest: whatever answered isn't Subsonic.
                    if (code !in 200..299 && !text.contains("subsonic-response")) {
                        throw ServerException(if (code == 401 || code == 403) ServerProblem.WrongLogin else ServerProblem.NotSubsonic)
                    }
                    text
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                throw ServerException(ServerProblem.Unreachable, e.message)
            }
        return Subsonic.response(body)
    }

    /** Checks the address and login. */
    fun ping() {
        call("ping")
    }

    /**
     * Every song on the server: `search3` with an empty query, a page at a time. Servers that
     * don't list everything for an empty query are read album by album instead.
     */
    fun allSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        var offset = 0
        while (true) {
            val page =
                Subsonic.searchSongs(
                    call(
                        "search3",
                        listOf(
                            "query" to "",
                            "songCount" to Subsonic.PAGE.toString(),
                            "songOffset" to offset.toString(),
                            "artistCount" to "0",
                            "albumCount" to "0",
                        ),
                    ),
                )
            songs += page
            if (page.size < Subsonic.PAGE) break
            offset += page.size
        }
        if (songs.isNotEmpty()) return songs.distinctBy { it.id }
        return byAlbum()
    }

    private fun byAlbum(): List<Song> {
        val ids = mutableListOf<String>()
        var offset = 0
        while (true) {
            val page =
                Subsonic.albumIds(
                    call(
                        "getAlbumList2",
                        listOf("type" to "alphabeticalByName", "size" to Subsonic.PAGE.toString(), "offset" to offset.toString()),
                    ),
                )
            ids += page
            if (page.size < Subsonic.PAGE) break
            offset += page.size
        }
        return ids.flatMap { Subsonic.albumSongs(call("getAlbum", listOf("id" to it))) }.distinctBy { it.id }
    }

    /** Where to stream a song from, transcoded to at most [maxKbps] (0: the original file). */
    fun streamUrl(
        id: String,
        maxKbps: Int,
    ) = url("stream", listOf("id" to id) + if (maxKbps > 0) listOf("maxBitRate" to maxKbps.toString()) else emptyList())

    fun coverArtUrl(
        art: String,
        size: Int,
    ) = url("getCoverArt", listOf("id" to art, "size" to size.toString()))

    /** "Now playing" ([submission] false), or "played" (true). Failures don't matter. */
    fun scrobble(
        id: String,
        submission: Boolean,
    ) {
        runCatching { call("scrobble", listOf("id" to id, "submission" to submission.toString())) }
    }

    companion object {
        private const val TIMEOUT_MS = 20_000
        private val random = SecureRandom()
    }
}
