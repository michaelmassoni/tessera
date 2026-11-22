package io.github.michaelmassoni.tessera.core.settings

import java.net.URI
import java.net.URISyntaxException

/**
 * A server address the person typed (Music's Subsonic server, Tasks' CalDAV server), tidied and
 * checked. Pure, so it's unit tested.
 */
object ServerAddress {
    enum class Problem {
        /** Not an http(s) address. */
        Bad,

        /** `http://` to an address on the internet. */
        Insecure,
    }

    class Invalid(
        val problem: Problem,
    ) : Exception(problem.name)

    /**
     * Adds `https://` when no scheme is given, lower-cases the host and drops a trailing slash.
     * Throws [Invalid] for anything that isn't an http(s) address, and for `http://` outside a
     * private network. [path] is the part of the path kept ([dropAfter] cuts a pasted API path).
     */
    fun normalize(
        typed: String,
        dropAfter: String? = null,
    ): String {
        var text = typed.trim()
        if (text.isEmpty()) throw Invalid(Problem.Bad)
        if (!text.contains("://")) text = "https://$text"
        val uri =
            try {
                URI(text)
            } catch (_: URISyntaxException) {
                throw Invalid(Problem.Bad)
            }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if (scheme !in setOf("http", "https") || host.isNullOrEmpty()) throw Invalid(Problem.Bad)
        if (scheme == "http" && !isPrivateHost(host)) throw Invalid(Problem.Insecure)
        var path = uri.rawPath ?: ""
        if (dropAfter != null) path = path.substringBefore("$dropAfter/").removeSuffix(dropAfter)
        path = path.trimEnd('/')
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$scheme://${hostPart.lowercase()}$port$path"
    }

    /**
     * Addresses where unencrypted HTTP stays on a network the person controls: private and
     * Tailscale (CGNAT) ranges, loopback, link-local, and local names.
     */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost") return true
        if (listOf(".local", ".lan", ".home.arpa", ".ts.net", ".internal").any { h.endsWith(it) }) return true
        ipv4(h)?.let { (a, b) ->
            return a == 10 ||
                a == 127 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        if (h.contains(':')) {
            return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") ||
                h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")
        }
        // A single word ("nas") only resolves on the local network.
        return !h.contains('.')
    }

    private fun ipv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val n = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return null }
        return n[0] to n[1]
    }
}
