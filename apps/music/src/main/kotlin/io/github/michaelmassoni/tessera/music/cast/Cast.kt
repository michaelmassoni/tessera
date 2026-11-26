package io.github.michaelmassoni.tessera.music.cast

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import io.github.michaelmassoni.tessera.music.PlaybackService
import io.github.michaelmassoni.tessera.music.server.ArtProvider
import io.github.michaelmassoni.tessera.music.server.ServerPlayback
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
 * the media server it fetches from. [PlaybackService] watches [link] and switches its media
 * session to a [RemotePlayer] while one is there.
 */
@OptIn(UnstableApi::class)
object Cast {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "cast-load").apply { isDaemon = true } }

    private val _state = MutableStateFlow<CastState>(CastState.Off)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val _link = MutableStateFlow<Link?>(null)
    val link: StateFlow<Link?> = _link.asStateFlow()

    private var discovery: CastDiscovery? = null
    private var session: CastSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun discovery(context: Context): CastDiscovery = discovery ?: CastDiscovery(context.applicationContext).also { discovery = it }

    /** Android 17 asks before an app reaches devices on the Wi-Fi; earlier versions allow it with INTERNET. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED

    // Manifest.permission.ACCESS_LOCAL_NETWORK, spelled out: the constant is API 37 and is only used from there.
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

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
        _link.value = Link(app, s, server, volume)
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
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera-music:cast").apply { acquire() }
        val wifi = context.getSystemService(WifiManager::class.java)
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "tessera-music:cast").apply { acquire() }
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    /** The connection as [RemotePlayer] sees it. */
    class Link(
        private val app: Context,
        private val session: CastSession,
        val server: MediaServer,
        val startVolume: Float?,
    ) : RemoteLink {
        private val sources = ServerPlayback.dataSourceFactory(app)

        override val deviceName: String get() = session.device.name
        override val mediaSessionId: Long? get() = session.mediaSessionId
        override var events: RemoteLink.Events? = null

        @Volatile private var generation = 0

        override fun load(
            item: MediaItem,
            positionMs: Long,
            autoplay: Boolean,
        ) {
            val mine = ++generation
            io.execute {
                val media = media(item)
                main.post {
                    if (mine != generation) return@post // another song was picked meanwhile
                    if (media == null) events?.onLoadFailed() else session.load(media, positionMs, autoplay)
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

        override fun stopMedia() {
            generation++
            session.stopMedia()
        }

        override fun setVolume(level: Float) = Cast.setVolume(level)

        override fun setMuted(muted: Boolean) = session.setMuted(muted)

        /** The song and its cover, published on the media server. Off the main thread; null if the song can't be opened. */
        private fun media(item: MediaItem): CastMedia? {
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
            val song = DataSourceMedia(sources, uri)
            val type = song.sniff() ?: return null
            val m = item.mediaMetadata
            val albumId = m.extras?.getLong(PlaybackService.EXTRA_ALBUM_ID) ?: 0
            val artUri = m.artworkUri ?: albumId.takeIf { it > 0 }?.let(ArtProvider::localUri)
            val art = artUri?.let { DataSourceMedia(sources, it) }
            val artType = art?.sniff()
            return CastMedia(
                url = server.publish(type, song),
                contentType = type,
                title = m.title?.toString().orEmpty(),
                artist = m.artist?.toString().orEmpty(),
                album = m.albumTitle?.toString().orEmpty(),
                imageUrl = if (art != null && artType != null) server.publish(artType, art) else null,
            )
        }
    }

    /** A song or cover read the way the phone's player reads it: files, and server songs through the cache. */
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

        /** The type from the first bytes; null if it can't be opened. Unknown audio is sent as MP3, which most servers transcode to. */
        fun sniff(): String? =
            try {
                open(0).use { o ->
                    val head = ByteArray(MediaType.HEAD)
                    var n = 0
                    while (n < head.size) {
                        val r = o.input.read(head, n, head.size - n)
                        if (r < 0) break
                        n += r
                    }
                    MediaType.sniff(head.copyOf(n))
                        ?: if (uri.scheme == "content" && uri.authority?.endsWith(".art") == true) "image/jpeg" else "audio/mpeg"
                }
            } catch (_: IOException) {
                null
            }
    }

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
            return if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) -1 else n
        }

        override fun close() = source.close()
    }
}
