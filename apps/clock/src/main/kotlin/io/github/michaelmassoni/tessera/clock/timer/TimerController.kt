package io.github.michaelmassoni.tessera.clock.timer

import io.github.michaelmassoni.tessera.clock.data.JsonStore
import io.github.michaelmassoni.tessera.clock.data.TimeSource

interface TimerSchedule {
    fun scheduleTimer(
        id: Int,
        endElapsed: Long,
    )

    fun cancelTimer(id: Int)
}

/** Listens for timer changes so the ongoing countdown notification stays current. */
fun interface TimersChangedListener {
    fun onTimersChanged(timers: List<ClockTimer>)
}

/** The single place timer state changes; mirrors [io.github.michaelmassoni.tessera.clock.alarm.AlarmController]. */
class TimerController(
    private val store: JsonStore<List<ClockTimer>>,
    private val schedule: TimerSchedule,
    private val time: TimeSource,
    private val listener: TimersChangedListener = TimersChangedListener { },
) {
    val timers get() = store.state

    fun get(id: Int): ClockTimer? = store.value.firstOrNull { it.id == id }

    /** Creates a timer and starts it straight away, as the Pixel clock does. */
    fun create(
        durationMillis: Long,
        label: String = "",
    ): ClockTimer {
        var created: ClockTimer? = null
        store.update { list ->
            val timer =
                ClockTimer(id = (list.maxOfOrNull { it.id } ?: 0) + 1, durationMillis = durationMillis, label = label)
                    .start(time.elapsedRealtime(), time.currentTimeMillis())
            created = timer
            list + timer
        }
        return requireNotNull(created).also { sync(it) }
    }

    fun start(id: Int) = change(id) { it.start(time.elapsedRealtime(), time.currentTimeMillis()) }

    fun pause(id: Int) = change(id) { it.pause(time.elapsedRealtime()) }

    fun reset(id: Int) = change(id) { it.reset() }

    fun setLabel(
        id: Int,
        label: String,
    ) = change(id) { it.copy(label = label.trim()) }

    fun addMinute(id: Int) = change(id) { it.addMinute(time.elapsedRealtime(), time.currentTimeMillis()) }

    fun expire(id: Int): ClockTimer? = change(id) { it.expire() }

    fun delete(id: Int) {
        store.update { list -> list.filterNot { it.id == id } }
        schedule.cancelTimer(id)
        listener.onTimersChanged(store.value)
    }

    /** After a reboot: rebuild running timers from the wall clock. */
    fun restoreAfterBoot() {
        store.update { list -> list.map { it.restoreAfterBoot(time.elapsedRealtime(), time.currentTimeMillis()) } }
        store.value.forEach(::sync)
        listener.onTimersChanged(store.value)
    }

    /** Re-registers running timers with AlarmManager (after an app update or permission change). */
    fun rescheduleRunning() {
        store.value.filter { it.state == ClockTimer.State.Running }.forEach(::sync)
    }

    /** After the wall clock was changed: keep elapsed deadlines, refresh the wall-clock copies. */
    fun onWallClockChanged() {
        store.update { list ->
            list.map { t ->
                val end = t.endElapsed
                if (end != null) t.copy(endWall = time.currentTimeMillis() + (end - time.elapsedRealtime())) else t
            }
        }
    }

    private fun change(
        id: Int,
        transform: (ClockTimer) -> ClockTimer,
    ): ClockTimer? {
        var changed: ClockTimer? = null
        store.update { list ->
            list.map {
                if (it.id == id) transform(it).also { t -> changed = t } else it
            }
        }
        changed?.let(::sync)
        return changed
    }

    private fun sync(timer: ClockTimer) {
        val end = timer.endElapsed
        if (timer.state == ClockTimer.State.Running && end != null) {
            schedule.scheduleTimer(timer.id, end)
        } else {
            schedule.cancelTimer(timer.id)
        }
        listener.onTimersChanged(store.value)
    }
}
