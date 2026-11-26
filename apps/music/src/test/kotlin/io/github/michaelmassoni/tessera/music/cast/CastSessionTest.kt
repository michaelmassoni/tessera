package io.github.michaelmassoni.tessera.music.cast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/**
 * [CastSession] against a fake receiver that speaks the protocol the way a Chromecast does: TLS with
 * a self-signed certificate, launch, the media namespace, statuses and the heartbeat.
 */
class CastSessionTest {
    private lateinit var receiver: FakeReceiver

    @Before
    fun start() {
        receiver = FakeReceiver()
    }

    @After
    fun stop() = receiver.close()

    private class Recorder : CastSession.Listener {
        val ready = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val media = LinkedBlockingQueue<MediaStatus>()
        var volume: Float? = null

        override fun onReady(volume: Float?) {
            this.volume = volume
            ready.countDown()
        }

        override fun onReceiver(status: ReceiverStatus) = Unit

        override fun onMedia(status: MediaStatus?) {
            status?.let(media::add)
        }

        override fun onFailed(type: String) = Unit

        override fun onEnded() = ended.countDown()
    }

    private fun session(listener: Recorder) =
        CastSession(CastDevice("id", "Fake TV", "", InetAddress.getLoopbackAddress(), receiver.port, true), listener)

    @Test
    fun `launches the receiver, loads a song, follows it and stops`() {
        val events = Recorder()
        val s = session(events)
        s.start()
        assertTrue(events.ready.await(5, TimeUnit.SECONDS))
        assertEquals(0.3f, events.volume)
        assertEquals(InetAddress.getLoopbackAddress(), s.localAddress)

        s.load(CastMedia("http://127.0.0.1:1/song", "audio/flac", "Slow Tide", "Aurora Bay", "Polar", null), 30_000, autoplay = true)
        val playing = events.media.poll(5, TimeUnit.SECONDS)!!
        assertEquals(MediaStatus.PlayerState.PLAYING, playing.playerState)
        assertEquals(30_000L, playing.positionMs)
        assertEquals(7L, s.mediaSessionId)

        s.pause()
        assertEquals(MediaStatus.PlayerState.PAUSED, events.media.poll(5, TimeUnit.SECONDS)!!.playerState)

        s.stop()
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
        assertTrue(receiver.waitFor { it.contains("\"STOP\"") && it.contains("\"sessionId\":\"s1\"") })
        val launch = receiver.received.first { it.contains("LAUNCH") }
        assertTrue(launch.contains("CC1AD845"))
        val load = receiver.received.first { it.contains("LOAD") }
        assertTrue(load.contains("http://127.0.0.1:1/song"))
    }

    @Test
    fun `answers the receiver's heartbeat`() {
        val events = Recorder()
        session(events).start()
        assertTrue(events.ready.await(5, TimeUnit.SECONDS))
        receiver.send("receiver-0", "sender-0", CastProtocol.HEARTBEAT, """{"type":"PING"}""")
        assertTrue(receiver.waitFor { it.contains("PONG") })
    }

