package io.github.michaelmassoni.tessera.files.engine

import java.text.Collator
import java.util.Locale

enum class SortField { Name, Modified, Size, Type }

data class SortOrder(
    val field: SortField = SortField.Name,
    val ascending: Boolean = true,
)

/** Folders always come first; ties fall back to name. Names use natural order ("file2" < "file10"). */
object Sorting {
    fun sort(
        entries: List<FileEntry>,
        order: SortOrder,
        locale: Locale = Locale.getDefault(),
    ): List<FileEntry> {
        val names = NaturalComparator(locale)
        val byName = Comparator<FileEntry> { a, b -> names.compare(a.name, b.name) }
        val primary: Comparator<FileEntry> =
            when (order.field) {
                SortField.Name -> byName
                SortField.Modified -> compareBy<FileEntry> { it.lastModified }.then(byName)
                SortField.Size -> compareBy<FileEntry> { if (it.isDirectory) (it.childCount ?: 0).toLong() else it.size }.then(byName)
                SortField.Type -> compareBy<FileEntry> { it.name.substringAfterLast('.', "").lowercase() }.then(byName)
            }
        val directed = if (order.ascending) primary else primary.reversed()
        return entries.sortedWith(compareBy<FileEntry> { !it.isDirectory }.then(directed))
    }
}

/** Compares runs of digits by value and everything else with a locale-aware collator. */
class NaturalComparator(
    locale: Locale,
) : Comparator<String> {
    private val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

    override fun compare(
        a: String,
        b: String,
    ): Int {
        val ta = tokens(a)
        val tb = tokens(b)
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val c =
                if (x[0].isDigit() && y[0].isDigit()) {
                    val nx = x.trimStart('0')
                    val ny = y.trimStart('0')
                    if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny).takeIf { it != 0 } ?: (x.length - y.length)
                } else {
                    collator.compare(x, y)
                }
            if (c != 0) return c
        }
        return (ta.size - tb.size).takeIf { it != 0 } ?: collator.compare(a, b)
    }

    private fun tokens(s: String): List<String> =
        Regex("""\d+|\D+""")
            .findAll(s)
            .map { it.value }
            .toList()
            .ifEmpty { listOf("") }
}
