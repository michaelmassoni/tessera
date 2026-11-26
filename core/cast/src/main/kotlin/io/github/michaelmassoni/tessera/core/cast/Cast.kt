package io.github.michaelmassoni.tessera.core.cast

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import io.github.michaelmassoni.tessera.core.settings.LocalNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors

/** Where casting is, for the Cast button and sheet. */
sealed interface CastState {
    data object Off : CastState

    data class Connecting(
        val device: CastDevice,
    ) : CastState

    data class Connected(
        val device: CastDevice,
        val volume: Float,
        val muted: Boolean,
    ) : CastState

    /** Connecting to [device] didn't work; shown until the next try. */
    data class Failed(
        val device: CastDevice,
    ) : CastState
}

/**
 * Casting for the whole app (one per process): discovery, the connection to one device, and
 * the media server it fetches from. The app's playback service sets [content] and watches [link],
 * switching its media session to a [RemotePlayer] while there is one (docs/music.md, Casting).
 */
@OptIn(UnstableApi::class)
object Cast {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cast-load").apply { isDaemon = true } }

    private val _state = MutableStateFlow<CastState>(CastState.Off)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val _link = MutableStateFlow<Link?>(null)
    val link: StateFlow<Link?> = _link.asStateFlow()

    /** What this app sends the device for each item; set by its playback service. */
    @Volatile var content: CastContent? = null

    private var discovery: CastDiscovery? = null
    private var session: CastSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun discovery(context: Context): CastDiscovery = discovery ?: CastDiscovery(context.applicationContext).also { discovery = it }

    /** Android 17 asks before an app reaches devices on the Wi-Fi; earlier versions allow it with INTERNET. */
    fun needsPermission(context: Context): Boolean = !LocalNetwork.granted(context)

    const val PERMISSION = LocalNetwork.PERMISSION

    /** Starts casting to [device], ending any session with another one first. */
    fun connect(
        context: Context,
        device: CastDevice,
    ) {
        val app = context.applicationContext
        session?.let {
            if (it.device.id == device.id) return
            session = null // so its ending is ignored
            it.end()
            _link.value?.server?.close()
            _link.value = null
            release()
        }
        _state.value = CastState.Connecting(device)
        val s = CastSession(device, Events(app))
        session = s
        s.start()
    }

    /** A session's news, on its reader thread; acted on on the main thread, and only while it's the current session. */
    private class Events(
        private val app: Context,
    ) : CastSession.Listener {
        private fun current(block: (CastSession) -> Unit) {
            main.post { session?.takeIf { it.listenerIs(this) }?.let(block) }
        }

        override fun onReady(volume: Float?) = current { ready(app, it, volume) }

        override fun onReceiver(status: ReceiverStatus) =
            current {
                val now = _state.value as? CastState.Connected ?: return@current
                _state.value = now.copy(volume = status.volume ?: now.volume, muted = status.muted ?: now.muted)
                _link.value?.events?.onVolume(status.volume, status.muted)
            }

        override fun onMedia(status: MediaStatus?) = current { _link.value?.events?.onMedia(status) }

        override fun onFailed(type: String) = current { if (type == "LOAD_FAILED") _link.value?.events?.onLoadFailed() }

        override fun onEnded() = current { ended(it.device) }
    }

    /** Stops casting: the device goes back to its home screen and playback comes back to the phone, paused. */
    fun disconnect() {
        session?.stop()
    }

    fun setVolume(level: Float) {
        session?.setVolume(level)
        (_state.value as? CastState.Connected)?.let { _state.value = it.copy(volume = level) }
    }

    /** Forgets a failure, for the sheet's next showing. */
    fun clearFailure() {
        if (_state.value is CastState.Failed) _state.value = CastState.Off
    }

    private fun ready(
        app: Context,
        s: CastSession,
        volume: Float?,
    ) {
        if (_link.value != null) return
        val local = s.localAddress
        val server =
            try {
                local?.let { MediaServer(it, s.device.host) }
            } catch (_: IOException) {
                null
            }
        if (server == null) {
            s.end()
            return
        }
        hold(app)
        _state.value = CastState.Connected(s.device, volume ?: 0.5f, false)
        _link.value = Link(s, server, volume)
    }

