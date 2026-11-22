package io.github.michaelmassoni.tessera.core.dav

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Which service: calendars and task lists (CalDAV) or address books (CardDAV). */
enum class DavKind(
    /** Where discovery looks when the address given isn't the service itself. */
    val wellKnown: String,
) {
    CalDav("/.well-known/caldav"),
    CardDav("/.well-known/carddav"),
}

/** One `<response>` of a WebDAV multistatus, with the properties sync uses. */
data class DavResponse(
    val href: String,
    /** The response's status (404 for a resource deleted since the sync token). */
    val status: Int = 200,
    val etag: String? = null,
    /** calendar-data or address-data. */
    val data: String? = null,
    val displayName: String? = null,
    /** `#RRGGBB` or `#RRGGBBAA` (Apple's calendar-color). */
    val color: String? = null,
    val isCalendar: Boolean = false,
    val isAddressBook: Boolean = false,
    /** supported-calendar-component-set, e.g. VTODO, VEVENT. */
    val components: Set<String> = emptySet(),
    val principal: String? = null,
    val calendarHome: String? = null,
    val addressBookHome: String? = null,
    /** From current-user-privilege-set: false when the person may only read. Null if not reported. */
    val writable: Boolean? = null,
    val syncToken: String? = null,
)

data class Multistatus(
    val responses: List<DavResponse>,
    val syncToken: String? = null,
)

/** WebDAV, CalDAV and CardDAV: request bodies and reading replies. Pure, so it's unit tested. */
object Dav {
    private const val D = "DAV:"
    private const val C = "urn:ietf:params:xml:ns:caldav"
    private const val CARD = "urn:ietf:params:xml:ns:carddav"
    private const val A = "http://apple.com/ns/ical/"
    private const val CS = "http://calendarserver.org/ns/"

    val principalBody =
        """<d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>"""

    fun homeSetBody(kind: DavKind) =
        when (kind) {
            DavKind.CalDav -> {
                """<d:propfind xmlns:d="DAV:" xmlns:c="$C"><d:prop><c:calendar-home-set/></d:prop></d:propfind>"""
            }

            DavKind.CardDav -> {
                """<d:propfind xmlns:d="DAV:" xmlns:card="$CARD">""" +
                    "<d:prop><card:addressbook-home-set/></d:prop></d:propfind>"
            }
        }

    /** The collections in a home: what they are, their names, colours, privileges and sync tokens. */
    val collectionsBody =
        """<d:propfind xmlns:d="DAV:" xmlns:c="$C" xmlns:a="$A" xmlns:cs="$CS"><d:prop>""" +
            "<d:resourcetype/><d:displayname/><a:calendar-color/><c:supported-calendar-component-set/>" +
            "<d:current-user-privilege-set/><d:sync-token/></d:prop></d:propfind>"

    fun syncCollectionBody(token: String?) =
        """<d:sync-collection xmlns:d="DAV:"><d:sync-token>${xml(token.orEmpty())}</d:sync-token>""" +
            "<d:sync-level>1</d:sync-level><d:prop><d:getetag/></d:prop></d:sync-collection>"

    /** Every resource's etag: of [component] (VTODO, VEVENT) for CalDAV, every vCard for CardDAV. */
    fun etagsBody(
        kind: DavKind,
        component: String = "VTODO",
    ) = when (kind) {
        DavKind.CalDav -> {
            """<c:calendar-query xmlns:d="DAV:" xmlns:c="$C"><d:prop><d:getetag/></d:prop>""" +
                """<c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="$component"/></c:comp-filter></c:filter></c:calendar-query>"""
        }

        DavKind.CardDav -> {
            """<card:addressbook-query xmlns:d="DAV:" xmlns:card="$CARD"><d:prop><d:getetag/></d:prop></card:addressbook-query>"""
        }
    }

    fun multigetBody(
        hrefs: List<String>,
        kind: DavKind = DavKind.CalDav,
    ) = when (kind) {
        DavKind.CalDav -> {
            """<c:calendar-multiget xmlns:d="DAV:" xmlns:c="$C"><d:prop><d:getetag/><c:calendar-data/></d:prop>""" +
                hrefs.joinToString("") { "<d:href>${xml(it)}</d:href>" } + "</c:calendar-multiget>"
        }

        DavKind.CardDav -> {
            """<card:addressbook-multiget xmlns:d="DAV:" xmlns:card="$CARD"><d:prop><d:getetag/><card:address-data/></d:prop>""" +
                hrefs.joinToString("") { "<d:href>${xml(it)}</d:href>" } + "</card:addressbook-multiget>"
        }
    }

