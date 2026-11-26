package io.github.michaelmassoni.tessera.gallery.engine

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Gallery search (docs/gallery.md, Search): every word of the query must match something about
 * an item: its file name, its album, when it was taken (the month's name or short name, the year,
 * the day of the month), or "video" / "photo". Ignores case and accents. No on-device AI. Pure,
 * so it's unit tested.
 */
class MediaSearch(
    private val zone: ZoneId,
    private val locale: Locale,
    /** The words for photos and videos in the person's language ("photo", "video"). */
    private val photoWords: Set<String> = setOf("photo", "photos", "picture", "pictures", "image", "images"),
    private val videoWords: Set<String> = setOf("video", "videos"),
) {
    fun search(
        items: List<MediaItem>,
        query: String,
    ): List<MediaItem> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return items.filter { item -> words.all { word -> matches(item, word) } }
    }

    private fun matches(
        item: MediaItem,
        word: String,
    ): Boolean {
        if (fold(item.name).contains(word) || fold(item.bucketName).contains(word)) return true
        if (word in videoWords) return item.isVideo
        if (word in photoWords) return !item.isVideo
        val date = Instant.ofEpochMilli(item.date).atZone(zone)
        val month = date.month
        if (fold(month.getDisplayName(TextStyle.FULL, locale)).startsWith(word) && word.length >= 3) return true
        if (fold(month.getDisplayName(TextStyle.FULL_STANDALONE, locale)).startsWith(word) && word.length >= 3) return true
        return word.toIntOrNull()?.let { n -> n == date.year || (word.length <= 2 && n == date.dayOfMonth) } == true
    }

    private fun fold(text: String) =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(locale)
}
