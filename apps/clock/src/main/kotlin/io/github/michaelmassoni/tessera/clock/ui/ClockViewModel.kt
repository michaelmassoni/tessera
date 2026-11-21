package io.github.michaelmassoni.tessera.clock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.clock.alarm.Alarm
import io.github.michaelmassoni.tessera.clock.alarm.TimeUntil
import io.github.michaelmassoni.tessera.clock.data.AppGraph
import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.clock.stopwatch.Stopwatch
import io.github.michaelmassoni.tessera.clock.timer.ClockTimer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-off messages for the snackbar. */
sealed interface ClockEvent {
    data class AlarmSet(
        val until: TimeUntil,
    ) : ClockEvent

    data class AlarmDeleted(
        val alarm: Alarm,
    ) : ClockEvent

    data class CityRemoved(
        val zoneId: String,
        val index: Int,
    ) : ClockEvent
}

/**
 * Thin layer between the tabs and the controllers in [AppGraph]. All writes go through the
 * controllers (off the main thread), which keep storage and AlarmManager in step.
 */
class ClockViewModel(
    private val graph: AppGraph,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val alarms: StateFlow<List<Alarm>> = graph.alarms.alarms
    val timers: StateFlow<List<ClockTimer>> = graph.timers.timers
    val stopwatch: StateFlow<Stopwatch> = graph.stopwatch.stopwatch
    val cities: StateFlow<List<String>> = graph.cities.state
    val time = graph.time

    private val eventChannel = Channel<ClockEvent>(Channel.BUFFERED)
    val events: Flow<ClockEvent> = eventChannel.receiveAsFlow()

    init {
        // Cheap and idempotent; also covers data restored from a backup, which Android
        // restores without telling the app.
        background {
            graph.alarms.rescheduleAll()
            graph.timers.rescheduleRunning()
        }
    }

    private fun background(block: () -> Unit) {
        viewModelScope.launch(io) { block() }
    }

    // ------------------------------------------------------------------ alarms

    fun addAlarm(
        hour: Int,
        minute: Int,
    ) = background {
        val alarm = graph.alarms.add(hour, minute)
        announce(alarm)
    }

    fun updateAlarm(alarm: Alarm) =
        background {
            val before = graph.alarms.get(alarm.id)
            graph.alarms.update(alarm)
            // Say when it will ring if it was just switched on or its time changed.
            val worthAnnouncing =
                before == null ||
                    !before.enabled ||
                    before.hour != alarm.hour ||
                    before.minute != alarm.minute ||
                    before.days != alarm.days
            if (alarm.enabled && worthAnnouncing) announce(alarm)
        }

    fun deleteAlarm(alarm: Alarm) =
        background {
            graph.alarms.delete(alarm.id)
            eventChannel.trySend(ClockEvent.AlarmDeleted(alarm))
        }

    fun restoreAlarm(alarm: Alarm) = background { graph.alarms.restore(alarm) }

    /** Cancels a pending snooze ("Dismiss" on a snoozed alarm). */
    fun cancelSnooze(alarm: Alarm) = background { graph.alarms.dismiss(alarm.id) }

    fun skipAlarm(
        alarm: Alarm,
        skip: Boolean,
    ) = background { graph.alarms.skipNext(alarm.id, skip) }

    private fun announce(alarm: Alarm) {
        val trigger = graph.alarms.nextTrigger(graph.alarms.get(alarm.id) ?: return) ?: return
        eventChannel.trySend(ClockEvent.AlarmSet(TimeUntil.between(time.now(), trigger)))
    }

    // ------------------------------------------------------------------ timers

    fun createTimer(durationMillis: Long) = background { graph.timers.create(durationMillis) }

    fun startTimer(id: Int) = background { graph.timers.start(id) }

    fun pauseTimer(id: Int) = background { graph.timers.pause(id) }

    fun resetTimer(id: Int) = background { graph.timers.reset(id) }

    fun addMinute(id: Int) = background { graph.timers.addMinute(id) }

    fun setTimerLabel(
        id: Int,
        label: String,
    ) = background { graph.timers.setLabel(id, label) }

    fun deleteTimer(id: Int) = background { graph.timers.delete(id) }

    // ------------------------------------------------------------------ stopwatch

    fun startStopwatch() = background { graph.stopwatch.start() }

    fun pauseStopwatch() = background { graph.stopwatch.pause() }

    fun lap() = background { graph.stopwatch.lap() }

    fun resetStopwatch() = background { graph.stopwatch.reset() }

    // ------------------------------------------------------------------ world clock

    fun addCity(zoneId: String) = background { graph.cities.update { if (zoneId in it) it else it + zoneId } }

    fun removeCity(zoneId: String) =
        background {
            val index = graph.cities.value.indexOf(zoneId)
            graph.cities.update { it - zoneId }
            if (index >= 0) eventChannel.trySend(ClockEvent.CityRemoved(zoneId, index))
        }

    fun restoreCity(
        zoneId: String,
        index: Int,
    ) = background {
        graph.cities.update { list ->
            if (zoneId in
                list
            ) {
                list
            } else {
                list.toMutableList().apply { add(index.coerceIn(0, size), zoneId) }
            }
        }
    }
}