    private fun ended(device: CastDevice) {
        val wasConnected = _link.value != null
        _link.value?.server?.close()
        _link.value = null
        session = null
        release()
        _state.value = if (wasConnected || _state.value !is CastState.Connecting) CastState.Off else CastState.Failed(device)
    }

    // Keep serving and answering the heartbeat with the screen off, only while casting.
    @SuppressLint("WakelockTimeout") // released when the session ends, however long the listening
    private fun hold(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:cast").apply { acquire() }
        val wifi = context.getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "tessera:cast").apply { acquire() }
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    /** The connection as [RemotePlayer] sees it. */
    class Link(
        private val session: CastSession,
        val server: MediaServer,
        val startVolume: Float?,
    ) : RemoteLink {
        private val phone = PhoneServer(server)

        override val deviceName: String get() = session.device.name
        override val mediaSessionId: Long? get() = session.mediaSessionId
        override var events: RemoteLink.Events? = null

        @Volatile private var generation = 0

        override fun load(
            item: MediaItem,
            positionMs: Long,
            autoplay: Boolean,
            speed: Float,
        ) {
            val mine = ++generation
            io.execute {
                val media =
                    try {
                        content?.media(item, phone)
                    } catch (_: IOException) {
                        null
                    }
                main.post {
                    if (mine != generation) return@post // another item was picked meanwhile
                    if (media == null) events?.onLoadFailed() else session.load(media, positionMs, autoplay, speed)
                }
            }
        }

        override fun play() {
            session.play()
        }

        override fun pause() {
            session.pause()
        }

        override fun seek(positionMs: Long) {
            session.seek(positionMs)
        }

        override fun setSpeed(speed: Float) {
            session.setSpeed(speed)
        }

        override fun stopMedia() {
            generation++
            session.stopMedia()
        }

        override fun setVolume(level: Float) = Cast.setVolume(level)

        override fun setMuted(muted: Boolean) = session.setMuted(muted)
    }
}

/**
 * What an app sends the cast device for each item of its queue: worked out off the main thread,
 * with [PhoneServer] for anything that lives on the phone. Null when the item can't be cast.
 */
fun interface CastContent {
    fun media(
        item: MediaItem,
        phone: PhoneServer,
    ): CastMedia?
}

/** Something served to the cast device: its address and type. */
data class Served(
    val url: String,
    val type: String,
)

/** Serves things on the phone (files, or anything a Media3 data source reads) to the cast device. */
class PhoneServer internal constructor(
    private val server: MediaServer,
) {
    /**
     * [uri] read through [sources] (the app's own, so caches and server sign-in work as when
     * playing on the phone), with its type from its first bytes or [fallbackType]; null if it
     * can't be opened.
     */
    @OptIn(UnstableApi::class)
    fun serve(
        uri: Uri,
        sources: DataSource.Factory,
        fallbackType: String,
    ): Served? {
        val media = DataSourceMedia(sources, uri)
        val type = media.sniff(fallbackType) ?: return null
        return Served(server.publish(type, media), type)
    }
}

/** A song or cover read the way the phone's player reads it. */
@OptIn(UnstableApi::class)
private class DataSourceMedia(
    private val factory: DataSource.Factory,
    private val uri: Uri,
) : MediaSource {
    override fun open(position: Long): Opened {
        val source = factory.createDataSource()
        val length =
            source.open(
                DataSpec
                    .Builder()
                    .setUri(uri)
                    .setPosition(position)
                    .build(),
            )
        return Opened(DataSourceStream(source), length)
    }

    /** The type from the first bytes, else [fallback]; null if it can't be opened. */
    fun sniff(fallback: String): String? =
        try {
            open(0).use { o ->
                val head = ByteArray(MediaType.HEAD)
                var n = 0
                while (n < head.size) {
                    val r = o.input.read(head, n, head.size - n)
                    if (r < 0) break
                    n += r
                }
                MediaType.sniff(head.copyOf(n)) ?: fallback
            }
        } catch (_: IOException) {
            null
        }
}

@OptIn(UnstableApi::class)
private class DataSourceStream(
    private val source: DataSource,
) : InputStream() {
    private val one = ByteArray(1)

    override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (len == 0) return 0
        val n = source.read(b, off, len)
        return if (n == C.RESULT_END_OF_INPUT) -1 else n
    }

    override fun close() = source.close()
}
