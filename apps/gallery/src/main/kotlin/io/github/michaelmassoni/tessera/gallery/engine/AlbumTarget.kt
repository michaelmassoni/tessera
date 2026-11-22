package io.github.michaelmassoni.tessera.gallery.engine

/**
 * Where moved or copied media goes. An album is a folder; MediaStore only lets each kind of media
 * live under certain top-level folders, so a new album is made under Pictures (which takes photos
 * and videos alike).
 */
object AlbumTarget {
    private val INVALID = Regex("[\\\\/:*?\"<>|\\u0000]")

    /** "Pictures/Trip/" for a new album called "Trip"; null for an empty or unusable name. */
    fun newAlbum(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." || INVALID.containsMatchIn(clean)) return null
        return "Pictures/$clean/"
    }

    /** Whether MediaStore accepts [item] in [relativePath] ("DCIM/Camera/", "Pictures/…", …). */
    fun allowed(
        item: MediaItem,
        relativePath: String,
    ): Boolean {
        val top = relativePath.trimStart('/').substringBefore('/')
        return when {
            top.equals("Download", ignoreCase = true) -> true
            item.isVideo -> top in setOf("DCIM", "Movies", "Pictures")
            else -> top in setOf("DCIM", "Pictures")
        }
    }

    /** Items that are already in [relativePath] don't need moving. */
    fun toMove(
        items: List<MediaItem>,
        relativePath: String,
    ) = items.filter { !it.relativePath.equals(relativePath, ignoreCase = true) }

    /** The EXIF orientation after turning a photo 90° clockwise from [orientation] (1, 6, 3, 8). */
    fun rotateClockwise(orientation: Int): Int =
        when (orientation) {
            6 -> 3
            3 -> 8
            8 -> 1
            else -> 6
        }

    /** Formats whose orientation can be changed in place, without re-encoding. */
    fun canRotate(item: MediaItem) = !item.isVideo && item.mimeType in setOf("image/jpeg", "image/webp", "image/png")
}
