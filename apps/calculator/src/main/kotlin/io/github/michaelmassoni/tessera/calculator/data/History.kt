package io.github.michaelmassoni.tessera.calculator.data

import java.io.File
import java.io.IOException

data class HistoryEntry(
    val expression: String,
    val result: String,
    val timestampMillis: Long,
)

/**
 * Calculation history stored as one tab-separated line per entry (newest first) in a private
 * file. Writes are atomic (temp file + rename) so a crash never leaves a half-written history.
 * Corrupt lines are skipped instead of failing the whole load.
 */
class HistoryStore(
    private val file: File,
) {
    fun load(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull(::parse).take(MAX_ENTRIES)
        } catch (e: IOException) {
            emptyList()
        }
    }

    fun save(entries: List<HistoryEntry>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(
            entries
                .take(
                    MAX_ENTRIES,
                ).joinToString(separator = "") { "${it.timestampMillis}\t${it.expression}\t${it.result}\n" },
        )
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun parse(line: String): HistoryEntry? {
        val parts = line.split('\t')
        if (parts.size != 3) return null
        val time = parts[0].toLongOrNull() ?: return null
        if (parts[1].isEmpty() || parts[2].isEmpty()) return null
        return HistoryEntry(expression = parts[1], result = parts[2], timestampMillis = time)
    }

    companion object {
        const val MAX_ENTRIES = 100

        /** Adds [entry] at the top, dropping the oldest entries beyond [MAX_ENTRIES]. */
        fun prepend(
            entries: List<HistoryEntry>,
            entry: HistoryEntry,
        ): List<HistoryEntry> = (listOf(entry) + entries).take(MAX_ENTRIES)
    }
}
