package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder

/**
 * LRCLIB (lrclib.net), an open lyrics database with no key or account. Only asked when the
 * person turns online lyrics on, and only with the song's artist, title, album and length.
 * Building requests and reading replies; pure, so it's unit tested.
 */
object LrcLib {
    const val BASE = "https://lrclib.net/api"

    private val json = Json { ignoreUnknownKeys = true }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** An exact match by artist, title, album and length (LRCLIB allows a couple of seconds either way). */
    fun getUrl(
        artist: String,
        title: String,
        album: String,
        durationSec: Long,
    ) = "$BASE/get?artist_name=${enc(artist)}&track_name=${enc(title)}&album_name=${enc(album)}&duration=$durationSec"

    fun searchUrl(
        artist: String,
        title: String,
    ) = "$BASE/search?artist_name=${enc(artist)}&track_name=${enc(title)}"

    /** Lyrics in one LRCLIB record: synced if it has them. Null for instrumentals and empty records. */
    fun record(o: JsonObject): Lyrics? {
        if ((o["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val synced = (o["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
        val plain = (o["plainLyrics"] as? JsonPrimitive)?.contentOrNull
        return synced?.let { LyricsParser.parse(it, LyricsSource.LrcLib) } ?: plain?.let { LyricsParser.parse(it, LyricsSource.LrcLib) }
    }

    fun get(body: String): Lyrics? = (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject)?.let(::record)

    /**
     * From `search` results, the record closest in length to the song (within 5 s), synced ones
     * first. Null when none is close enough: a different recording's lyrics would be out of step.
     */
    fun bestOf(
        body: String,
        durationSec: Long,
    ): Lyrics? {
        val results =
            (runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        return results
            .filter { o ->
                val d = (o["duration"] as? JsonPrimitive)?.doubleOrNull ?: return@filter false
                durationSec <= 0 || kotlin.math.abs(d - durationSec) <= 5
            }.sortedWith(
                compareBy<JsonObject> { (it["syncedLyrics"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() }
                    .thenBy { kotlin.math.abs(((it["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) - durationSec) },
            ).firstNotNullOfOrNull(::record)
    }
}
