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
)

/** A folder of media. */
data class Album(
    val id: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
    val latest: Long,
)
