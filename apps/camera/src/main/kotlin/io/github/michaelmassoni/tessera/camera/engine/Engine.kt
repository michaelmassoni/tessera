package io.github.michaelmassoni.tessera.camera.engine

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class Mode { Photo, Video }

/** Photo flash; in video the same button is a torch on/off. */
enum class Flash {
    Off,
    Auto,
    On,
    ;

    fun next(video: Boolean): Flash =
        if (video) {
            if (this == Off) On else Off
        } else {
            entries[(ordinal + 1) % entries.size]
        }
}

/** Camera file names, like the system camera's. */
object FileNames {
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /**
     * "IMG_20261119_103015" (no extension). If [taken] already contains that name (two shots in
     * the same second), "_1", "_2", ... is appended.
     */
    fun name(
        mode: Mode,
        time: LocalDateTime,
        taken: Set<String> = emptySet(),
    ): String {
        val base = (if (mode == Mode.Photo) "IMG_" else "VID_") + time.format(STAMP)
        if (base !in taken) return base
        return generateSequence(1) { it + 1 }.map { "${base}_$it" }.first { it !in taken }
    }
}

object ZoomSteps {
    /**
     * Zoom chips for a lens that zooms from [min] to [max]: 0.5× when ultra-wide is reachable,
     * 1×, then 2×, 5× and 10× while within range.
     */
    fun steps(
        min: Float,
        max: Float,
    ): List<Float> = listOf(0.5f, 1f, 2f, 5f, 10f).filter { it in min..max || it == 1f }

    /** "1×", "0.5×", "2.5×". */
    fun label(ratio: Float): String {
        val rounded = Math.round(ratio * 10) / 10f
        val text = if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
        return "$text×"
    }
}

/** "0:07", "12:34", "1:02:03". */
fun formatElapsed(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