    @Test
    fun `ends when another phone takes over the device`() {
        val events = Recorder()
        session(events).start()
        assertTrue(events.ready.await(5, TimeUnit.SECONDS))
        receiver.send(
            "receiver-0",
            "*",
            CastProtocol.RECEIVER,
            """{"type":"RECEIVER_STATUS","requestId":0,"status":{"applications":[{"appId":"CC1AD845","sessionId":"other","transportId":"t2"}]}}""",
        )
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `ends when the device goes away`() {
        val events = Recorder()
        session(events).start()
        assertTrue(events.ready.await(5, TimeUnit.SECONDS))
        receiver.close()
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a device that isn't there ends at once`() {
        val events = Recorder()
        val port = receiver.port
        receiver.close()
        CastSession(CastDevice("id", "Gone", "", InetAddress.getLoopbackAddress(), port, true), events).start()
        assertTrue(events.ended.await(10, TimeUnit.SECONDS))
        assertEquals(1L, events.ready.count)
    }

    /** One connection's worth of a Chromecast running the Default Media Receiver. */
    private class FakeReceiver {
        private val server: SSLServerSocket
        private var out: OutputStream? = null

        @Volatile private var client: java.net.Socket? = null
        val received = CopyOnWriteArrayList<String>()
        val port: Int get() = server.localPort

        init {
            server = context().serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
            thread(isDaemon = true) {
                try {
                    server.accept().use { socket ->
                        client = socket
                        out = socket.outputStream
                        val input = DataInputStream(socket.inputStream)
                        while (true) handle(CastMessage.readFrom(input))
                    }
                } catch (_: IOException) {
                }
            }
        }

        fun send(
            from: String,
            to: String,
            namespace: String,
            payload: String,
        ) = synchronized(this) { CastMessage(from, to, namespace, payload).writeTo(out!!) }

        fun waitFor(match: (String) -> Boolean): Boolean {
            repeat(50) {
                if (received.any(match)) return true
                Thread.sleep(100)
            }
            return false
        }

        fun close() {
            server.close()
            client?.close()
        }

        private fun handle(m: CastMessage) {
            received += m.payload
            val o = Json.parseToJsonElement(m.payload).jsonObject
            val requestId = o["requestId"]?.jsonPrimitive?.long ?: 0
            when (o["type"]?.jsonPrimitive?.content) {
                "LAUNCH" -> {
                    send(
                        "receiver-0",
                        m.sourceId,
                        CastProtocol.RECEIVER,
                        """{"type":"RECEIVER_STATUS","requestId":$requestId,"status":{"applications":[{"appId":"CC1AD845",
                        "sessionId":"s1","transportId":"t1"}],"volume":{"level":0.3,"muted":false}}}""",
                    )
                }

                "GET_STATUS" -> {
                    send("t1", m.sourceId, CastProtocol.MEDIA, """{"type":"MEDIA_STATUS","requestId":$requestId,"status":[]}""")
                }

                "LOAD" -> {
                    media(requestId, "PLAYING", o["currentTime"]!!.jsonPrimitive.content)
                }

                "PAUSE" -> {
                    media(requestId, "PAUSED", "31.0")
                }

                "STOP" -> {
                    if (m.namespace == CastProtocol.RECEIVER) {
                        send(
                            "receiver-0",
                            m.sourceId,
                            CastProtocol.RECEIVER,
                            """{"type":"RECEIVER_STATUS","requestId":$requestId,"status":{"applications":[]}}""",
                        )
                    }
                }
            }
        }

        private fun media(
            requestId: Long,
            state: String,
            time: String,
        ) = send(
            "t1",
            "sender-0",
            CastProtocol.MEDIA,
            """{"type":"MEDIA_STATUS","requestId":$requestId,"status":[{"mediaSessionId":7,"playerState":"$state","currentTime":$time}]}""",
        )

        companion object {
            /** A throwaway self-signed certificate, made with the JDK's keytool, as a Chromecast makes its own. */
            fun context(): SSLContext {
                val dir = Files.createTempDirectory("cast").toFile()
                val store = File(dir, "receiver.p12")
                val keytool = File(System.getProperty("java.home"), "bin/keytool").path
                val process =
                    ProcessBuilder(
                        keytool,
                        "-genkeypair",
                        "-alias",
                        "r",
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-dname",
                        "CN=fake-chromecast",
                        "-validity",
                        "2",
                        "-storetype",
                        "PKCS12",
                        "-keystore",
                        store.path,
                        "-storepass",
                        "secret",
                        "-keypass",
                        "secret",
                    ).redirectErrorStream(true).start()
                process.inputStream.readBytes()
                check(process.waitFor() == 0) { "keytool failed" }
                val ks = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, "secret".toCharArray()) } }
                dir.deleteRecursively()
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, "secret".toCharArray()) }
                return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            }
        }
    }
}
