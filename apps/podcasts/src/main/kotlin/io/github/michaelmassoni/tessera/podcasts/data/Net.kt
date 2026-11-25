package io.github.michaelmassoni.tessera.podcasts.data

import io.github.michaelmassoni.tessera.podcasts.engine.Directory
import io.github.michaelmassoni.tessera.podcasts.engine.FeedParser
import io.github.michaelmassoni.tessera.podcasts.engine.ParsedFeed
import io.github.michaelmassoni.tessera.podcasts.engine.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The result of fetching a feed. */
sealed interface FeedFetch {
    /** Unchanged since the ETag / Last-Modified we sent. */
    data object NotModified : FeedFetch

    data class Fetched(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
        /** The feed moved for good (301/308, or `itunes:new-feed-url`): its new address. */
        val movedTo: String?,
    ) : FeedFetch
}

/** HTTP for feeds, search and downloads. Blocking calls; use from [Dispatchers.IO]. */
object Net {
    const val USER_AGENT = "TesseraPodcasts/0.1 (Android; +https://github.com/michaelmassoni/tessera)"

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            // Feeds and audio often hop between http and https through tracking prefixes.
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }.build()
    }

    /**
     * Fetches and parses the feed at [url].
     * @throws IOException when it can't be fetched, [io.github.michaelmassoni.tessera.podcasts.engine.NotAFeedException] when it isn't a feed.
     */
    fun fetchFeed(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): FeedFetch {
        val request =
            Request
                .Builder()
                .url(url)
                .apply {
                    etag?.let { header("If-None-Match", it) }
                    lastModified?.let { header("If-Modified-Since", it) }
                }.build()
        client.newCall(request).execute().use { response ->
            if (response.code == 304) return FeedFetch.NotModified
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val finalUrl = response.request.url.toString()
            val feed = response.body.byteStream().use { FeedParser.parse(it, finalUrl) }
            val moved = movedFor(response)?.takeIf { it != url } ?: feed.newFeedUrl?.takeIf { it != url && it.startsWith("http") }
            return FeedFetch.Fetched(feed, response.header("ETag"), response.header("Last-Modified"), moved)
        }
    }

    /** The final address when every redirect on the way was permanent. */
    private fun movedFor(response: Response): String? {
        var prior = response.priorResponse ?: return null
        while (true) {
            if (prior.code != 301 && prior.code != 308) return null
            prior = prior.priorResponse ?: break
        }
        return response.request.url.toString()
    }

    /** Searches Apple and fyyd at once; one failing is skipped. @throws IOException when both fail. */
    suspend fun search(terms: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val apple = async { runCatching { Directory.apple(get(Directory.appleSearchUrl(terms))) } }
                val fyyd = async { runCatching { Directory.fyyd(get(Directory.fyydSearchUrl(terms))) } }
                val a = apple.await()
                val f = fyyd.await()
                if (a.isFailure && f.isFailure) throw IOException("search failed", a.exceptionOrNull())
                Directory.merge(a.getOrDefault(emptyList()), f.getOrDefault(emptyList()))
            }
        }

    /** An Apple Podcasts id's feed URL, or null. */
    fun appleFeed(id: String): String? = Directory.apple(get(Directory.appleLookupUrl(id))).firstOrNull()?.feedUrl

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
}
