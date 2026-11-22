package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable

/** One line of lyrics, with when it's sung for synced lyrics. */
@Serializable
data class LyricLine(
    val timeMs: Long? = null,
    val text: String,
)

/** Where lyrics came from, said under them. */
enum class LyricsSource { Server, LrcLib }

@Serializable
data class Lyrics(
    val lines: List<LyricLine>,
    val source: LyricsSource,
) {
    /** Every line has a time, so the lyrics can follow the song. */
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }

    /**
     * The line being sung at [positionMs]: the last one that has started, or -1 before the first
     * (and always -1 for unsynced lyrics).
     */
    fun current(positionMs: Long): Int {
        if (!synced) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if ((lines[mid].timeMs ?: 0) <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/** Reading lyrics files. Pure, so it's unit tested. */
object LyricsParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val TAG = Regex("""^\[([a-zA-Z#]+):(.*)]\s*$""")

    /**
     * LRC: `[mm:ss.xx]text` lines, several times on one line for a repeated line, `[offset:ms]`
     * (positive is earlier), and tags like `[ar:...]` ignored. Text without any times becomes
     * unsynced lyrics. Null when there's nothing to show.
     */
    fun parse(
        text: String,
        source: LyricsSource,
    ): Lyrics? {
        var offset = 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val times = TIME.findAll(line).toList()
            if (times.isNotEmpty() && line.startsWith("[")) {
                // Times are at the start; the text follows the last one.
                var end = 0
                val starts = mutableListOf<Long>()
                for (m in times) {
                    if (m.range.first != end) break
                    end = m.range.last + 1
                    starts += millis(m)
                }
                val words = line.substring(end).trim()
                starts.forEach { timed += LyricLine(it, words) }
            } else {
                val tag = TAG.matchEntire(line)
                if (tag != null) {
                    if (tag.groupValues[1].equals("offset", ignoreCase = true)) offset = tag.groupValues[2].trim().toLongOrNull() ?: 0
                } else {
                    plain += line
                }
            }
        }
        if (timed.isNotEmpty()) {
            val lines = timed.map { it.copy(timeMs = ((it.timeMs ?: 0) - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs }
            return Lyrics(trimBlankEnds(lines), source)
        }
        val lines = plain.map { LyricLine(null, it.trim()) }
        return trimBlankEnds(lines).takeIf { l -> l.any { it.text.isNotBlank() } }?.let { Lyrics(it, source) }
    }

    private fun millis(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val fraction =
            when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.toLong()
            }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + fraction
    }

    private fun trimBlankEnds(lines: List<LyricLine>): List<LyricLine> =
        lines.dropWhile { it.text.isBlank() && it.timeMs == null }.dropLastWhile { it.text.isBlank() && it.timeMs == null }
}
