package io.github.michaelmassoni.tessera.clock.timer

import kotlinx.serialization.Serializable

/**
 * A countdown timer. Running timers store their deadline on the elapsed-realtime clock (immune to
 * wall-clock changes) and on the wall clock (survives a reboot, when elapsed time resets).
 * All functions are pure; "now" values are passed in.
 */
@Serializable
data class ClockTimer(
    val id: Int,
    val durationMillis: Long,
    val label: String = "",
    val state: State = State.Reset,
    /** Remaining time while paused or reset. */
    val remainingMillis: Long = durationMillis,
    /** Deadline on SystemClock.elapsedRealtime() while running or expired. */
    val endElapsed: Long? = null,
    /** Deadline in epoch millis while running or expired. */
    val endWall: Long? = null,
) {
    enum class State { Reset, Running, Paused, Expired }

    /** Remaining time; negative once expired (the display counts up past zero). */
    fun remaining(elapsedNow: Long): Long =
        when (state) {
            State.Running, State.Expired -> requireNotNull(endElapsed) - elapsedNow
            State.Paused, State.Reset -> remainingMillis
        }

    /** Fraction left, 1 → 0, for the progress ring. */
    fun progress(elapsedNow: Long): Float =
        if (durationMillis <= 0) 0f else (remaining(elapsedNow).toFloat() / durationMillis).coerceIn(0f, 1f)

    fun start(
        elapsedNow: Long,
        wallNow: Long,
    ): ClockTimer {
        if (state == State.Running) return this
        val left = if (state == State.Expired || remainingMillis <= 0) durationMillis else remainingMillis
        return copy(state = State.Running, endElapsed = elapsedNow + left, endWall = wallNow + left)
    }

    fun pause(elapsedNow: Long): ClockTimer {
        if (state != State.Running) return this
        return copy(
            state = State.Paused,
            remainingMillis = remaining(elapsedNow).coerceAtLeast(0),
            endElapsed = null,
            endWall = null,
        )
    }

    fun reset(): ClockTimer = copy(state = State.Reset, remainingMillis = durationMillis, endElapsed = null, endWall = null)

    fun expire(): ClockTimer = if (state == State.Running) copy(state = State.Expired) else this

    /** "+1:00": extends whatever is left; restarts an expired timer with one minute. */
    fun addMinute(
        elapsedNow: Long,
        wallNow: Long,
    ): ClockTimer =
        when (state) {
            State.Running -> {
                copy(
                    endElapsed = endElapsed!! + MINUTE,
                    endWall = endWall!! + MINUTE,
                    durationMillis =
                        durationMillis + MINUTE,
                )
            }

            State.Expired -> {
                copy(
                    state = State.Running,
                    endElapsed = elapsedNow + MINUTE,
                    endWall = wallNow + MINUTE,
                    durationMillis = MINUTE,
                )
            }

            State.Paused, State.Reset -> {
                copy(remainingMillis = remainingMillis + MINUTE, durationMillis = durationMillis + MINUTE)
            }
        }

    /**
     * After a reboot the elapsed clock restarts from zero, so running timers are rebuilt from
     * their wall-clock deadline. A timer whose deadline passed while the phone was off is expired.
     */
    fun restoreAfterBoot(
        elapsedNow: Long,
        wallNow: Long,
    ): ClockTimer {
        if (state != State.Running && state != State.Expired) return this
        val left = requireNotNull(endWall) - wallNow
        return copy(state = if (left > 0) State.Running else State.Expired, endElapsed = elapsedNow + left)
    }

    companion object {
        const val MINUTE = 60_000L
    }
}
