package io.github.michaelmassoni.tessera.music.cast

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A song as the cast device is told about it. */
data class CastMedia(
    val url: String,
    val contentType: String,
    val title: String,
    val artist: String,
    val album: String,
    val imageUrl: String?,
)

/** What the receiver says about itself: the app running on it, and its volume. */
data class ReceiverStatus(
    val apps: List<App>,
    val volume: Float?,
    val muted: Boolean?,
) {
    data class App(
        val appId: String,
        val sessionId: String,
        val transportId: String,
    )
}

/** What the media player on the receiver is doing. */
data class MediaStatus(
    val mediaSessionId: Long,
    val playerState: PlayerState,
    val positionMs: Long,
    val durationMs: Long?,
    val idleReason: IdleReason?,
) {
    enum class PlayerState { IDLE, BUFFERING, PLAYING, PAUSED }

    enum class IdleReason { FINISHED, CANCELLED, INTERRUPTED, ERROR }
}

/** A message from the receiver, as far as Music cares. */
sealed interface Incoming {
    data object Ping : Incoming

    /** The other end closed this virtual connection. */
    data object Close : Incoming

    data class Receiver(
        val status: ReceiverStatus,
        /** The request this answers; 0 when the receiver volunteered it. */
        val requestId: Long,
    ) : Incoming

    /** Every media session on the receiver (usually one, or none once it stops). */
    data class Media(
        val statuses: List<MediaStatus>,
    ) : Incoming

    /** A request failed (LOAD_FAILED, INVALID_REQUEST, LAUNCH_ERROR ...). */
    data class Failed(
        val type: String,
        val requestId: Long?,
    ) : Incoming

    data object Other : Incoming
}

/**
 * The Cast v2 JSON messages Music sends and reads: the platform namespaces (connection,
 * heartbeat, receiver) and the media namespace of the Default Media Receiver.
 */
object CastProtocol {
    const val CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
    const val HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
    const val RECEIVER = "urn:x-cast:com.google.cast.receiver"
    const val MEDIA = "urn:x-cast:com.google.cast.media"

    const val SENDER = "sender-0"
    const val PLATFORM = "receiver-0"

    /** Google's Default Media Receiver: plays a URL, shows the title and art. */
    const val DEFAULT_RECEIVER = "CC1AD845"

    private val json = Json { ignoreUnknownKeys = true }

    fun connect() = obj("CONNECT")

    fun close() = obj("CLOSE")

    fun ping() = obj("PING")

    fun pong() = obj("PONG")

    fun getStatus(requestId: Long) = obj("GET_STATUS", requestId)

    fun launch(
        requestId: Long,
        appId: String = DEFAULT_RECEIVER,
    ) = obj("LAUNCH", requestId) { put("appId", appId) }

    fun stopApp(
        requestId: Long,
        sessionId: String,
    ) = obj("STOP", requestId) { put("sessionId", sessionId) }

    fun setVolume(
        requestId: Long,
        level: Float,
    ) = obj("SET_VOLUME", requestId) { put("volume", buildJsonObject { put("level", level.coerceIn(0f, 1f).toDouble()) }) }

    fun setMuted(
        requestId: Long,
        muted: Boolean,
    ) = obj("SET_VOLUME", requestId) { put("volume", buildJsonObject { put("muted", muted) }) }

    fun load(
        requestId: Long,
        media: CastMedia,
        positionMs: Long,
        autoplay: Boolean,
    ) = obj("LOAD", requestId) {
        put(
            "media",
            buildJsonObject {
                put("contentId", media.url)
                put("contentUrl", media.url)
                put("contentType", media.contentType)
                put("streamType", "BUFFERED")
                put(
                    "metadata",
                    buildJsonObject {
                        put("metadataType", MUSIC_TRACK)
                        put("title", media.title)
                        if (media.artist.isNotBlank()) put("artist", media.artist)
                        if (media.album.isNotBlank()) put("albumName", media.album)
                        media.imageUrl?.let { url ->
                            put("images", buildJsonArray { add(buildJsonObject { put("url", url) }) })
                        }
                    },
                )
            },
        )
        put("autoplay", autoplay)
        put("currentTime", positionMs / 1000.0)
    }

    fun play(
        requestId: Long,
        mediaSessionId: Long,
    ) = obj("PLAY", requestId) { put("mediaSessionId", mediaSessionId) }

    fun pause(
        requestId: Long,
        mediaSessionId: Long,
    ) = obj("PAUSE", requestId) { put("mediaSessionId", mediaSessionId) }

    fun stopMedia(
        requestId: Long,
        mediaSessionId: Long,
    ) = obj("STOP", requestId) { put("mediaSessionId", mediaSessionId) }

    fun seek(
        requestId: Long,
        mediaSessionId: Long,
        positionMs: Long,
    ) = obj("SEEK", requestId) {
        put("mediaSessionId", mediaSessionId)
        put("currentTime", positionMs / 1000.0)
    }

    /** Reads a payload; anything unexpected or malformed is [Incoming.Other], never an exception. */
    fun parse(payload: String): Incoming {
        val o =
            try {
                json.parseToJsonElement(payload) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return Incoming.Other
        val type = o.string("type") ?: return Incoming.Other
        return when (type) {
            "PING" -> {
                Incoming.Ping
            }

            "CLOSE" -> {
                Incoming.Close
            }

            "RECEIVER_STATUS" -> {
                (o["status"] as? JsonObject)?.let { Incoming.Receiver(receiverStatus(it), o.long("requestId") ?: 0) }
                    ?: Incoming.Other
            }

            "MEDIA_STATUS" -> {
                Incoming.Media((o["status"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::mediaStatus) })
            }

            "LOAD_FAILED", "LOAD_CANCELLED", "INVALID_REQUEST", "INVALID_PLAYER_STATE", "LAUNCH_ERROR" -> {
                Incoming.Failed(type, o.long("requestId"))
            }

            else -> {
                Incoming.Other
            }
        }
    }

    private fun receiverStatus(status: JsonObject): ReceiverStatus {
        val apps =
            (status["applications"] as? JsonArray).orEmpty().mapNotNull { a ->
                val app = a as? JsonObject ?: return@mapNotNull null
                ReceiverStatus.App(
                    appId = app.string("appId") ?: return@mapNotNull null,
                    sessionId = app.string("sessionId") ?: return@mapNotNull null,
                    transportId = app.string("transportId") ?: return@mapNotNull null,
                )
            }
        val volume = status["volume"] as? JsonObject
        return ReceiverStatus(
            apps,
            volume?.double("level")?.toFloat(),
            (volume?.get("muted") as? JsonPrimitive)?.booleanOrNull,
        )
    }

    private fun mediaStatus(s: JsonObject): MediaStatus? {
        val id = s.long("mediaSessionId") ?: return null
        val state = MediaStatus.PlayerState.entries.firstOrNull { it.name == s.string("playerState") } ?: return null
        val duration = (s["media"] as? JsonObject)?.double("duration")
        return MediaStatus(
            mediaSessionId = id,
            playerState = state,
            positionMs = ((s.double("currentTime") ?: 0.0) * 1000).toLong(),
            durationMs = duration?.takeIf { it > 0 }?.let { (it * 1000).toLong() },
            idleReason = MediaStatus.IdleReason.entries.firstOrNull { it.name == s.string("idleReason") },
        )
    }

    private const val MUSIC_TRACK = 3

    private fun obj(
        type: String,
        requestId: Long? = null,
        body: JsonObjectBuilder.() -> Unit = {},
    ): String =
        buildJsonObject {
            put("type", type)
            requestId?.let { put("requestId", it) }
            body()
        }.toString()

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
}
