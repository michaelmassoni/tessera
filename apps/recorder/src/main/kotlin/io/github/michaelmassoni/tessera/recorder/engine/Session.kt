package io.github.michaelmassoni.tessera.recorder.engine

/** What the recorder is doing, as the screens and the notification show it. */
sealed interface RecorderState {
    data object Idle : RecorderState

    data class Active(
        val elapsedMs: Long,
        val paused: Boolean,
        /** Another app (a call) has the microphone, so this stretch records silence. */
        val silenced: Boolean = false,
    ) : RecorderState

    /** Writing the file after Stop. */
    data object Saving : RecorderState
}

/**
 * The timing of one recording: elapsed time counts only while recording, across any number of
 * pauses. Pure, with the clock passed in ([now] in ms, monotonic), so it's unit tested.
 */
class Session(
    now: Long,
) {
    private var runningSince: Long? = now
    private var banked = 0L

    val paused: Boolean get() = runningSince == null

    fun elapsed(now: Long): Long = banked + (runningSince?.let { now - it } ?: 0)

    /** False if it was already paused. */
    fun pause(now: Long): Boolean {
        val since = runningSince ?: return false
        banked += now - since
        runningSince = null
        return true
    }

    /** False if it wasn't paused. */
    fun resume(now: Long): Boolean {
        if (runningSince != null) return false
        runningSince = now
        return true
    }

    fun state(
        now: Long,
        silenced: Boolean = false,
    ): RecorderState.Active = RecorderState.Active(elapsed(now), paused, silenced && !paused)
}
