package io.github.michaelmassoni.tessera.gallery.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A day in the timeline. [label] is "Today", "Yesterday", a weekday this week, or a date. */
data class DaySection(
    val day: LocalDate,
    val label: String,
    val items: List<MediaItem>,
)

/** Labels for the two relative days, from string resources. */
data class RelativeLabels(
    val today: String,
    val yesterday: String,
)

object Timeline {
    /**
     * Groups [items] (any order) into days, newest first. Items within a day follow [MediaOrder].
     * Days in the last week are labelled by weekday; days this year omit the year.
     */
    fun sections(
        items: List<MediaItem>,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale,
        labels: RelativeLabels,
    ): List<DaySection> =
        items
            .sortedWith(MediaOrder)
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .map { (day, list) -> DaySection(day, label(day, today, locale, labels), list) }
            .sortedByDescending { it.day }

    fun label(
        day: LocalDate,
        today: LocalDate,
        locale: Locale,
        labels: RelativeLabels,
    ): String {
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo == 0L -> labels.today
            daysAgo == 1L -> labels.yesterday
            daysAgo in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            day.year == today.year -> day.format(DateTimeFormatter.ofPattern(bestPattern(locale, "EEEMMMd"), locale))
            else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }

    /** Overridable so tests don't need Android's best-pattern generator. */
    var bestPattern: (Locale, String) -> String = { _, _ -> "EEE, MMM d" }
}

object Albums {
    /** Folders that sort first when they exist, by relative path prefix. */
    private val pinned = listOf("DCIM/Camera", "Pictures/Screenshots", "DCIM/Screenshots")

    /** One album per bucket: Camera and Screenshots first, then by most recent item. */
    fun fromItems(items: List<MediaItem>): List<Album> =
        items
            .groupBy { it.bucketId }
            .map { (id, list) ->
                val newest = list.maxBy { it.date }
                Album(id, newest.bucketName, list.size, newest, newest.date)
            }.sortedWith(
                compareBy<Album> { album ->
                    val path = album.cover.relativePath.trimEnd('/')
                    pinned.indexOfFirst { path.equals(it, ignoreCase = true) }.let { if (it < 0) Int.MAX_VALUE else it }
                }.thenByDescending { it.latest },
            )
}

object TrashInfo {
    /** Whole days until [expiresMillis] (rounded up), never negative. */
    fun daysLeft(
        expiresMillis: Long,
        nowMillis: Long,
    ): Int {
        val ms = expiresMillis - nowMillis
        if (ms <= 0) return 0
        return ((ms + DAY - 1) / DAY).toInt()
    }

    private const val DAY = 24L * 60 * 60 * 1000
}

/** "0:42", "12:05", "1:02:03". */
fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
