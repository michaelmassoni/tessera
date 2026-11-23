package io.github.michaelmassoni.tessera.contacts.engine

import io.github.michaelmassoni.tessera.core.dav.Component
import io.github.michaelmassoni.tessera.core.dav.ICal
import io.github.michaelmassoni.tessera.core.dav.Property
import java.util.Base64
import java.util.Locale

/** A typed value as ContactsContract stores it: [type] is its TYPE constant, [label] for TYPE_CUSTOM (0). */
data class Typed(
    val value: String,
    val type: Int,
    val label: String? = null,
)

/** A postal address in StructuredPostal's parts. */
data class PostalAddress(
    val street: String = "",
    val poBox: String = "",
    val city: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
    val type: Int = CardTypes.POSTAL_HOME,
    val label: String? = null,
)

/** A contact as sync reads and writes it (the data rows Tessera maps). */
data class CardContact(
    val uid: String,
    val formattedName: String = "",
    val given: String = "",
    val family: String = "",
    val middle: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val phones: List<Typed> = emptyList(),
    val emails: List<Typed> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val organization: String = "",
    val department: String = "",
    val title: String = "",
    val note: String = "",
    val websites: List<String> = emptyList(),
    /** "1990-04-23", or "--04-23" without a year (ContactsContract's forms). */
    val birthday: String? = null,
    val anniversary: String? = null,
    val relations: List<Typed> = emptyList(),
    val sip: List<String> = emptyList(),
    /** The groups it's in, for the categories method. */
    val categories: List<String> = emptyList(),
)

/** A group kept as its own vCard (the "separate vCards" method): members by UID. */
data class CardGroup(
    val uid: String,
    val name: String,
    val members: List<String>,
)

/** ContactsContract's TYPE values, kept here so the engine has no Android types. */
object CardTypes {
    const val CUSTOM = 0
    const val PHONE_HOME = 1
    const val PHONE_MOBILE = 2
    const val PHONE_WORK = 3
    const val PHONE_FAX_WORK = 4
    const val PHONE_FAX_HOME = 5
    const val PHONE_PAGER = 6
    const val PHONE_OTHER = 7
    const val PHONE_MAIN = 12
    const val PHONE_WORK_MOBILE = 17
    const val EMAIL_HOME = 1
    const val EMAIL_WORK = 2
    const val EMAIL_OTHER = 3
    const val EMAIL_MOBILE = 4
    const val POSTAL_HOME = 1
    const val POSTAL_WORK = 2
    const val POSTAL_OTHER = 3
    const val RELATION_CHILD = 3
    const val RELATION_FATHER = 5
    const val RELATION_FRIEND = 6
    const val RELATION_MOTHER = 8
    const val RELATION_PARENT = 9
    const val RELATION_PARTNER = 10
    const val RELATION_SPOUSE = 14
}

/**
 * vCards (3.0 and 4.0) to and from [CardContact]/[CardGroup] for CardDAV sync. Writing starts from
 * the server's previous text and changes only what changed, so everything Tessera doesn't map
 * (and other apps' X- properties) survives. Writes 3.0, as DAVx5 does. Photos are handled by the
 * caller (they're large): [photo] and [withPhoto] read and set PHOTO. Pure.
 */
object CardMapping {
    /** A vCard's kind: a group (4.0 KIND:group, or Apple's 3.0 X-ADDRESSBOOKSERVER-KIND). */
    fun isGroup(card: Component): Boolean =
        card.first("KIND")?.value.equals("group", true) || card.first("X-ADDRESSBOOKSERVER-KIND")?.value.equals("group", true)

    fun parse(text: String): Component? = ICal.parse(text)?.takeIf { it.name.equals("VCARD", true) }

    // ---- reading ----

