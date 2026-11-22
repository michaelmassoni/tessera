package io.github.michaelmassoni.tessera.music.engine

import io.github.michaelmassoni.tessera.core.settings.ServerAddress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.security.MessageDigest

/** Where a server is, and who signs in. The password is kept separately, encrypted. */
data class ServerAccount(
    val address: String,
    val username: String,
)

/** Why a server request failed, in terms the person can act on. */
enum class ServerProblem {
    /** No answer: offline, wrong address, server down. */
    Unreachable,

    /** Error 40 (or 41 for token auth): wrong username or password. */
    WrongLogin,

    /** It answered, but not as a Subsonic server. */
    NotSubsonic,

    /** Error 30: the server is older than the client protocol. */
    TooOld,

    /** `http://` to an address on the internet. */
    Insecure,

    /** Not an address at all. */
    BadAddress,

    /** Any other Subsonic error. */
    Other,
}

class ServerException(
    val problem: ServerProblem,
    message: String? = null,
) : Exception(message ?: problem.name)

/**
 * The Subsonic REST protocol, without the network: addresses, authentication, and reading
 * responses into [Song]s. Pure, so it's unit tested.
 */
object Subsonic {
    const val API_VERSION = "1.16.1"
    const val CLIENT = "Tessera"
    const val SCHEME = "tessera-subsonic"
    const val PAGE = 500

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Tidies what the person typed (see [ServerAddress.normalize]), dropping a pasted `/rest/...`.
     * Fails for anything that isn't an http(s) address, and for `http://` outside a private network.
     */
    fun normalizeAddress(typed: String): String =
        try {
            ServerAddress.normalize(typed, dropAfter = "/rest")
        } catch (e: ServerAddress.Invalid) {
            throw ServerException(if (e.problem == ServerAddress.Problem.Insecure) ServerProblem.Insecure else ServerProblem.BadAddress)
        }

    fun isPrivateHost(host: String) = ServerAddress.isPrivateHost(host)

    /** Subsonic token auth: md5(password + salt), lower-case hex. */
    fun token(
        password: String,
        salt: String,
    ): String =
        MessageDigest
            .getInstance("MD5")
            .digest((password + salt).toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** `<address>/rest/<method>?u=…&t=…&s=…&v=…&c=…&f=json&<params>`. */
    fun url(
        account: ServerAccount,
        password: String,
        salt: String,
        method: String,
        params: List<Pair<String, String>> = emptyList(),
    ): String {
        val auth =
            listOf(
                "u" to account.username,
                "t" to token(password, salt),
                "s" to salt,
                "v" to API_VERSION,
                "c" to CLIENT,
                "f" to "json",
            )
        val query = (auth + params).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return "${account.address}/rest/$method?$query"
    }

    /**
     * The `subsonic-response` object of a reply, or the problem it reports. Anything that isn't
     * a Subsonic reply (a login page, a proxy error) is [ServerProblem.NotSubsonic].
     */
    fun response(body: String): JsonObject {
        val root =
            try {
                json.parseToJsonElement(body) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: throw ServerException(ServerProblem.NotSubsonic)
        val response = root["subsonic-response"] as? JsonObject ?: throw ServerException(ServerProblem.NotSubsonic)
        if (response.string("status") == "ok") return response
        val error = response["error"] as? JsonObject
        val code = error?.int("code")
        val problem =
            when (code) {
                40, 41, 44 -> ServerProblem.WrongLogin
                30 -> ServerProblem.TooOld
                else -> ServerProblem.Other
            }
        throw ServerException(problem, error?.string("message"))
    }

    /** The songs in a `search3` reply. */
    fun searchSongs(response: JsonObject): List<Song> = (response["searchResult3"] as? JsonObject).array("song").mapNotNull(::song)

    /** Album ids in a `getAlbumList2` reply. */
    fun albumIds(response: JsonObject): List<String> =
        (response["albumList2"] as? JsonObject).array("album").mapNotNull { (it as? JsonObject)?.string("id") }

    /** The songs in a `getAlbum` reply. */
    fun albumSongs(response: JsonObject): List<Song> = (response["album"] as? JsonObject).array("song").mapNotNull(::song)

    /** A playlist in a `getPlaylists` reply, without its songs: (server id, name, read-only). */
    data class PlaylistInfo(
        val id: String,
        val name: String,
        val readOnly: Boolean,
    )

    /**
     * The playlists in a `getPlaylists` reply. Smart playlists (Navidrome marks them `readonly`) and
     * other people's shared playlists can be played but not changed.
     */
    fun playlists(
        response: JsonObject,
        username: String,
    ): List<PlaylistInfo> =
        (response["playlists"] as? JsonObject).array("playlist").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.string("id") ?: return@mapNotNull null
            val owner = o.string("owner")
            PlaylistInfo(
                id = id,
                name = o.string("name").orEmpty(),
                readOnly = o.string("readonly") == "true" || (owner != null && !owner.equals(username, ignoreCase = true)),
            )
        }

    /** The songs of a `getPlaylist` reply, in order, as server song ids. */
    fun playlistSongIds(response: JsonObject): List<String> =
        (response["playlist"] as? JsonObject).array("entry").mapNotNull { (it as? JsonObject)?.string("id") }

    /**
     * Lyrics from an OpenSubsonic `getLyricsBySongId` reply (Navidrome reads them from the file's
     * tags or a .lrc next to it): synced ones if there are any. A positive offset shows lines sooner.
     */
    fun structuredLyrics(response: JsonObject): Lyrics? {
        val all = (response["lyricsList"] as? JsonObject).array("structuredLyrics").mapNotNull { it as? JsonObject }
        val chosen = all.firstOrNull { it.string("synced") == "true" } ?: all.firstOrNull() ?: return null
        val synced = chosen.string("synced") == "true"
        val offset = chosen.long("offset") ?: 0
        val lines =
            chosen.array("line").mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                LyricLine(if (synced) ((o.long("start") ?: 0) - offset).coerceAtLeast(0) else null, o.string("value").orEmpty())
            }
        return lines.takeIf { l -> l.any { it.text.isNotBlank() } }?.let { Lyrics(it, LyricsSource.Server) }
    }

