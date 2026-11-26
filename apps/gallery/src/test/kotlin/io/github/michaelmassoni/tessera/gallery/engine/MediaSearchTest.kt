package io.github.michaelmassoni.tessera.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class MediaSearchTest {
    private val search = MediaSearch(ZoneOffset.UTC, Locale.ENGLISH)

    private fun item(
        id: Long,
        name: String,
        album: String,
        date: LocalDate,
        video: Boolean = false,
    ) = MediaItem(
        id = id,
        uri = "content://media/$id",
        isVideo = video,
        mimeType = if (video) "video/mp4" else "image/jpeg",
        name = name,
        date = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        bucketId = album.hashCode().toLong(),
        bucketName = album,
    )

    private val items =
        listOf(
            item(1, "IMG_20261124_0930.jpg", "Camera", LocalDate.of(2026, 11, 24)),
            item(2, "Screenshot_Café.png", "Screenshots", LocalDate.of(2026, 3, 2)),
            item(3, "VID_beach.mp4", "Camera", LocalDate.of(2025, 11, 2), video = true),
        )

    private fun ids(query: String) = search.search(items, query).map { it.id }

    @Test
    fun `names and albums, ignoring case and accents`() {
        assertEquals(listOf(2L), ids("cafe"))
        assertEquals(listOf(2L), ids("SCREENSHOTS"))
        assertEquals(listOf(1L, 3L), ids("camera"))
    }

    @Test
    fun `months, years and days`() {
        assertEquals(listOf(1L, 3L), ids("november"))
        assertEquals(listOf(1L, 3L), ids("nov"))
        assertEquals(listOf(3L), ids("2025"))
        assertEquals(listOf(1L), ids("24 november"))
        // Two letters aren't enough for a month ("ma" would match March and May).
        assertEquals(emptyList<Long>(), ids("ma"))
    }

    @Test
    fun `photos and videos, and every word must match`() {
        assertEquals(listOf(3L), ids("video"))
        assertEquals(listOf(1L), ids("photos november"))
        assertEquals(listOf(3L), ids("camera 2025"))
        assertEquals(emptyList<Long>(), ids(" "))
    }
}