    /** A new calendar holding [component] (VEVENT or VTODO), with a colour if given. */
    fun mkcalendarBody(
        name: String,
        component: String = "VTODO",
        color: Int? = null,
    ) = """<c:mkcalendar xmlns:d="DAV:" xmlns:c="$C" xmlns:a="$A"><d:set><d:prop><d:displayname>${xml(name)}</d:displayname>""" +
        (color?.let { "<a:calendar-color>${colorHex(it)}</a:calendar-color>" } ?: "") +
        """<c:supported-calendar-component-set><c:comp name="$component"/></c:supported-calendar-component-set>""" +
        "</d:prop></d:set></c:mkcalendar>"

    fun renameBody(name: String) =
        """<d:propertyupdate xmlns:d="DAV:"><d:set><d:prop><d:displayname>${xml(
            name,
        )}</d:displayname></d:prop></d:set></d:propertyupdate>"""

    fun colorBody(color: Int) =
        """<d:propertyupdate xmlns:d="DAV:" xmlns:a="$A"><d:set><d:prop>""" +
            "<a:calendar-color>${colorHex(color)}</a:calendar-color></d:prop></d:set></d:propertyupdate>"

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
                        .children(D, "propstat")
                        .filter { status(it.child(D, "status")?.textContent) in 200..299 }
                        .mapNotNull { it.child(D, "prop") }

                fun prop(
                    ns: String,
                    name: String,
                ) = okProps.firstNotNullOfOrNull { it.child(ns, name) }
                val type = prop(D, "resourcetype")
                DavResponse(
                    href = href,
                    status = r.child(D, "status")?.textContent?.let(::status) ?: 200,
                    etag = prop(D, "getetag")?.textContent?.trim()?.ifEmpty { null },
                    data = (prop(C, "calendar-data") ?: prop(CARD, "address-data"))?.textContent?.ifBlank { null },
                    displayName = prop(D, "displayname")?.textContent?.trim()?.ifEmpty { null },
                    color = prop(A, "calendar-color")?.textContent?.trim()?.ifEmpty { null },
                    isCalendar = type?.child(C, "calendar") != null,
                    isAddressBook = type?.child(CARD, "addressbook") != null,
                    components =
                        prop(C, "supported-calendar-component-set")
                            ?.children(C, "comp")
                            ?.map { it.getAttribute("name").uppercase(Locale.ROOT) }
                            ?.toSet()
                            .orEmpty(),
                    principal = prop(D, "current-user-principal")?.child(D, "href")?.textContent?.trim(),
                    calendarHome = prop(C, "calendar-home-set")?.child(D, "href")?.textContent?.trim(),
                    addressBookHome = prop(CARD, "addressbook-home-set")?.child(D, "href")?.textContent?.trim(),
                    writable = prop(D, "current-user-privilege-set")?.let(::canWrite),
                    syncToken = prop(D, "sync-token")?.textContent?.trim()?.ifEmpty { null },
                )
            }
        return Multistatus(responses, root.child(D, "sync-token")?.textContent?.trim())
    }

    /** True when the privilege set includes write, write-content or all. */
    private fun canWrite(set: Element): Boolean =
        set.children(D, "privilege").any { p ->
            p.child(D, "write") != null || p.child(D, "write-content") != null || p.child(D, "all") != null
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
        if (hex.length < 6) return null
        val rgb = hex.take(6).toLongOrNull(16) ?: return null
        val alpha = if (hex.length >= 8) hex.substring(6, 8).toLongOrNull(16) ?: 0xFF else 0xFF
        return ((alpha shl 24) or rgb).toInt()
    }

    /** `#RRGGBBFF`, as Apple's calendar-color is written (opaque). */
    fun colorHex(argb: Int): String = "#%06X".format(Locale.ROOT, argb and 0xFFFFFF) + "FF"
}