    /** Lyrics from the older `getLyrics` (by artist and title): plain text, or LRC. */
    fun plainLyrics(response: JsonObject): Lyrics? =
        (response["lyrics"] as? JsonObject)?.string("value")?.let { LyricsParser.parse(it, LyricsSource.Server) }

    /** The id of the playlist a `createPlaylist` reply describes. */
    fun createdPlaylistId(response: JsonObject): String? = (response["playlist"] as? JsonObject)?.string("id")

    /** One Subsonic `child` (a song) as a [Song], or null for a directory or something unplayable. */
    fun song(element: JsonElement): Song? {
        val o = element as? JsonObject ?: return null
        if (o.string("isDir") == "true") return null
        val id = o.string("id") ?: return null
        val albumKey = o.string("albumId") ?: o.string("album") ?: o.string("parent") ?: ""
        val artistKey = o.string("artistId") ?: o.string("artist") ?: ""
        val disc = o.int("discNumber") ?: 0
        val track = o.int("track") ?: 0
        return Song(
            id = idOf("song", id),
            uri = songUri(id),
            title = o.string("title") ?: o.string("path")?.substringAfterLast('/') ?: "",
            artist = o.string("artist").orEmpty(),
            artistId = idOf("artist", artistKey),
            album = o.string("album").orEmpty(),
            albumId = idOf("album", albumKey),
            durationMillis = (o.long("duration") ?: 0) * 1000,
            track = if (track > 0) disc.coerceAtLeast(0) * 1000 + track else 0,
            year = o.int("year") ?: 0,
            art = o.string("coverArt"),
            albumArtist =
                o.string("displayAlbumArtist")
                    ?: ((o["albumArtists"] as? JsonArray)?.firstOrNull() as? JsonObject)?.string("name")
                    ?: o.string("albumArtist")
                    ?: "",
            addedAt = time(o.string("created")),
            playedAt = time(o.string("played")),
        )
    }

    /** An ISO 8601 time from the server, as ms since 1970; 0 when missing or unreadable. */
    fun time(text: String?): Long =
        try {
            if (text.isNullOrBlank()) {
                0
            } else {
                java.time.OffsetDateTime
                    .parse(text)
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (_: java.time.format.DateTimeParseException) {
            0
        }

    fun songUri(id: String) = "$SCHEME:song/$id"

    /** The server's id of a song from its [songUri], or null for a local song. */
    fun songId(uri: String): String? = uri.removePrefix("$SCHEME:song/").takeIf { it != uri && it.isNotEmpty() }

    fun isServerUri(uri: String) = uri.startsWith("$SCHEME:")

    /**
     * A stable positive number for a server id (FNV-1a, 64 bits), separate per [kind]. Numbers
     * are what playlists, the queue and the widget keep; collisions in a library of any real
     * size are vanishingly unlikely.
     */
    fun idOf(
        kind: String,
        id: String,
    ): Long {
        var hash = -0x340d631b7bdddcdbL
        for (b in "$kind/$id".toByteArray()) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L
        }
        return (hash and Long.MAX_VALUE).coerceAtLeast(1)
    }

    private fun JsonObject?.array(key: String): List<JsonElement> =
        when (val v = this?.get(key)) {
            is JsonArray -> v
            is JsonObject -> listOf(v)
            else -> emptyList()
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
}
