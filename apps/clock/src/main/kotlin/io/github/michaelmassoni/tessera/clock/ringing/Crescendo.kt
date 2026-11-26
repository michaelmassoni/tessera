package io.github.michaelmassoni.tessera.clock.ringing

/**
 * "Gradually increase volume": the alarm sound's volume over time, from quiet to full. Loudness is
 * heard roughly logarithmically, so the volume rises along a curve (cubed), which sounds like a
 * steady climb rather than a jump at the start. Pure, so it's unit tested.
 */
object Crescendo {
    /** The volume to start from: just audible, not silent. */
    const val START = 0.05f

    /** How often the volume is raised while ramping. */
    const val STEP_MS = 250L

    /** The player volume (0–1) [elapsedMs] into the alarm, over [durationMs] (0 = no ramp). */
    fun volume(
        elapsedMs: Long,
        durationMs: Long,
    ): Float {
        if (durationMs <= 0 || elapsedMs >= durationMs) return 1f
        val t = (elapsedMs.coerceAtLeast(0).toFloat() / durationMs)
        return START + (1f - START) * t * t * t
    }
}
