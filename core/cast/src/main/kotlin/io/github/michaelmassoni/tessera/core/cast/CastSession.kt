package io.github.michaelmassoni.tessera.core.cast

import android.annotation.SuppressLint
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/** A cast device found on the Wi-Fi. */
data class CastDevice(
    /** The device's own id (mDNS `id`), stable across addresses. */
    val id: String,
    val name: String,
    val model: String,
    val host: InetAddress,
    val port: Int,
    /** It has a screen (a TV or Chromecast) rather than being a speaker. */
    val video: Boolean,
)

/**
 * One connection to a cast device running the Default Media Receiver: TLS to the device, the
 * virtual connections, the heartbeat, launching the receiver and talking to its media player.
 * Messages arrive on a reader thread and go to [listener] there; sending is thread-safe.
 */
class CastSession(
    val device: CastDevice,
    private val listener: Listener,
) {
    fun listenerIs(other: Listener) = listener === other

    interface Listener {
        /** The receiver is running and ready for media. */
        fun onReady(volume: Float?)

        fun onReceiver(status: ReceiverStatus)

        fun onMedia(status: MediaStatus?)

        fun onFailed(type: String)

        /** The session is over: closed, lost, or the device was taken over. */
        fun onEnded()
    }

    private val requests = AtomicLong(1)
    private val launchRequest = requests.getAndIncrement()
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "cast-send").apply { isDaemon = true } }
    private var socket: SSLSocket? = null

    @Volatile private var transportId: String? = null

    @Volatile private var appSessionId: String? = null

    @Volatile var mediaSessionId: Long? = null
        private set

    @Volatile private var lastHeard = 0L

    @Volatile private var ended = false

    /** This phone's address on the network the device is on: where the device can reach the media server. */
    @Volatile var localAddress: InetAddress? = null
        private set

    /** Connects and launches the receiver, on a thread of its own; the result arrives through [listener]. */
    fun start() {
        thread(name = "cast-session", isDaemon = true) {
            try {
                val s = open()
                socket = s
                localAddress = s.localAddress
                lastHeard = System.currentTimeMillis()
                send(CastProtocol.PLATFORM, CastProtocol.CONNECTION, CastProtocol.connect())
                send(CastProtocol.PLATFORM, CastProtocol.RECEIVER, CastProtocol.launch(launchRequest))
                thread(name = "cast-heartbeat", isDaemon = true) { heartbeat() }
                read(DataInputStream(s.inputStream.buffered()))
            } catch (_: IOException) {
                // Couldn't connect, or the connection dropped.
            } finally {
                end()
            }
        }
    }

    fun load(
        media: CastMedia,
        positionMs: Long,
        autoplay: Boolean,
        speed: Float = 1f,
    ) = media(CastProtocol.load(requests.getAndIncrement(), media, positionMs, autoplay, speed))

    fun setSpeed(speed: Float) = mediaSessionId?.let { media(CastProtocol.setSpeed(requests.getAndIncrement(), it, speed)) }

    fun play() = mediaSessionId?.let { media(CastProtocol.play(requests.getAndIncrement(), it)) }

    fun pause() = mediaSessionId?.let { media(CastProtocol.pause(requests.getAndIncrement(), it)) }

    fun seek(positionMs: Long) = mediaSessionId?.let { media(CastProtocol.seek(requests.getAndIncrement(), it, positionMs)) }

    fun stopMedia() = mediaSessionId?.let { media(CastProtocol.stopMedia(requests.getAndIncrement(), it)) }

    fun setVolume(level: Float) =
        post(CastProtocol.PLATFORM, CastProtocol.RECEIVER, CastProtocol.setVolume(requests.getAndIncrement(), level))

    fun setMuted(muted: Boolean) =
        post(CastProtocol.PLATFORM, CastProtocol.RECEIVER, CastProtocol.setMuted(requests.getAndIncrement(), muted))

    /** Stops the receiver on the device (the TV goes back to its home screen) and disconnects. */
    fun stop() {
        appSessionId?.let { post(CastProtocol.PLATFORM, CastProtocol.RECEIVER, CastProtocol.stopApp(requests.getAndIncrement(), it)) }
        end()
    }

    /** Disconnects, leaving whatever is playing on the device. Safe from any thread, any number of times. */
    fun end() {
        synchronized(lock) {
            if (ended) return
            ended = true
        }
        // After anything already queued (a STOP), on the writer: never network on the caller's thread.
        val transport = transportId
        execute {
            transport?.let { runCatching { send(it, CastProtocol.CONNECTION, CastProtocol.close()) } }
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
        writer.shutdown()
        listener.onEnded()
    }

    private fun media(payload: String) {
        val to = transportId ?: return
        post(to, CastProtocol.MEDIA, payload)
    }

    /** Sends from the writer thread, so callers (the main thread) never touch the network. */
    private fun post(
        to: String,
        namespace: String,
        payload: String,
    ) {
        if (ended) return
        execute {
            try {
                send(to, namespace, payload)
            } catch (_: IOException) {
                end()
            }
        }
    }

    private fun execute(task: () -> Unit) {
        try {
            writer.execute(task)
        } catch (_: RejectedExecutionException) {
            // Already shut down: the session is over.
        }
    }

    private fun send(
        to: String,
        namespace: String,
        payload: String,
    ) {
        val s = socket ?: throw IOException("Not connected")
        synchronized(lock) { CastMessage(CastProtocol.SENDER, to, namespace, payload).writeTo(s.outputStream) }
    }

    private fun read(input: DataInputStream) {
        while (!ended) {
            val m = CastMessage.readFrom(input)
            lastHeard = System.currentTimeMillis()
            if (m.destinationId != CastProtocol.SENDER && m.destinationId != "*") continue
            when (val message = CastProtocol.parse(m.payload)) {
                Incoming.Ping -> {
                    send(m.sourceId, CastProtocol.HEARTBEAT, CastProtocol.pong())
                }

                Incoming.Close -> {
                    if (m.sourceId == transportId || m.sourceId == CastProtocol.PLATFORM) return
                }

                is Incoming.Receiver -> {
                    receiver(message.status, message.requestId)
                }

                is Incoming.Media -> {
                    if (m.sourceId == transportId) {
                        val status = message.statuses.firstOrNull()
                        mediaSessionId = status?.mediaSessionId ?: mediaSessionId
                        listener.onMedia(status)
                    }
                }

                is Incoming.Failed -> {
                    listener.onFailed(message.type)
                }

                Incoming.Other -> {}
            }
        }
    }

    private fun receiver(
        status: ReceiverStatus,
        requestId: Long,
    ) {
        val app = status.apps.firstOrNull { it.appId == CastProtocol.DEFAULT_RECEIVER }
        val ours = appSessionId
        when {
            // The answer to our launch: a receiver already running for someone else isn't ours.
            ours == null && app != null && requestId == launchRequest -> {
                appSessionId = app.sessionId
                transportId = app.transportId
                send(app.transportId, CastProtocol.CONNECTION, CastProtocol.connect())
                send(app.transportId, CastProtocol.MEDIA, CastProtocol.getStatus(requests.getAndIncrement()))
                listener.onReady(status.volume)
            }

            ours == null -> {}

            // Our receiver was stopped (from the TV's remote) or replaced by another app or phone.
            status.apps.none { it.sessionId == ours } -> {
                throw IOException("Receiver gone")
            }

            else -> {
                listener.onReceiver(status)
            }
        }
    }

    private fun heartbeat() {
        val started = System.currentTimeMillis()
        while (!ended) {
            Thread.sleep(HEARTBEAT_MS)
            if (ended) return
            if (System.currentTimeMillis() - lastHeard > TIMEOUT_MS) return end()
            // The receiver never started (or never said so).
            if (appSessionId == null && System.currentTimeMillis() - started > TIMEOUT_MS) return end()
            post(CastProtocol.PLATFORM, CastProtocol.HEARTBEAT, CastProtocol.ping())
        }
    }

    private fun open(): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(AnyCertificate), null)
        val s = context.socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(device.host, device.port), CONNECT_TIMEOUT_MS)
        s.startHandshake()
        return s
    }

    /**
     * Cast devices present self-signed certificates (made on the device, renewed daily); a sender
     * can't check them against anything without Google's device-authentication root. The link is
     * still encrypted, and the device only ever gets addresses on the phone (docs/music.md, Casting).
     */
    @SuppressLint("CustomX509TrustManager")
    private object AnyCertificate : X509TrustManager {
        // Only ever a client here.
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ): Unit = throw CertificateException("Not a server")

        // Any certificate the device presents, as long as it presents one.
        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) {
            if (chain.isNullOrEmpty()) throw CertificateException("No certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val HEARTBEAT_MS = 5_000L
        const val TIMEOUT_MS = 20_000L
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
