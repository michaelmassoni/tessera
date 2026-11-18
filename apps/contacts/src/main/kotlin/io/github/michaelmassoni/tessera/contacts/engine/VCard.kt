package io.github.michaelmassoni.tessera.contacts.engine

import java.io.ByteArrayOutputStream

/** A contact read from a vCard. Types are vCard TYPE values in upper case ("CELL", "WORK", ...). */
data class VCardContact(
    val formattedName: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val phones: List<Pair<String, Set<String>>> = emptyList(),
    val emails: List<Pair<String, Set<String>>> = emptyList(),
    val addresses: List<Pair<String, Set<String>>> = emptyList(),
    val organization: String = "",
    val birthday: String = "",
    val note: String = "",
    val urls: List<String> = emptyList(),
) {
    /** Given and family name, falling back to splitting FN when N is missing. */
    fun names(): Pair<String, String> {
        if (givenName.isNotBlank() || familyName.isNotBlank()) return givenName to familyName
        val words = formattedName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when (words.size) {
            0 -> "" to ""
            1 -> words[0] to ""
            else -> words.dropLast(1).joinToString(" ") to words.last()
        }
    }
}

/** Reads vCard 2.1, 3.0 and 4.0 files. Unknown properties are ignored. */
object VCard {
    fun parse(text: String): List<VCardContact> {
        val contacts = mutableListOf<VCardContact>()
        var current: VCardContact? = null
        for (line in unfold(text)) {
            val colon = findColon(line)
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val rawValue = line.substring(colon + 1)
            val parts = head.split(';')
            val name = parts[0].substringAfterLast('.').uppercase() // strip "item1." group prefixes
            val params = parseParams(parts.drop(1))
            val value = decode(rawValue, params)
            when (name) {
                "BEGIN" -> {
                    if (value.equals("VCARD", ignoreCase = true)) current = VCardContact()
                }

                "END" -> {
                    if (value.equals("VCARD", ignoreCase = true)) {
                        current?.let { contacts += it }
                        current = null
                    }
                }

                else -> {
                    current = current?.let { apply(it, name, value, types(params)) }
                }
            }
        }
        return contacts
    }

    private fun apply(
        c: VCardContact,
        name: String,
        value: String,
        types: Set<String>,
    ): VCardContact =
        when (name) {
            "FN" -> {
                c.copy(formattedName = unescape(value))
            }

            "N" -> {
                val f = splitStructured(value)
                c.copy(
                    familyName =
                        f.getOrElse(0) {
                            ""
                        },
                    givenName = listOf(f.getOrElse(1) { "" }, f.getOrElse(2) { "" }).filter { it.isNotBlank() }.joinToString(" "),
                )
            }

            "TEL" -> {
                c.copy(phones = c.phones + (unescape(value).removePrefix("tel:") to types))
            }

            "EMAIL" -> {
                c.copy(emails = c.emails + (unescape(value).removePrefix("mailto:") to types))
            }

            "ADR" -> {
                // PO box; extended; street; locality; region; postal code; country
                val f = splitStructured(value)
                val street =
                    listOf(
                        f.getOrElse(0) { "" },
                        f.getOrElse(1) { "" },
                        f.getOrElse(2) { "" },
                    ).filter { it.isNotBlank() }.joinToString(", ")
                val cityLine =
                    listOf(
                        f.getOrElse(3) { "" },
                        f.getOrElse(4) { "" },
                        f.getOrElse(5) { "" },
                    ).filter { it.isNotBlank() }.joinToString(" ")
                val formatted = listOf(street, cityLine, f.getOrElse(6) { "" }).filter { it.isNotBlank() }.joinToString("\n")
                if (formatted.isBlank()) c else c.copy(addresses = c.addresses + (formatted to types))
            }

            "ORG" -> {
                c.copy(organization = splitStructured(value).filter { it.isNotBlank() }.joinToString(", "))
            }

            "BDAY" -> {
                c.copy(birthday = normalizeDate(unescape(value)))
            }

            "NOTE" -> {
                c.copy(note = unescape(value))
            }

            "URL" -> {
                c.copy(urls = c.urls + unescape(value))
            }

            else -> {
                c
            }
        }

    /** Joins folded lines: a line starting with a space or tab continues the previous one. Also handles QP soft breaks. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else if (out.isNotEmpty() && out.last().endsWith("=") && out.last().contains("QUOTED-PRINTABLE", ignoreCase = true)) {
                out[out.lastIndex] = out.last().dropLast(1) + raw
            } else {
                out += raw
            }
        }
        return out
    }

    /** The colon that ends the property name, skipping colons inside quoted parameter values. */
    private fun findColon(line: String): Int {
        var quoted = false
        line.forEachIndexed { i, ch ->
            if (ch == '"') quoted = !quoted
            if (ch == ':' && !quoted) return i
        }
        return -1
    }

    private fun parseParams(parts: List<String>): Map<String, List<String>> {
        val params = mutableMapOf<String, MutableList<String>>()
        for (p in parts) {
            val eq = p.indexOf('=')
            if (eq < 0) {
                // vCard 2.1 bare types ("TEL;CELL;HOME:") and bare encodings.
                val key = if (p.equals("QUOTED-PRINTABLE", true) || p.equals("BASE64", true)) "ENCODING" else "TYPE"
                params.getOrPut(key) { mutableListOf() } += p
            } else {
                val key = p.substring(0, eq).uppercase()
                val values = p.substring(eq + 1).trim('"').split(',')
                params.getOrPut(key) { mutableListOf() } += values
            }
        }
        return params
    }

    private fun types(params: Map<String, List<String>>): Set<String> = params["TYPE"].orEmpty().map { it.uppercase() }.toSet()

    private fun decode(
        value: String,
        params: Map<String, List<String>>,
    ): String {
        if (params["ENCODING"].orEmpty().none { it.equals("QUOTED-PRINTABLE", true) }) return value
        val charset = params["CHARSET"]?.firstOrNull() ?: "UTF-8"
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '=' && i + 2 <= value.lastIndex) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.write(hex)
                    i += 3
                    continue
                }
            }
            bytes.write(ch.code)
            i++
        }
        return runCatching { String(bytes.toByteArray(), charset(charset)) }.getOrDefault(value)
    }

    /** Splits on unescaped ';' and unescapes each part. */
    private fun splitStructured(value: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                sb.append(ch).append(value[i + 1])
                i += 2
                continue
            }
            if (ch == ';') {
                parts += unescape(sb.toString())
                sb.clear()
            } else {
                sb.append(ch)
            }
            i++
        }
        parts += unescape(sb.toString())
        return parts.map { it.trim() }
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    /** "19900423", "1990-04-23", "--0423" and "--04-23" become "1990-04-23" or "--04-23". */
    internal fun normalizeDate(value: String): String {
        val v = value.substringBefore('T').trim()
        Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})$").find(v)?.let { m -> return "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}" }
        Regex("^--(\\d{2})-?(\\d{2})$").find(v)?.let { m -> return "--${m.groupValues[1]}-${m.groupValues[2]}" }
        return v
    }
}