    fun readContact(card: Component): CardContact? {
        val uid = card.first("UID")?.value?.let(::stripUrn) ?: return null
        val n =
            card
                .first("N")
                ?.value
                ?.let(::components)
                .orEmpty()
        val org =
            card
                .first("ORG")
                ?.value
                ?.let(::components)
                .orEmpty()
        return CardContact(
            uid = uid,
            formattedName =
                card
                    .first("FN")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            family = n.getOrNull(0).orEmpty(),
            given = n.getOrNull(1).orEmpty(),
            middle = n.getOrNull(2).orEmpty(),
            prefix = n.getOrNull(3).orEmpty(),
            suffix = n.getOrNull(4).orEmpty(),
            nickname =
                card
                    .first("NICKNAME")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            phones = all(card, "TEL").map { (p, label) -> Typed(telValue(p.value), phoneType(types(p)), label).custom(label) },
            emails = all(card, "EMAIL").map { (p, label) -> Typed(ICal.unescape(p.value), emailType(types(p)), label).custom(label) },
            addresses =
                all(card, "ADR").map { (p, label) ->
                    val a = components(p.value)
                    PostalAddress(
                        poBox = a.getOrNull(0).orEmpty(),
                        street = listOf(a.getOrNull(1).orEmpty(), a.getOrNull(2).orEmpty()).filter { it.isNotBlank() }.joinToString("\n"),
                        city = a.getOrNull(3).orEmpty(),
                        region = a.getOrNull(4).orEmpty(),
                        postcode = a.getOrNull(5).orEmpty(),
                        country = a.getOrNull(6).orEmpty(),
                        type = if (label != null) CardTypes.CUSTOM else postalType(types(p)),
                        label = label,
                    )
                },
            organization = org.getOrNull(0).orEmpty(),
            department = org.drop(1).filter { it.isNotBlank() }.joinToString(", "),
            title =
                card
                    .first("TITLE")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            note =
                card
                    .first("NOTE")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            websites = all(card, "URL").map { ICal.unescape(it.first.value) },
            birthday = card.first("BDAY")?.let(::date),
            anniversary = (card.first("ANNIVERSARY") ?: card.first("X-ANNIVERSARY"))?.let(::date),
            relations =
                all(card, "RELATED").map { (p, label) -> Typed(ICal.unescape(p.value), relationType(types(p)), label).custom(label) } +
                    all(card, "X-ABRELATEDNAMES").map { (p, label) ->
                        val type = relationOf(label)
                        Typed(ICal.unescape(p.value), type, if (type == CardTypes.CUSTOM) label else null)
                    },
            sip = all(card, "IMPP").filter { it.first.value.startsWith("sip:", true) }.map { it.first.value.substring(4) },
            categories = card.all("CATEGORIES").flatMap { p -> splitList(p.value) }.distinct(),
        )
    }

    fun readGroup(card: Component): CardGroup? {
        val uid = card.first("UID")?.value?.let(::stripUrn) ?: return null
        val name = (card.first("FN") ?: card.first("N"))?.value?.let { ICal.unescape(it.substringBefore(';')) }.orEmpty()
        val members = (card.all("MEMBER") + card.all("X-ADDRESSBOOKSERVER-MEMBER")).map { stripUrn(it.value) }
        return CardGroup(uid, name, members.distinct())
    }

    /** The photo's bytes (3.0 base64 or a 4.0 data: URI), or null. */
    fun photo(card: Component): ByteArray? {
        val p = card.first("PHOTO") ?: return null
        val v = p.value.trim()
        val data = if (v.startsWith("data:", true)) v.substringAfter(",") else v
        if (p.param("VALUE").equals("uri", true) && !v.startsWith("data:", true)) return null
        return runCatching { Base64.getMimeDecoder().decode(data) }.getOrNull()
    }

    // ---- writing ----

