package io.github.michaelmassoni.tessera.tasks.sync

import io.github.michaelmassoni.tessera.tasks.engine.Dav
import io.github.michaelmassoni.tessera.tasks.engine.DavResponse
import io.github.michaelmassoni.tessera.tasks.engine.Multistatus
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why talking to the server failed. */
enum class SyncProblem { Unreachable, WrongLogin, NotCalDav, Other }

class SyncException(
    val problem: SyncProblem,
    message: String? = null,
) : Exception(message ?: problem.name)

/** A reply: status, body, and the ETag header (for PUT). */
data class DavReply(
    val code: Int,
    val body: String,
    val etag: String?,
)

/**
 * Talks CalDAV to one server as one user (Basic auth over HTTPS, as OpenCloud and Radicale
 * expect). Blocking: call off the main thread.
 */
class CalDavClient(
    private val username: String,
    private val password: String,
    private val http: OkHttpClient = shared,
) {
    private val xml = "application/xml; charset=utf-8".toMediaType()
    private val ics = "text/calendar; charset=utf-8".toMediaType()

    fun send(
        method: String,
        url: String,
        body: String? = null,
        depth: String? = null,
        headers: Map<String, String> = emptyMap(),
        calendar: Boolean = false,
    ): DavReply {
        val request =
            Request
                .Builder()
                .url(url)
                .method(method, body?.toRequestBody(if (calendar) ics else xml))
                .header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
                .apply {
                    depth?.let { header("Depth", it) }
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
        try {
            http.newCall(request).execute().use { r ->
                if (r.code == 401 || (r.code == 403 && method == "PROPFIND")) throw SyncException(SyncProblem.WrongLogin)
                return DavReply(r.code, r.body.string(), r.header("ETag"))
            }
        } catch (e: IOException) {
            throw SyncException(SyncProblem.Unreachable, e.message)
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncProblem.Other, e.message)
        }
    }

    private fun multistatus(reply: DavReply): Multistatus {
        if (reply.code !=
            207
        ) {
            throw SyncException(
                if (reply.code == 404 ||
                    reply.code == 405
                ) {
                    SyncProblem.NotCalDav
                } else {
                    SyncProblem.Other
                },
                "HTTP ${reply.code}",
            )
        }
        return try {
            Dav.parse(reply.body)
        } catch (e: Exception) {
            throw SyncException(SyncProblem.NotCalDav, e.message)
        }
    }

    /**
     * The calendar home for [address]: its current-user-principal (asked of the address, then of
     * /.well-known/caldav), then that principal's calendar-home-set.
     */
    fun findHome(address: String): String {
        val base = if (address.endsWith("/")) address else "$address/"
        val problems = mutableListOf<SyncException>()
        val principal =
            listOf(base, Dav.resolve(base, "/.well-known/caldav")).firstNotNullOfOrNull { url ->
                runCatching { multistatus(send("PROPFIND", url, Dav.principalBody, "0")) }
                    .getOrElse {
                        if (it !is SyncException || it.problem == SyncProblem.WrongLogin) throw it
                        problems += it
                        null
                    }?.responses
                    ?.firstNotNullOfOrNull { r -> r.principal?.let { Dav.resolve(url, it) } }
            }
                // A server that can't be reached says so, rather than "not CalDAV".
                ?: throw problems.firstOrNull { it.problem == SyncProblem.Unreachable } ?: SyncException(SyncProblem.NotCalDav)
        val home =
            multistatus(send("PROPFIND", principal, Dav.homeSetBody, "0"))
                .responses
                .firstNotNullOfOrNull { it.homeSet }
                ?: throw SyncException(SyncProblem.NotCalDav)
        return Dav.resolve(principal, home)
    }

    /** The calendars in [home] that hold tasks (VTODO), with absolute hrefs. */
    fun taskCollections(home: String): List<DavResponse> =
        multistatus(send("PROPFIND", home, Dav.collectionsBody, "1"))
            .responses
            .filter { it.isCalendar && (it.components.isEmpty() || "VTODO" in it.components) }
            .map { it.copy(href = Dav.resolve(home, it.href)) }

    /**
     * What changed in [collection] since [token] (all of it for null). Null when the server doesn't
     * do sync-collection or no longer knows the token: compare etags instead.
     */
    fun changes(
        collection: String,
        token: String?,
    ): Multistatus? {
        val reply = send("REPORT", collection, Dav.syncCollectionBody(token), "1")
        if (reply.code != 207) return null
        val m = multistatus(reply)
        return m.copy(
            responses =
                m.responses.map { it.copy(href = Dav.resolve(collection, it.href)) }.filter {
                    it.href.trimEnd('/') !=
                        collection.trimEnd('/')
                },
        )
    }

    /** Every task resource in [collection] with its etag. */
    fun etags(collection: String): List<DavResponse> =
        multistatus(send("REPORT", collection, Dav.etagsBody, "1")).responses.map { it.copy(href = Dav.resolve(collection, it.href)) }

    /** The calendar data of [hrefs], fetched in batches. */
    fun fetch(
        collection: String,
        hrefs: List<String>,
    ): List<DavResponse> =
        hrefs.chunked(50).flatMap { batch ->
            multistatus(send("REPORT", collection, Dav.multigetBody(batch.map { java.net.URI(it).rawPath }), "1"))
                .responses
                .map { it.copy(href = Dav.resolve(collection, it.href)) }
        }

    /** Uploads; [etag] null means "only if it doesn't exist yet". Returns the reply (412: changed on the server). */
    fun put(
        href: String,
        body: String,
        etag: String?,
    ): DavReply =
        send(
            "PUT",
            href,
            body,
            headers =
                if (etag ==
                    null
                ) {
                    mapOf("If-None-Match" to "*")
                } else {
                    mapOf("If-Match" to etag)
                },
            calendar = true,
        )

    fun delete(
        href: String,
        etag: String?,
    ): DavReply = send("DELETE", href, headers = etag?.let { mapOf("If-Match" to it) }.orEmpty())

    /** Makes a task list (a VTODO calendar) under [home]; returns its href. */
    fun makeList(
        home: String,
        id: String,
        name: String,
    ): String {
        val href = Dav.resolve(if (home.endsWith("/")) home else "$home/", "$id/")
        val reply = send("MKCALENDAR", href, Dav.mkcalendarBody(name))
        if (reply.code !in 200..299) throw SyncException(SyncProblem.Other, "MKCALENDAR ${reply.code}")
        return href
    }

    fun rename(
        collection: String,
        name: String,
    ) {
        send("PROPPATCH", collection, Dav.renameBody(name))
    }

    fun deleteList(collection: String) {
        send("DELETE", collection)
    }

    companion object {
        val shared: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
