package io.github.michaelmassoni.tessera.clock.alarm

import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.clock.data.JsonStore
import io.github.michaelmassoni.tessera.clock.data.TimeSource
import java.time.ZonedDateTime

/** Where alarms go to be scheduled. Implemented with AlarmManager; faked in tests. */
interface AlarmSchedule {
    fun scheduleAlarm(
        id: Int,
        triggerAtMillis: Long,
    )

    fun cancelAlarm(id: Int)
}

/**
 * The single place alarm state changes. Every mutation is persisted and then the system
 * schedule is brought in line, so the stored alarms and AlarmManager can't drift apart.
 * Used by the UI, the receivers and the ringing service alike.
 */
class AlarmController(
    private val store: JsonStore<List<Alarm>>,
    private val settings: JsonStore<ClockSettings>,
    private val schedule: AlarmSchedule,
    private val time: TimeSource,
) {
    val alarms get() = store.state

    fun get(id: Int): Alarm? = store.value.firstOrNull { it.id == id }

    fun add(
        hour: Int,
        minute: Int,
        label: String = "",
        days: Set<java.time.DayOfWeek> = emptySet(),
        vibrate: Boolean = true,
    ): Alarm {
        var created: Alarm? = null
        store.update { list ->
            val alarm =
                Alarm(
                    id =
                        (
                            list.maxOfOrNull {
                                it.id
                            } ?: 0
                        ) + 1,
                    hour = hour,
                    minute = minute,
                    label = label,
                    days = days,
                    vibrate = vibrate,
                )
            created = alarm
            list + alarm
        }
        return requireNotNull(created).also(::reschedule)
    }

    /** Saves an edited alarm. Changing time or days clears any pending snooze. */
    fun update(alarm: Alarm) {
        val old = get(alarm.id)
        val cleaned =
            if (old != null &&
                (old.hour != alarm.hour || old.minute != alarm.minute || old.days != alarm.days || !alarm.enabled)
            ) {
                alarm.copy(snoozedUntil = null, skipped = null)
            } else {
                alarm
            }
        store.update { list -> list.map { if (it.id == alarm.id) cleaned else it } }
        reschedule(cleaned)
    }

    fun delete(id: Int) {
        store.update { list -> list.filterNot { it.id == id } }
        schedule.cancelAlarm(id)
    }

    /** Re-inserts a deleted alarm (undo). */
    fun restore(alarm: Alarm) {
        store.update { list -> (list.filterNot { it.id == alarm.id } + alarm).sortedBy { it.id } }
        reschedule(alarm)
    }

    /** The alarm went off: one-off alarms turn off, repeating ones move to their next day. */
    fun onFired(id: Int): Alarm? {
        val alarm = get(id) ?: return null
        val after = AlarmTimes.afterRinging(alarm)
        store.update { list -> list.map { if (it.id == id) after else it } }
        reschedule(after)
        return alarm
    }

    fun snooze(id: Int) {
        val alarm = get(id) ?: return
        val until = time.currentTimeMillis() + settings.value.snoozeMinutes * 60_000L
        val snoozed = alarm.copy(enabled = true, snoozedUntil = until)
        store.update { list -> list.map { if (it.id == id) snoozed else it } }
        reschedule(snoozed)
    }

    /**
     * Skips the next time a repeating alarm would ring (or brings it back). A one-off alarm has
     * nothing after its next time, so skipping it turns it off.
     */
    fun skipNext(
        id: Int,
        skip: Boolean = true,
    ) {
        val alarm = get(id) ?: return
        val changed =
            when {
                !skip -> {
                    alarm.copy(skipped = null)
                }

                !alarm.repeats -> {
                    alarm.copy(enabled = false, snoozedUntil = null)
                }

                else -> {
                    alarm.copy(
                        snoozedUntil = null,
                        skipped = AlarmTimes.nextOccurrence(alarm.copy(skipped = null), time.now()).toInstant().toEpochMilli(),
                    )
                }
            }
        store.update { list -> list.map { if (it.id == id) changed else it } }
        reschedule(changed)
    }

    /** "Dismiss" on the upcoming-alarm notification: a snoozed alarm is dismissed, otherwise its next time is skipped. */
    fun dismissUpcoming(id: Int) {
        val alarm = get(id) ?: return
        if (alarm.snoozedUntil != null) dismiss(id) else skipNext(id)
    }

    /** Dismissing a ringing or snoozed alarm. */
    fun dismiss(id: Int) {
        val alarm = get(id) ?: return
        if (alarm.snoozedUntil == null) return // already handled when it fired
        val after = AlarmTimes.afterRinging(alarm)
        store.update { list -> list.map { if (it.id == id) after else it } }
        reschedule(after)
    }

    fun nextTrigger(alarm: Alarm): ZonedDateTime? = AlarmTimes.nextTrigger(alarm, time.now())

    /** After boot, time or zone changes, app updates and permission changes. */
    fun rescheduleAll() {
        // Drop snoozes that expired while we weren't running (e.g. the phone was off).
        val nowMillis = time.currentTimeMillis()
        store.update { list ->
            list.map { a ->
                val snooze = a.snoozedUntil
                if (snooze != null && snooze <= nowMillis) AlarmTimes.afterRinging(a) else a
            }
        }
        store.value.forEach(::reschedule)
    }

    private fun reschedule(alarm: Alarm) {
        val trigger = AlarmTimes.nextTrigger(alarm, time.now())
        if (trigger == null) {
            schedule.cancelAlarm(alarm.id)
        } else {
            schedule.scheduleAlarm(alarm.id, trigger.toInstant().toEpochMilli())
        }
    }
}
