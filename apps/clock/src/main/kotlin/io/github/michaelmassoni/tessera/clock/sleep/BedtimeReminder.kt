package io.github.michaelmassoni.tessera.clock.sleep

import io.github.michaelmassoni.tessera.clock.data.JsonStore
import io.github.michaelmassoni.tessera.clock.data.TimeSource
import kotlinx.serialization.Serializable

/** "Time for bed" at [atMillis], to wake at [wakeMillis] if it's known. */
@Serializable
data class BedtimeReminder(
    val atMillis: Long,
    val wakeMillis: Long? = null,
)

/** Where the reminder goes to be scheduled. Implemented with AlarmManager; faked in tests. */
interface BedtimeSchedule {
    fun scheduleBedtime(atMillis: Long)

    fun cancelBedtime()
}

/**
 * The one bedtime reminder (docs/clock.md, "Sleep calculator"). Kept on disk so it survives a
 * reboot; a new one replaces it; one already past is dropped.
 */
class BedtimeController(
    private val store: JsonStore<BedtimeReminder?>,
    private val schedule: BedtimeSchedule,
    private val time: TimeSource,
) {
    val reminder get() = store.state

    fun set(
        atMillis: Long,
        wakeMillis: Long? = null,
    ) {
        store.update { BedtimeReminder(atMillis, wakeMillis) }
        schedule.scheduleBedtime(atMillis)
    }

    fun cancel() {
        store.update { null }
        schedule.cancelBedtime()
    }

    /** It went off: returns it (to show) and forgets it. Null if it was cancelled meanwhile. */
    fun onFired(): BedtimeReminder? {
        val fired = store.value ?: return null
        store.update { null }
        return fired
    }

    /** After a reboot or a clock change: back into AlarmManager, or dropped if it's past. */
    fun reschedule() {
        val r = store.value ?: return
        if (r.atMillis <= time.currentTimeMillis()) cancel() else schedule.scheduleBedtime(r.atMillis)
    }
}
