package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.feeds.engine.Discovery
import io.github.michaelmassoni.tessera.feeds.engine.FeedParser
import io.github.michaelmassoni.tessera.feeds.engine.NotAFeed
import io.github.michaelmassoni.tessera.feeds.engine.ParsedFeed
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** A conditional fetch's outcome. */
sealed interface Fetched {
    data class Fresh(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
    ) : Fetched

    /** 304: nothing new. */
    data object Unchanged : Fetched

    data class Failed(
        val reason: String,
    ) : Fetched
}

/** Fetches feeds on the phone ("On this phone" mode). Blocking: call off the main thread. */
class LocalFetcher(
    private val http: OkHttpClient = YarrClient.shared,
) {
    private fun request(url: String) =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.8")

    fun fetch(
        url: String,
        etag: String?,
        lastModified: String?,
    ): Fetched {
        val request =
            try {
                request(url)
                    .apply {
                        etag?.let { header("If-None-Match", it) }
                        lastModified?.let { header("If-Modified-Since", it) }
                    }.build()
            } catch (e: IllegalArgumentException) {
                return Fetched.Failed(e.message ?: "bad address")
            }
        return try {
            http.newCall(request).execute().use { r ->
                when {
                    r.code == 304 -> {
                        Fetched.Unchanged
                    }

                    !r.isSuccessful -> {
                        Fetched.Failed("HTTP ${r.code}")
                    }

                    else -> {
                        val bytes = r.body.bytes()
                        Fetched.Fresh(FeedParser.parse(bytes, r.request.url.toString()), r.header("ETag"), r.header("Last-Modified"))
                    }
                }
            }
        } catch (e: IOException) {
            Fetched.Failed(e.message ?: "unreachable")
        } catch (_: NotAFeed) {
            Fetched.Failed("not a feed")
        }
    }

    /**
     * The feeds at [address]: itself when it's a feed, else the feeds its page links to.
     * Empty when there are none; throws IOException when it can't be reached.
     */
    fun discover(address: String): List<Discovery.Found> {
        val url = normalise(address) ?: return emptyList()
        val request = request(url).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val bytes = r.body.bytes()
            val finalUrl = r.request.url.toString()
            val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.UTF_8)
            if (Discovery.looksLikeFeed(head)) {
                return try {
                    val feed = FeedParser.parse(bytes, finalUrl)
                    listOf(Discovery.Found(finalUrl, feed.title))
                } catch (_: NotAFeed) {
                    emptyList()
                }
            }
            return Discovery.feedsIn(String(bytes, Charsets.UTF_8), finalUrl)
        }
    }

    /** The site's `/favicon.ico`, or null. */
    fun favicon(siteUrl: String): ByteArray? {
        val site = siteUrl.toHttpUrlOrNull() ?: return null
        val url =
            site
                .newBuilder()
                .encodedPath("/favicon.ico")
                .query(null)
                .fragment(null)
                .build()
        return try {
            http
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build(),
                ).execute()
                .use { r ->
                    val type = r.header("Content-Type").orEmpty()
                    if (r.isSuccessful &&
                        (type.startsWith("image/") || type.isEmpty())
                    ) {
                        r.body.bytes().takeIf { it.size in 1..200_000 }
                    } else {
                        null
                    }
                }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        const val USER_AGENT = "TesseraFeeds/0.1 (+https://github.com/michaelmassoni/tessera)"

        /** Adds https:// to an address typed without a scheme. */
        fun normalise(address: String): String? {
            val t = address.trim()
            if (t.isEmpty()) return null
            val withScheme = if (t.contains("://")) t else "https://$t"
            return withScheme.toHttpUrlOrNull()?.toString()
        }
    }
}