    /** [contact] as vCard 3.0 text, starting from [previous] so unmapped properties survive. */
    fun writeContact(
        contact: CardContact,
        previous: Component?,
    ): String {
        val card = previous?.takeIf { !isGroup(it) } ?: newCard()
        val before = previous?.let(::readContact)
        card.set(Property("VERSION", value = "3.0"))
        card.set(Property("UID", value = contact.uid))
        val fn = contact.formattedName.ifBlank { displayName(contact) }
        if (before == null || before.formattedName != fn) card.set(Property("FN", value = ICal.escape(fn)))
        val n = listOf(contact.family, contact.given, contact.middle, contact.prefix, contact.suffix)
        if (before == null || listOf(before.family, before.given, before.middle, before.prefix, before.suffix) != n) {
            card.set(Property("N", value = n.joinToString(";") { ICal.escape(it) }))
        }
        if (before == null || before.nickname != contact.nickname) setText(card, "NICKNAME", contact.nickname)
        if (before == null || before.phones != contact.phones) {
            replace(card, "TEL", contact.phones.map { t -> Triple(phoneTypes(t.type), t.label, t.value) })
        }
        if (before == null || before.emails != contact.emails) {
            replace(
                card,
                "EMAIL",
                contact.emails.map { t ->
                    Triple(listOf("INTERNET") + emailTypes(t.type), t.label, ICal.escape(t.value))
                },
            )
        }
        if (before == null || before.addresses != contact.addresses) {
            replace(
                card,
                "ADR",
                contact.addresses.map { a ->
                    val parts = listOf(a.poBox, "", a.street, a.city, a.region, a.postcode, a.country)
                    Triple(postalTypes(a.type), a.label, parts.joinToString(";") { ICal.escape(it) })
                },
            )
        }
        val org = listOf(contact.organization) + contact.department.split(", ").filter { it.isNotBlank() }
        if (before == null || before.organization != contact.organization || before.department != contact.department) {
            if (contact.organization.isBlank() && contact.department.isBlank()) {
                card.remove("ORG")
            } else {
                card.set(Property("ORG", value = org.joinToString(";") { ICal.escape(it) }))
            }
        }
        if (before == null || before.title != contact.title) setText(card, "TITLE", contact.title)
        if (before == null || before.note != contact.note) setText(card, "NOTE", contact.note)
        if (before == null ||
            before.websites != contact.websites
        ) {
            replace(card, "URL", contact.websites.map { Triple(emptyList(), null, it) })
        }
        if (before == null || before.birthday != contact.birthday) setDate(card, "BDAY", contact.birthday)
        if (before == null || before.anniversary != contact.anniversary) {
            card.remove("ANNIVERSARY")
            setDate(card, "X-ANNIVERSARY", contact.anniversary)
        }
        if (before == null || before.relations != contact.relations) {
            card.remove("RELATED")
            replace(
                card,
                "X-ABRELATEDNAMES",
                contact.relations.map { r ->
                    Triple(emptyList(), r.label ?: relationLabel(r.type), ICal.escape(r.value))
                },
            )
        }
        if (before == null || before.sip != contact.sip) {
            card.properties.removeAll { it.name.equals("IMPP", true) && it.value.startsWith("sip:", true) }
            contact.sip.forEach { card.properties += Property("IMPP", value = "sip:$it") }
        }
        if (before == null || before.categories != contact.categories) {
            card.remove("CATEGORIES")
            if (contact.categories.isNotEmpty()) {
                card.properties +=
                    Property("CATEGORIES", value = contact.categories.joinToString(",") { it.replace("\\", "\\\\").replace(",", "\\,") })
            }
        }
        return ICal.write(card)
    }

    /** [group] as a vCard 3.0 group (Apple's X-ADDRESSBOOKSERVER properties, which every server keeps). */
    fun writeGroup(
        group: CardGroup,
        previous: Component?,
    ): String {
        val card = previous?.takeIf(::isGroup) ?: newCard()
        card.set(Property("VERSION", value = "3.0"))
        card.set(Property("UID", value = group.uid))
        card.set(Property("X-ADDRESSBOOKSERVER-KIND", value = "group"))
        card.set(Property("FN", value = ICal.escape(group.name)))
        card.set(Property("N", value = ICal.escape(group.name)))
        card.remove("MEMBER")
        card.remove("X-ADDRESSBOOKSERVER-MEMBER")
        group.members.forEach { card.properties += Property("X-ADDRESSBOOKSERVER-MEMBER", value = "urn:uuid:$it") }
        return ICal.write(card)
    }

    /** Sets or removes PHOTO (3.0 base64 JPEG). */
    fun withPhoto(
        text: String,
        jpeg: ByteArray?,
    ): String {
        val card = parse(text) ?: return text
        card.remove("PHOTO")
        if (jpeg !=
            null
        ) {
            card.properties +=
                Property("PHOTO", listOf("ENCODING" to "b", "TYPE" to "JPEG"), Base64.getEncoder().encodeToString(jpeg))
        }
        return ICal.write(card)
    }

    // ---- helpers ----

    private fun newCard() =
        Component("VCARD", mutableListOf(Property("VERSION", value = "3.0"), Property("PRODID", value = "-//Tessera//Contacts//EN")))

