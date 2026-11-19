package io.github.michaelmassoni.tessera.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class TimelineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 19) // a Thursday
    private val labels = RelativeLabels("Today", "Yesterday")

    private fun item(
        id: Long,
        at: LocalDateTime,
        bucket: Long = 1,
        name: String = "Camera",
        path: String = "DCIM/Camera/",
    ) = MediaItem(
        id,
        "content://$id",
        false,
        "image/jpeg",
        "$id.jpg",
        at.toInstant(ZoneOffset.UTC).toEpochMilli(),
        bucket,
        name,
        relativePath = path,
    )

    @Test fun `days are grouped newest first with relative labels`() {
        val items =
            listOf(
                item(1, LocalDateTime.of(2026, 11, 19, 9, 0)),
                item(2, LocalDateTime.of(2026, 11, 19, 18, 0)),
                item(3, LocalDateTime.of(2026, 11, 18, 23, 59)),
                item(4, LocalDateTime.of(2026, 11, 16, 12, 0)),
                item(5, LocalDateTime.of(2026, 3, 2, 12, 0)),
                item(6, LocalDateTime.of(2024, 12, 25, 12, 0)),
            )
        val sections = Timeline.sections(items, today, zone, Locale.ENGLISH, labels)
        assertEquals(listOf("Today", "Yesterday", "Monday", "Mon, Mar 2", "Dec 25, 2024"), sections.map { it.label })
        assertEquals(listOf(2L, 1L), sections[0].items.map { it.id })
    }

    @Test fun `reading the sections in order gives the same order as the viewer`() {
        // Shuffled, with two items sharing a timestamp: the grid and the viewer must still agree,
        // or swiping in the viewer jumps around the timeline.
        val items =
            listOf(
                item(7, LocalDateTime.of(2026, 6, 3, 10, 0)),
                item(3, LocalDateTime.of(2026, 11, 16, 9, 0)),
                item(9, LocalDateTime.of(2026, 11, 16, 9, 0)),
                item(1, LocalDateTime.of(2026, 11, 16, 14, 0)),
                item(5, LocalDateTime.of(2026, 11, 15, 8, 0)),
            )
        val grid = Timeline.sections(items, today, zone, Locale.ENGLISH, labels).flatMap { it.items }.map { it.id }
        assertEquals(items.sortedWith(MediaOrder).map { it.id }, grid)
        assertEquals(listOf(1L, 9L, 3L, 5L, 7L), grid)
    }

    @Test fun `albums put camera and screenshots first, then most recent`() {
        val items =
            listOf(
                item(1, LocalDateTime.of(2026, 1, 1, 0, 0), bucket = 10, name = "Camera", path = "DCIM/Camera/"),
                item(2, LocalDateTime.of(2026, 11, 1, 0, 0), bucket = 20, name = "WhatsApp", path = "Pictures/WhatsApp/"),
                item(3, LocalDateTime.of(2026, 6, 1, 0, 0), bucket = 30, name = "Screenshots", path = "Pictures/Screenshots/"),
                item(4, LocalDateTime.of(2026, 10, 1, 0, 0), bucket = 40, name = "Download", path = "Download/"),
                item(5, LocalDateTime.of(2026, 2, 1, 0, 0), bucket = 10, name = "Camera", path = "DCIM/Camera/"),
            )
        val albums = Albums.fromItems(items)
        assertEquals(listOf("Camera", "Screenshots", "WhatsApp", "Download"), albums.map { it.name })
        assertEquals(2, albums[0].count)
        assertEquals(5L, albums[0].cover.id)
    }

    @Test fun `trash days round up and durations format`() {
        val day = 86_400_000L
        assertEquals(30, TrashInfo.daysLeft(30 * day, 0))
        assertEquals(1, TrashInfo.daysLeft(day / 2, 0))
        assertEquals(0, TrashInfo.daysLeft(5, 10))
        assertEquals("0:42", formatDuration(42_300))
        assertEquals("12:05", formatDuration(725_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }
}
