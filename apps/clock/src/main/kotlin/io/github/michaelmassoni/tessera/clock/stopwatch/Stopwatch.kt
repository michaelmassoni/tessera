package io.github.michaelmassoni.tessera.clock.stopwatch

import kotlinx.serialization.Serializable

/** Stopwatch on the elapsed-realtime clock. Pure; "now" is passed in. */
@Serializable
data class Stopwatch(
    val running: Boolean = false,
    /** elapsedRealtime when the current run started. */
    val startElapsed: Long? = null,
    /** Time from previous runs. */
    val accumulated: Long = 0,
    /** Total elapsed time at each lap, oldest first. */
    val lapTotals: List<Long> = emptyList(),
) {
    val isReset: Boolean get() = !running && accumulated == 0L && lapTotals.isEmpty()

    fun elapsed(now: Long): Long = accumulated + if (running) now - requireNotNull(startElapsed) else 0

    fun start(now: Long): Stopwatch = if (running) this else copy(running = true, startElapsed = now)

    fun pause(now: Long): Stopwatch = if (!running) this else copy(running = false, startElapsed = null, accumulated = elapsed(now))

    fun reset(): Stopwatch = Stopwatch()

    fun lap(now: Long): Stopwatch = if (!running) this else copy(lapTotals = lapTotals + elapsed(now))

    /** Individual lap durations, oldest first. */
    val laps: List<Long> get() = lapTotals.mapIndexed { i, total -> total - (if (i == 0) 0 else lapTotals[i - 1]) }

    /** Index of the fastest and slowest lap, once there are at least two. */
    val fastestLap: Int? get() = if (laps.size < 2) null else laps.indices.minBy { laps[it] }
    val slowestLap: Int? get() = if (laps.size < 2) null else laps.indices.maxBy { laps[it] }
}
