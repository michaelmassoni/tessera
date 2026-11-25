package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class YarrFolder(
    val id: Long,
    val title: String,
)

@Serializable
data class YarrFeed(
    val id: Long,
    @SerialName("folder_id") val folderId: Long? = null,
    val title: String = "",
    val link: String = "",
    @SerialName("feed_link") val feedLink: String = "",
    /** A data: URI (yarr 2.6 and later). */
    val icon: String? = null,
    /** Older yarr: the icon is at /api/feeds/{id}/icon. */
    @SerialName("has_icon") val hasIcon: Boolean = false,
)

@Serializable
data class YarrItem(
    val id: Long,
    @SerialName("feed_id") val feedId: Long,
    val title: String = "",
    val link: String = "",
    val content: String = "",
    val date: String = "",
    val status: String = "unread",
)

@Serializable
data class YarrPage(
    val list: List<YarrItem> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

/** What adding a feed did. */
sealed interface AddResult {
    data class Added(
        val feed: YarrFeed,
    ) : AddResult

    /** The address offered several feeds: pick one. */
    data class Choices(
        val choices: List<Pair<String, String>>,
    ) : AddResult

    data object NotFound : AddResult
}

/**
 * yarr's own JSON API (docs/feeds.md). Signs in with `/login` when the server asks (401) and
 * keeps the `auth` cookie; with no username it assumes a server without auth. Blocking: call off
 * the main thread. Errors are [SyncException]s.
 */
class YarrClient(
    address: String,
    private val username: String?,
    private val password: String?,
    private val http: OkHttpClient = shared,
    /** The auth cookie from an earlier sign-in, and where to keep a new one. */
    private var cookie: String? = null,
    private val onCookie: (String?) -> Unit = {},
) {
    private val base: HttpUrl =
        try {
            (address.trimEnd('/') + "/").toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncProblem.Other, e.message)
        }
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): HttpUrl {
        val b = base.newBuilder().addPathSegments(path)
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private data class Reply(
        val code: Int,
        val body: String,
    )

    private fun call(
        method: String,
        url: HttpUrl,
        body: RequestBody? = null,
        retried: Boolean = false,
    ): Reply {
        val request =
            Request
                .Builder()
                .url(url)
                .method(method, body ?: if (method == "GET" || method == "DELETE") null else ByteArray(0).toRequestBody())
                .apply { cookie?.let { header("Cookie", "auth=$it") } }
                .build()
        val reply =
            try {
                http.newCall(request).execute().use { Reply(it.code, it.body.string()) }
            } catch (e: IOException) {
                throw SyncException(SyncProblem.Unreachable, e.message)
            }
        if (reply.code == 401) {
            if (retried || username.isNullOrEmpty()) throw SyncException(SyncProblem.WrongLogin)
            login()
            return call(method, url, body, retried = true)
        }
        if (reply.code == 404) throw SyncException(SyncProblem.NotCalDav, "HTTP 404")
        if (reply.code !in 200..299) throw SyncException(SyncProblem.Other, "HTTP ${reply.code}")
        return reply
    }

    private inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): T {
        val reply = call("GET", url(path, query))
        return try {
            json.decodeFromString<T>(reply.body)
        } catch (e: SerializationException) {
            // A page that isn't yarr's JSON: not a yarr server (or a login page in front of it).
            throw SyncException(SyncProblem.NotCalDav, e.message)
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncProblem.NotCalDav, e.message)
        }
    }

    /** `POST /login`; keeps the `auth` cookie. */
    fun login() {
        val form =
            FormBody
                .Builder()
                .add("username", username.orEmpty())
                .add("password", password.orEmpty())
                .build()
        val request =
            Request
                .Builder()
                .url(url("login"))
                .post(form)
                .build()
        val value =
            try {
                http.newCall(request).execute().use { r ->
                    if (r.code == 401) throw SyncException(SyncProblem.WrongLogin)
                    if (r.code !in 200..399) throw SyncException(SyncProblem.Other, "HTTP ${r.code}")
                    r
                        .headers("Set-Cookie")
                        .firstOrNull { it.startsWith("auth=") }
                        ?.substringAfter("auth=")
                        ?.substringBefore(';')
                }
            } catch (e: IOException) {
                throw SyncException(SyncProblem.Unreachable, e.message)
            } ?: throw SyncException(SyncProblem.NotCalDav, "no auth cookie")
        cookie = value
        onCookie(value)
    }

    /** Checks the address and login: `GET /api/status` (signing in if asked). */
    fun check() {
        val status = get<JsonObject>("api/status")
        if ("stats" !in status && "running" !in status) throw SyncException(SyncProblem.NotCalDav)
    }

    fun folders(): List<YarrFolder> = get("api/folders")

    fun feeds(): List<YarrFeed> = get("api/feeds")

    fun feedErrors(): Map<Long, String> =
        get<JsonObject>("api/feeds/errors")
            .mapNotNull { (k, v) ->
                k.toLongOrNull()?.let { it to ((v as? JsonPrimitive)?.content ?: "") }
            }.toMap()

    /** A page of items, newest first: [status] (unread, read, starred) or all, after item [after]. */
    fun items(
        status: String? = null,
        after: Long? = null,
    ): YarrPage =
        get(
            "api/items",
            buildMap {
                status?.let { put("status", it) }
                after?.let { put("after", it.toString()) }
            },
        )

    /** One item with its (sanitised) content. */
    fun item(id: Long): YarrItem = get("api/items/$id")

    fun setStatus(
        id: Long,
        status: String,
    ) {
        call("PUT", url("api/items/$id"), buildJsonObject { put("status", status) }.toString().toRequestBody(jsonType))
    }

    /** Asks the server to fetch every feed now. */
    fun refresh() {
        call("POST", url("api/feeds/refresh"))
    }

    fun addFeed(
        address: String,
        folderId: Long?,
    ): AddResult {
        val body =
            buildJsonObject {
                put("url", address)
                folderId?.let { put("folder_id", it) }
            }.toString().toRequestBody(jsonType)
        val reply =
            try {
                call("POST", url("api/feeds"), body)
            } catch (e: SyncException) {
                // yarr answers 500 when it finds no feed at the address.
                if (e.problem == SyncProblem.Other) return AddResult.NotFound
                throw e
            }
        if (reply.body.isBlank()) return AddResult.NotFound
        val obj =
            try {
                json.parseToJsonElement(reply.body).jsonObject
            } catch (_: IllegalArgumentException) {
                return AddResult.NotFound
            }
        return when ((obj["status"] as? JsonPrimitive)?.content) {
            "success" -> {
                AddResult.Added(json.decodeFromJsonElement(YarrFeed.serializer(), obj["feed"] ?: return AddResult.NotFound))
            }

            "multiple" -> {
                val choices =
                    (obj["choice"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { c ->
                        val o = c as? JsonObject ?: return@mapNotNull null
                        val u = o["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        u to (o["title"]?.jsonPrimitive?.content ?: u)
                    }
                AddResult.Choices(choices)
            }

            else -> {
                AddResult.NotFound
            }
        }
    }

    /** Renames and/or moves a feed; [folderId] null with [moveFolder] takes it out of its folder. */
    fun updateFeed(
        id: Long,
        title: String? = null,
        moveFolder: Boolean = false,
        folderId: Long? = null,
    ) {
        val body =
            buildJsonObject {
                title?.let { put("title", it) }
                if (moveFolder) put("folder_id", folderId?.let { JsonPrimitive(it) } ?: JsonNull)
            }.toString().toRequestBody(jsonType)
        call("PUT", url("api/feeds/$id"), body)
    }

    fun deleteFeed(id: Long) {
        call("DELETE", url("api/feeds/$id"))
    }

    fun createFolder(title: String): YarrFolder {
        val reply = call("POST", url("api/folders"), buildJsonObject { put("title", title) }.toString().toRequestBody(jsonType))
        return try {
            json.decodeFromString(YarrFolder.serializer(), reply.body)
        } catch (e: SerializationException) {
            throw SyncException(SyncProblem.Other, e.message)
        }
    }

    fun renameFolder(
        id: Long,
        title: String,
    ) {
        call("PUT", url("api/folders/$id"), buildJsonObject { put("title", title) }.toString().toRequestBody(jsonType))
    }

    fun deleteFolder(id: Long) {
        call("DELETE", url("api/folders/$id"))
    }

    /** Adds the feeds in an OPML file (yarr skips ones it has). */
    fun importOpml(xml: String) {
        val body =
            okhttp3.MultipartBody
                .Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("opml", "feeds.opml", xml.toRequestBody("text/x-opml; charset=utf-8".toMediaType()))
                .build()
        call("POST", url("opml/import"), body)
    }

    /** The page at [link], reduced to its main text by yarr's readability (`/page`). */
    fun fullArticle(link: String): String {
        val obj = get<JsonObject>("page", mapOf("url" to link))
        val content = (obj["content"] as? JsonPrimitive)?.content.orEmpty()
        if (content.startsWith("error:")) throw SyncException(SyncProblem.Other, content)
        return content
    }

    /** An older yarr's feed icon. */
    fun icon(feedId: Long): ByteArray? {
        val request =
            Request
                .Builder()
                .url(url("api/feeds/$feedId/icon"))
                .apply { cookie?.let { header("Cookie", "auth=$it") } }
                .build()
        return try {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body.bytes() else null }
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        val shared: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }
}