    fun displayName(c: CardContact): String =
        listOf(c.prefix, c.given, c.middle, c.family, c.suffix).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.organization }

    /** Properties named [name] (also in an Apple "item1." group), each with its X-ABLabel if any. */
    private fun all(
        card: Component,
        name: String,
    ): List<Pair<Property, String?>> =
        card.properties
            .filter { it.name.substringAfterLast('.').equals(name, true) }
            .map { p ->
                val group = p.name.substringBeforeLast('.', "").ifEmpty { null }
                val label =
                    group?.let { g ->
                        card.properties
                            .firstOrNull { it.name.equals("$g.X-ABLabel", true) }
                            ?.value
                            ?.let(::appleLabel)
                    }
                p to label
            }

    /** Apple's built-in labels look like "_$!<Spouse>!$_". */
    private fun appleLabel(v: String): String = Regex("^_\\\$!<(.*)>!\\\$_\$").find(v)?.groupValues?.get(1) ?: ICal.unescape(v)

    private fun Typed.custom(label: String?) =
        if (label != null &&
            type == CardTypes.CUSTOM
        ) {
            this
        } else if (label != null) {
            copy(type = CardTypes.CUSTOM)
        } else {
            this
        }

    /** Replaces every [name] property (and its Apple label) with [values]: (TYPEs, custom label, value). */
    private fun replace(
        card: Component,
        name: String,
        values: List<Triple<List<String>, String?, String>>,
    ) {
        val groups =
            card.properties
                .filter { it.name.substringAfterLast('.').equals(name, true) }
                .mapNotNull { it.name.substringBeforeLast('.', "").ifEmpty { null } }
                .toSet()
        card.properties.removeAll { p ->
            p.name.substringAfterLast('.').equals(name, true) ||
                (p.name.substringAfterLast('.').equals("X-ABLabel", true) && p.name.substringBeforeLast('.', "") in groups)
        }
        var item =
            (
                card.properties
                    .mapNotNull {
                        Regex("^item(\\d+)\\.", RegexOption.IGNORE_CASE)
                            .find(it.name)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }.maxOrNull()
                    ?: 0
            ) +
                1
        for ((types, label, value) in values) {
            val params = if (types.isEmpty()) emptyList() else listOf("TYPE" to types.joinToString(","))
            if (label != null) {
                val g = "item${item++}"
                card.properties += Property("$g.$name", params, value)
                card.properties += Property("$g.X-ABLabel", value = ICal.escape(label))
            } else {
                card.properties += Property(name, params, value)
            }
        }
    }

    private fun setText(
        card: Component,
        name: String,
        value: String,
    ) {
        if (value.isBlank()) card.remove(name) else card.set(Property(name, value = ICal.escape(value)))
    }

    private fun setDate(
        card: Component,
        name: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            card.remove(name)
            return
        }
        // 3.0 has no year-less dates: Apple writes 1604 with X-APPLE-OMIT-YEAR.
        if (value.startsWith("--")) {
            card.set(Property(name, listOf("X-APPLE-OMIT-YEAR" to "1604"), "1604-${value.removePrefix("--")}"))
        } else {
            card.set(Property(name, value = value))
        }
    }

    /** BDAY/ANNIVERSARY as "1990-04-23" or "--04-23". */
    private fun date(p: Property): String? {
        val v = p.value.trim()
        if (p.param("X-APPLE-OMIT-YEAR") != null || v.startsWith("1604")) {
            return Regex("^\\d{4}-?(\\d{2})-?(\\d{2})").find(v)?.let { "--${it.groupValues[1]}-${it.groupValues[2]}" }
        }
        Regex("^--(\\d{2})-?(\\d{2})").find(v)?.let { return "--${it.groupValues[1]}-${it.groupValues[2]}" }
        Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})").find(v)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }
        return null
    }

    private fun types(p: Property): Set<String> =
        p.params
            .filter { it.first.equals("TYPE", true) }
            .flatMap { it.second.split(',') }
            .map { it.trim().uppercase(Locale.ROOT) }
            .toSet() +
            // vCard 2.1 bare parameters ("TEL;CELL:…").
            p.params.filter { it.second.isEmpty() }.map { it.first.uppercase(Locale.ROOT) }

    private fun telValue(v: String) = ICal.unescape(v.removePrefix("tel:"))

    private fun stripUrn(v: String) = v.trim().removePrefix("urn:uuid:").removePrefix("URN:UUID:")

    /** A structured value (N, ADR, ORG) split on unescaped semicolons, each part unescaped. */
    fun components(v: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                sb.append(c).append(v[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                out += ICal.unescape(sb.toString())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        out += ICal.unescape(sb.toString())
        return out
    }

    private fun splitList(v: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                sb.append(v[i + 1])
                i += 2
                continue
            }
            if (c == ',') {
                out += sb.toString().trim()
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out.filter { it.isNotEmpty() }
    }

    private fun phoneType(t: Set<String>): Int =
        when {
            "CELL" in t && "WORK" in t -> CardTypes.PHONE_WORK_MOBILE
            "CELL" in t -> CardTypes.PHONE_MOBILE
            "FAX" in t && "WORK" in t -> CardTypes.PHONE_FAX_WORK
            "FAX" in t -> CardTypes.PHONE_FAX_HOME
            "PAGER" in t -> CardTypes.PHONE_PAGER
            "MAIN" in t || ("PREF" in t && t.size == 1) -> CardTypes.PHONE_MAIN
            "WORK" in t -> CardTypes.PHONE_WORK
            "HOME" in t -> CardTypes.PHONE_HOME
            "VOICE" in t && t.size == 1 -> CardTypes.PHONE_OTHER
            t.isEmpty() -> CardTypes.PHONE_MOBILE
            else -> CardTypes.PHONE_OTHER
        }

    private fun phoneTypes(type: Int): List<String> =
        when (type) {
            CardTypes.PHONE_HOME -> listOf("HOME", "VOICE")
            CardTypes.PHONE_MOBILE -> listOf("CELL")
            CardTypes.PHONE_WORK -> listOf("WORK", "VOICE")
            CardTypes.PHONE_FAX_WORK -> listOf("WORK", "FAX")
            CardTypes.PHONE_FAX_HOME -> listOf("HOME", "FAX")
            CardTypes.PHONE_PAGER -> listOf("PAGER")
            CardTypes.PHONE_MAIN -> listOf("MAIN")
            CardTypes.PHONE_WORK_MOBILE -> listOf("WORK", "CELL")
            CardTypes.CUSTOM -> emptyList()
            else -> listOf("VOICE")
        }

    private fun emailType(t: Set<String>): Int =
        when {
            "WORK" in t -> CardTypes.EMAIL_WORK
            "HOME" in t -> CardTypes.EMAIL_HOME
            "CELL" in t -> CardTypes.EMAIL_MOBILE
            else -> CardTypes.EMAIL_OTHER
        }

    private fun emailTypes(type: Int): List<String> =
        when (type) {
            CardTypes.EMAIL_WORK -> listOf("WORK")
            CardTypes.EMAIL_HOME -> listOf("HOME")
            CardTypes.EMAIL_MOBILE -> listOf("CELL")
            else -> emptyList()
        }

    private fun postalType(t: Set<String>): Int =
        when {
            "WORK" in t -> CardTypes.POSTAL_WORK
            "HOME" in t -> CardTypes.POSTAL_HOME
            else -> CardTypes.POSTAL_OTHER
        }

    private fun postalTypes(type: Int): List<String> =
        when (type) {
            CardTypes.POSTAL_WORK -> listOf("WORK")
            CardTypes.POSTAL_HOME -> listOf("HOME")
            else -> emptyList()
        }

    private fun relationType(t: Set<String>): Int =
        when {
            "SPOUSE" in t -> CardTypes.RELATION_SPOUSE
            "CHILD" in t -> CardTypes.RELATION_CHILD
            "PARENT" in t -> CardTypes.RELATION_PARENT
            "FRIEND" in t -> CardTypes.RELATION_FRIEND
            "SWEETHEART" in t -> CardTypes.RELATION_PARTNER
            else -> CardTypes.CUSTOM
        }

    private fun relationOf(label: String?): Int =
        when (label?.lowercase(Locale.ROOT)) {
            "spouse" -> CardTypes.RELATION_SPOUSE
            "child" -> CardTypes.RELATION_CHILD
            "parent" -> CardTypes.RELATION_PARENT
            "mother" -> CardTypes.RELATION_MOTHER
            "father" -> CardTypes.RELATION_FATHER
            "friend" -> CardTypes.RELATION_FRIEND
            "partner" -> CardTypes.RELATION_PARTNER
            else -> CardTypes.CUSTOM
        }

    private fun relationLabel(type: Int): String =
        when (type) {
            CardTypes.RELATION_SPOUSE -> "_\$!<Spouse>!\$_"
            CardTypes.RELATION_CHILD -> "_\$!<Child>!\$_"
            CardTypes.RELATION_PARENT -> "_\$!<Parent>!\$_"
            CardTypes.RELATION_MOTHER -> "_\$!<Mother>!\$_"
            CardTypes.RELATION_FATHER -> "_\$!<Father>!\$_"
            CardTypes.RELATION_FRIEND -> "_\$!<Friend>!\$_"
            CardTypes.RELATION_PARTNER -> "_\$!<Partner>!\$_"
            else -> "_\$!<Other>!\$_"
        }
}
