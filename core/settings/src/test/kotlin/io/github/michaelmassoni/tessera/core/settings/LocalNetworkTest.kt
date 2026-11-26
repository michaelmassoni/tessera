package io.github.michaelmassoni.tessera.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkTest {
    @Test
    fun `addresses on the local network`() {
        listOf(
            "http://192.168.1.20:7070",
            "10.0.2.2:4533",
            "https://172.20.0.5/remote.php/dav",
            "http://nas.local",
            "nas",
            "http://[fd12:3456::1]:8080",
            "https://pi.home.arpa",
            "http://100.101.102.103",
        ).forEach { assertTrue(it, LocalNetwork.isLocal(it)) }
    }

    @Test
    fun `addresses that aren't`() {
        listOf(
            "https://cloud.example.com",
            "example.org/rss",
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://[::1]",
            "https://8.8.8.8",
            "",
            "not a url at all",
        ).forEach { assertFalse(it, LocalNetwork.isLocal(it)) }
    }

    @Test
    fun `resolved addresses`() {
        fun ip(text: String) = InetAddress.getByName(text)
        assertTrue(LocalNetwork.isLocal(ip("192.168.0.4")))
        assertTrue(LocalNetwork.isLocal(ip("169.254.1.1")))
        assertTrue(LocalNetwork.isLocal(ip("fd00::5")))
        assertTrue(LocalNetwork.isLocal(ip("fe80::1")))
        assertTrue(LocalNetwork.isLocal(ip("100.64.0.1")))
        assertFalse(LocalNetwork.isLocal(ip("100.128.0.1")))
        assertFalse(LocalNetwork.isLocal(ip("127.0.0.1")))
        assertFalse(LocalNetwork.isLocal(ip("93.184.216.34")))
        assertFalse(LocalNetwork.isLocal(ip("2606:4700::1111")))
    }
}
