package io.github.michaelmassoni.tessera.tasks.engine

/** One content line: `NAME;PARAM=value:VALUE`. */
data class Property(
    val name: String,
    val params: List<Pair<String, String>> = emptyList(),
    val value: String,
) {
    fun param(key: String) = params.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second
}

/** A component (VCALENDAR, VTODO, VALARM …) with its properties and sub-components, in order. */
class Component(
    val name: String,
    val properties: MutableList<Property> = mutableListOf(),
    val children: MutableList<Component> = mutableListOf(),
) {
    fun first(name: String) = properties.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun all(name: String) = properties.filter { it.name.equals(name, ignoreCase = true) }

    fun remove(name: String) = properties.removeAll { it.name.equals(name, ignoreCase = true) }

    /** Replaces every [name] property with [property] where the first was, or adds it at the end. */
    fun set(property: Property) {
        val at = properties.indexOfFirst { it.name.equals(property.name, ignoreCase = true) }
        remove(property.name)
        if (at >= 0) properties.add(at.coerceAtMost(properties.size), property) else properties.add(property)
    }
}

/**
 * iCalendar (RFC 5545) text: unfolding, parsing into [Component]s, and writing back with folding,
 * keeping everything it doesn't understand. Pure, so it's unit tested.
 */
object ICal {
    fun parse(text: String): Component? {
        val stack = ArrayDeque<Component>()
        var root: Component? = null
        for (line in unfold(text)) {
            if (line.isBlank()) continue
            val property = parseLine(line) ?: continue
            when (property.name.uppercase()) {
                "BEGIN" -> {
                    val c = Component(property.value.uppercase())
                    stack.lastOrNull()?.children?.add(c)
                    stack.addLast(c)
                }

                "END" -> {
                    val c = stack.removeLastOrNull() ?: return null
                    if (stack.isEmpty()) root = c
                }

                else -> {
                    stack.lastOrNull()?.properties?.add(property)
                }
            }
        }
        return root
    }

    fun write(component: Component): String = buildString { write(component, this) }

    private fun write(
        c: Component,
        out: StringBuilder,
    ) {
        out.append("BEGIN:").append(c.name).append("\r\n")
        c.properties.forEach { p -> out.append(fold(formatLine(p))).append("\r\n") }
        c.children.forEach { write(it, out) }
        out.append("END:").append(c.name).append("\r\n")
    }

    /** Joins continuation lines (starting with a space or tab) onto the line before. */
    fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + line.substring(1)
            } else {
                lines += line
            }
        }
        return lines
    }

    /** Splits lines longer than 75 octets, as the RFC asks, without cutting a UTF-8 character. */
    fun fold(line: String): String {
        if (line.toByteArray().size <= 75) return line
        val out = StringBuilder()
        var count = 0
        var first = true
        for (ch in line) {
            val size = ch.toString().toByteArray().size
            if (count + size > (if (first) 75 else 74)) {
                out.append("\r\n ")
                count = 0
                first = false
            }
            out.append(ch)
            count += size
        }
        return out.toString()
    }

    private fun parseLine(line: String): Property? {
        // The name and parameters end at the first ':' outside quotes.
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> {
                    quoted = !quoted
                }

                ':' -> {
                    if (!quoted) {
                        colon = i
                        break
                    }
                }
            }
        }
        if (colon <= 0) return null
        val head = splitOutsideQuotes(line.substring(0, colon), ';')
        val params =
            head.drop(1).mapNotNull { p ->
                val eq = p.indexOf('=')
                if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).removeSurrounding("\"")
            }
        return Property(head.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun splitOutsideQuotes(
        text: String,
        separator: Char,
    ): List<String> {
        val parts = mutableListOf<String>()
        var quoted = false
        val current = StringBuilder()
        for (ch in text) {
            when {
                ch == '"' -> {
                    quoted = !quoted
                    current.append(ch)
                }

                ch == separator && !quoted -> {
                    parts += current.toString()
                    current.clear()
                }

                else -> {
                    current.append(ch)
                }
            }
        }
        parts += current.toString()
        return parts
    }

    private fun formatLine(p: Property): String =
        buildString {
            append(p.name)
            p.params.forEach { (k, v) ->
                append(';').append(k).append('=')
                if (v.any { it == ':' || it == ';' || it == ',' }) append('"').append(v).append('"') else append(v)
            }
            append(':').append(p.value)
        }

    /** A TEXT value as written: backslash, semicolon, comma and newlines escaped. */
    fun escape(text: String) =
        text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")

    fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(text[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }
}
