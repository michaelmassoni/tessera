package io.github.michaelmassoni.tessera.tasks.engine

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/** One `<response>` of a WebDAV multistatus, with the properties CalDAV sync uses. */
data class DavResponse(
    val href: String,
    /** The response's status (404 for a resource deleted since the sync token). */
    val status: Int = 200,
    val etag: String? = null,
    val calendarData: String? = null,
    val displayName: String? = null,
    /** `#RRGGBB` or `#RRGGBBAA` (Apple's calendar-color). */
    val color: String? = null,
    val isCalendar: Boolean = false,
    /** supported-calendar-component-set, e.g. VTODO, VEVENT. */
    val components: Set<String> = emptySet(),
    val principal: String? = null,
    val homeSet: String? = null,
)

data class Multistatus(
    val responses: List<DavResponse>,
    val syncToken: String? = null,
)

/** WebDAV and CalDAV: request bodies and reading replies. Pure, so it's unit tested. */
object Dav {
    private const val D = "DAV:"
    private const val C = "urn:ietf:params:xml:ns:caldav"
    private const val A = "http://apple.com/ns/ical/"
    private const val CS = "http://calendarserver.org/ns/"

    val principalBody =
        """<d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>"""

    val homeSetBody =
        """<d:propfind xmlns:d="DAV:" xmlns:c="$C"><d:prop><c:calendar-home-set/></d:prop></d:propfind>"""

    val collectionsBody =
        """<d:propfind xmlns:d="DAV:" xmlns:c="$C" xmlns:a="$A" xmlns:cs="$CS"><d:prop>""" +
            "<d:resourcetype/><d:displayname/><a:calendar-color/><c:supported-calendar-component-set/><d:sync-token/>" +
            "</d:prop></d:propfind>"

    fun syncCollectionBody(token: String?) =
        """<d:sync-collection xmlns:d="DAV:"><d:sync-token>${xml(token.orEmpty())}</d:sync-token>""" +
            "<d:sync-level>1</d:sync-level><d:prop><d:getetag/></d:prop></d:sync-collection>"

    val etagsBody =
        """<c:calendar-query xmlns:d="DAV:" xmlns:c="$C"><d:prop><d:getetag/></d:prop>""" +
            """<c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VTODO"/></c:comp-filter></c:filter></c:calendar-query>"""

    fun multigetBody(hrefs: List<String>) =
        """<c:calendar-multiget xmlns:d="DAV:" xmlns:c="$C"><d:prop><d:getetag/><c:calendar-data/></d:prop>""" +
            hrefs.joinToString("") { "<d:href>${xml(it)}</d:href>" } + "</c:calendar-multiget>"

    fun mkcalendarBody(name: String) =
        """<c:mkcalendar xmlns:d="DAV:" xmlns:c="$C"><d:set><d:prop><d:displayname>${xml(name)}</d:displayname>""" +
            """<c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set>""" +
            "</d:prop></d:set></c:mkcalendar>"

    fun renameBody(name: String) =
        """<d:propertyupdate xmlns:d="DAV:"><d:set><d:prop><d:displayname>${xml(
            name,
        )}</d:displayname></d:prop></d:set></d:propertyupdate>"""

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Reads a multistatus reply. Hrefs are left as the server wrote them (paths, usually). */
    fun parse(body: String): Multistatus {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // No external entities or DTDs from a server.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                isExpandEntityReferences = false
            }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(body)))
        val root = doc.documentElement
        val responses =
            root.children(D, "response").map { r ->
                val href =
                    r
                        .child(D, "href")
                        ?.textContent
                        ?.trim()
                        .orEmpty()
                val okProps =
                    r
                        .children(
                            D,
                            "propstat",
                        ).filter { status(it.child(D, "status")?.textContent) in 200..299 }
                        .mapNotNull { it.child(D, "prop") }

                fun prop(
                    ns: String,
                    name: String,
                ) = okProps.firstNotNullOfOrNull { it.child(ns, name) }
                DavResponse(
                    href = href,
                    status = r.child(D, "status")?.textContent?.let(::status) ?: 200,
                    etag = prop(D, "getetag")?.textContent?.trim()?.ifEmpty { null },
                    calendarData = prop(C, "calendar-data")?.textContent?.ifBlank { null },
                    displayName = prop(D, "displayname")?.textContent?.trim()?.ifEmpty { null },
                    color = prop(A, "calendar-color")?.textContent?.trim()?.ifEmpty { null },
                    isCalendar = prop(D, "resourcetype")?.child(C, "calendar") != null,
                    components =
                        prop(C, "supported-calendar-component-set")
                            ?.children(C, "comp")
                            ?.map { it.getAttribute("name").uppercase() }
                            ?.toSet()
                            .orEmpty(),
                    principal = prop(D, "current-user-principal")?.child(D, "href")?.textContent?.trim(),
                    homeSet = prop(C, "calendar-home-set")?.child(D, "href")?.textContent?.trim(),
                )
            }
        return Multistatus(responses, root.child(D, "sync-token")?.textContent?.trim())
    }

    /** "HTTP/1.1 404 Not Found" → 404. */
    private fun status(line: String?): Int =
        line
            ?.trim()
            ?.split(' ')
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 200

    private fun Element.children(
        ns: String,
        name: String,
    ): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n is Element && n.namespaceURI == ns && n.localName == name) out += n
        }
        return out
    }

    private fun Element.child(
        ns: String,
        name: String,
    ) = children(ns, name).firstOrNull()

    /** An href (often a path) made absolute against [base]. */
    fun resolve(
        base: String,
        href: String,
    ): String = URI(base).resolve(href).toString()

    /** ARGB from `#RRGGBB` or `#RRGGBBAA`. */
    fun color(text: String?): Int? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        val rgb = hex.take(6).toLongOrNull(16) ?: return null
        val alpha = if (hex.length >= 8) hex.substring(6, 8).toLongOrNull(16) ?: 0xFF else 0xFF
        return ((alpha shl 24) or rgb).toInt()
    }
}
