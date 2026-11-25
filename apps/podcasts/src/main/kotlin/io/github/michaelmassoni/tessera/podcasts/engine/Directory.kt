package io.github.michaelmassoni.tessera.podcasts.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/** A show found by searching. */
data class SearchResult(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val artworkUrl: String? = null,
)

/** Searching Apple's and fyyd's podcast directories: URLs and replies. */
object Directory {
    private val json = Json { ignoreUnknownKeys = true }

    fun appleSearchUrl(
        terms: String,
        limit: Int = 30,
    ) = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=$limit&term=${encode(terms)}"

    fun appleLookupUrl(id: String) = "https://itunes.apple.com/lookup?entity=podcast&id=$id"

    fun fyydSearchUrl(
        terms: String,
        limit: Int = 30,
    ) = "https://api.fyyd.de/0.2/search/podcast?count=$limit&title=${encode(terms)}"

    /** Apple's search and lookup replies. */
    fun apple(body: String): List<SearchResult> =
        results(body, "results") { o ->
            SearchResult(
                feedUrl = o.string("feedUrl") ?: return@results null,
                title = o.string("collectionName") ?: o.string("trackName") ?: return@results null,
                author = o.string("artistName").orEmpty(),
                artworkUrl = o.string("artworkUrl600") ?: o.string("artworkUrl100"),
            )
        }

    fun fyyd(body: String): List<SearchResult> =
        results(body, "data") { o ->
            SearchResult(
                feedUrl = o.string("xmlURL") ?: return@results null,
                title = o.string("title") ?: return@results null,
                author = o.string("author").orEmpty(),
                artworkUrl = o.string("layoutImageURL") ?: o.string("imgURL"),
            )
        }

    /** Apple's results first, then fyyd's that Apple didn't have. */
    fun merge(
        apple: List<SearchResult>,
        fyyd: List<SearchResult>,
    ): List<SearchResult> {
        val seen = HashSet<String>()
        return (apple + fyyd).filter { seen.add(key(it.feedUrl)) }
    }

    /** The Apple Podcasts id in a link such as `https://podcasts.apple.com/us/podcast/name/id123`. */
    fun appleId(link: String): String? {
        val m = Regex("""^https?://(podcasts|itunes)\.apple\.com/.*\bid(\d+)""", RegexOption.IGNORE_CASE).find(link.trim())
        return m?.groupValues?.get(2)
    }

    /** What "Add by URL" was given: an Apple link, or a feed address (scheme added if missing). */
    fun feedAddress(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val withScheme =
            when {
                t.startsWith("http://", true) || t.startsWith("https://", true) -> t
                t.startsWith("feed://", true) -> "https://" + t.substring(7)
                t.startsWith("itpc://", true) || t.startsWith("pcast://", true) -> "https://" + t.substringAfter("://")
                else -> "https://$t"
            }
        return withScheme.takeIf { Regex("""^https?://[^/\s]+\.[^/\s]+""").containsMatchIn(it) }
    }

    /** Two feed URLs that differ only in scheme, "www.", a trailing slash or `?format=xml` are the same feed. */
    fun key(feedUrl: String): String =
        feedUrl
            .trim()
            .lowercase()
            .substringAfter("://")
            .removePrefix("www.")
            .removeSuffix("?format=xml")
            .trimEnd('/')

    private fun results(
        body: String,
        field: String,
        map: (JsonObject) -> SearchResult?,
    ): List<SearchResult> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val list = (root[field] as? JsonArray) ?: return emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let(map) }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)
            ?.takeIf {
                it.isString
            }?.content
            ?.takeIf { it.isNotBlank() }

    private fun encode(terms: String) = URLEncoder.encode(terms.trim(), "UTF-8")
}
