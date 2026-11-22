package io.github.michaelmassoni.tessera.recorder.engine

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Titles and file names of recordings. */
object Names {
    /** Characters no file system on a phone, SD card or computer accepts in a name. */
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    /** "Recording 23 Nov 10:42", in the person's language ([word] is "Recording" translated). */
    fun defaultTitle(
        word: String,
        at: LocalDateTime,
        locale: Locale,
        is24Hour: Boolean,
    ): String {
        val date = DateTimeFormatter.ofPattern("d MMM", locale).format(at)
        val time = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(at)
        return "$word $date $time"
    }

    /** What the person typed, tidied for use as a title: trimmed, one line, not blank. */
    fun cleanTitle(
        typed: String,
        fallback: String,
    ): String = typed.replace(Regex("\\s+"), " ").trim().ifEmpty { fallback }

    /**
     * The file name for [title] (".m4a" added), avoiding the names in [taken]: "Interview.m4a",
     * then "Interview (2).m4a" and so on. Characters a file system refuses become "-".
     */
    fun fileName(
        title: String,
        taken: Set<String>,
        extension: String = "m4a",
    ): String {
        val base =
            title
                // A time's colon becomes a dot ("10.42"), which reads the same; the rest a dash.
                .replace(':', '.')
                .replace(forbidden, "-")
                .trim()
                .trimEnd('.')
                .take(MAX_BASE)
                .ifEmpty { "Recording" }
        val lower = taken.map { it.lowercase() }.toSet()
        var name = "$base.$extension"
        var n = 2
        while (name.lowercase() in lower) name = "$base (${n++}).$extension"
        return name
    }

    private const val MAX_BASE = 120
}

/** Durations as a clock: "0:07", "3:12", "1:04:09". */
object Durations {
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, sec) else "%d:%02d".format(Locale.ROOT, m, sec)
    }

    /** "3:12.4", with tenths, for the large timer while recording. */
    fun clockTenths(ms: Long): String = clock(ms) + "." + ((ms.coerceAtLeast(0) % 1000) / 100)
}
