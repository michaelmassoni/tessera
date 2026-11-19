package io.github.michaelmassoni.tessera.gallery.engine

/** One photo or video from MediaStore. Times are epoch milliseconds. */
data class MediaItem(
    val id: Long,
    /** content:// URI as a string, so this class stays free of Android types. */
    val uri: String,
    val isVideo: Boolean,
    val mimeType: String,
    val name: String,
    /** When it was taken, or added if that's unknown. */
    val date: Long,
    val bucketId: Long,
    val bucketName: String,
    val size: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val durationMillis: Long = 0,
    val favorite: Boolean = false,
    val relativePath: String = "",
    /** For trashed items: when the system will delete it. */
    val expires: Long = 0,
    /** When the file last changed: images are cached per version, so an edited photo reloads. */
    val modified: Long = 0,
)

/** A folder of media. */
data class Album(
    val id: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
    val latest: Long,
)

/**
 * The one order media is shown in, everywhere: newest first, ties broken by id. The grid and the
 * viewer both use it, so swiping in the viewer walks the grid exactly. (MediaStore can't sort this
 * way itself: [MediaItem.date] falls back to the date added when there's no date taken.)
 */
val MediaOrder: Comparator<MediaItem> = compareByDescending<MediaItem> { it.date }.thenByDescending { it.id }
