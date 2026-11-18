package io.github.michaelmassoni.tessera.clock.ui

import kotlin.math.abs

/** Clock-style durations: "1:02:03", "2:03", "0:09"; negative values get a leading "−". */
object DurationFormat {
    fun timer(millis: Long): String {
        // Round up while counting down so "0:01" shows until the very end, like the Pixel.
        val totalSeconds = if (millis >= 0) (millis + 999) / 1000 else -(abs(millis) / 1000)
        val sign = if (totalSeconds < 0) "−" else ""
        val s = abs(totalSeconds)
        return sign + hms(s)
    }

    fun stopwatch(millis: Long): String {
        val s = millis / 1000
        val hundredths = (millis % 1000) / 10
        return hms(s) + "." + hundredths.toString().padStart(2, '0')
    }

    private fun hms(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds / 60) % 60
        val sec = totalSeconds % 60
        val ss = sec.toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
    }
}
