package io.github.michaelmassoni.tessera.clock.data

import android.content.Context
import io.github.michaelmassoni.tessera.clock.alarm.Alarm
import io.github.michaelmassoni.tessera.clock.alarm.AlarmController
import io.github.michaelmassoni.tessera.clock.ringing.ClockNotifications
import io.github.michaelmassoni.tessera.clock.ringing.Ringing
import io.github.michaelmassoni.tessera.clock.sleep.BedtimeController
import io.github.michaelmassoni.tessera.clock.sleep.BedtimeReminder
import io.github.michaelmassoni.tessera.clock.stopwatch.Stopwatch
import io.github.michaelmassoni.tessera.clock.stopwatch.StopwatchController
import io.github.michaelmassoni.tessera.clock.stopwatch.StopwatchTile
import io.github.michaelmassoni.tessera.clock.timer.ClockTimer
import io.github.michaelmassoni.tessera.clock.timer.TimerController
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import java.io.File

/**
 * Hand-written dependency graph, one per process. All state lives in **device-protected
 * storage**, so alarms can be read and rescheduled before the user first unlocks after a reboot.
 */
class AppGraph(
    context: Context,
    val time: TimeSource = SystemTimeSource,
) {
    private val app = context.applicationContext
    private val storage: File = app.createDeviceProtectedStorageContext().filesDir

    val notifications = ClockNotifications(app)
    val scheduler = AndroidScheduler(app)

    val settings = JsonStore(File(storage, "settings.json"), ClockSettings.serializer(), ClockSettings())

    val alarms =
        AlarmController(
            store = JsonStore(File(storage, "alarms.json"), ListSerializer(Alarm.serializer()), emptyList()),
            settings = settings,
            schedule = scheduler,
            time = time,
        )

    val timers =
        TimerController(
            store = JsonStore(File(storage, "timers.json"), ListSerializer(ClockTimer.serializer()), emptyList()),
            schedule = scheduler,
            time = time,
            listener = { notifications.updateTimers(it, time.elapsedRealtime()) },
        )

    val stopwatch =
        StopwatchController(
            store = JsonStore(File(storage, "stopwatch.json"), Stopwatch.serializer(), Stopwatch()),
            time = time,
            onChanged = {
                notifications.updateStopwatch(it, time.elapsedRealtime(), time.currentTimeMillis())
                StopwatchTile.refresh(context)
            },
        )

    /** What is ringing now, if anything. */
    val ringing =
        JsonStore<Ringing?>(
            File(storage, "ringing.json"),
            Ringing.serializer().nullable,
            null,
        )

    /** The bedtime reminder from the sleep calculator, if one is set. */
    val bedtime =
        BedtimeController(
            store = JsonStore(File(storage, "bedtime.json"), BedtimeReminder.serializer().nullable, null),
            schedule = scheduler,
            time = time,
        )

    /** IANA zone ids of the cities on the world clock tab, in display order. */
    val cities = JsonStore(File(storage, "cities.json"), ListSerializer(String.serializer()), emptyList())
}
