package io.github.michaelmassoni.tessera.clock.system

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.michaelmassoni.tessera.clock.alarm.AlarmTimes
import io.github.michaelmassoni.tessera.clock.graph
import io.github.michaelmassoni.tessera.clock.ringing.ClockReceiver
import io.github.michaelmassoni.tessera.clock.ringing.RingingService
import io.github.michaelmassoni.tessera.clock.ringing.SystemEventReceiver
import io.github.michaelmassoni.tessera.clock.timer.ClockTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.DayOfWeek

/**
 * The path from "user sets an alarm" to "AlarmManager will wake us", and from "AlarmManager woke
 * us" to "the ringing service starts". These are the parts that must never regress.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class AlarmDeliveryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val shadowAlarms get() = shadowOf(alarmManager)
    private val graph get() = app.graph

    @Before fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun scheduledRtc() = shadowAlarms.scheduledAlarms.filter { it.getType() == AlarmManager.RTC_WAKEUP }

    @Test fun `new alarm is scheduled as an alarm clock at its next trigger`() {
        val alarm = graph.alarms.add(7, 30)
        val expected = AlarmTimes.nextTrigger(alarm, graph.time.now())!!.toInstant().toEpochMilli()
        val scheduled = scheduledRtc().single()
        assertEquals(expected, scheduled.triggerAtMs)
        assertNotNull("must use setAlarmClock (Doze-exempt, shown to user)", scheduled.alarmClockInfo)
        assertEquals(expected, scheduled.alarmClockInfo!!.triggerTime)
    }

    @Test fun `disabling or deleting cancels the schedule`() {
        val a = graph.alarms.add(7, 30)
        val b = graph.alarms.add(8, 0)
        assertEquals(2, scheduledRtc().size)
        graph.alarms.update(a.copy(enabled = false))
        assertEquals(1, scheduledRtc().size)
        graph.alarms.delete(b.id)
        assertTrue(scheduledRtc().isEmpty())
    }

    @Test fun `boot reschedules every enabled alarm, before first unlock`() {
        graph.alarms.add(6, 0)
        graph.alarms.add(7, 0).let { graph.alarms.update(it.copy(enabled = false)) }
        graph.alarms.add(9, 15, days = setOf(DayOfWeek.SATURDAY))
        // Simulate the reboot wiping AlarmManager.
        wipeAlarmManager()
        assertTrue(scheduledRtc().isEmpty())

        SystemEventReceiver().onReceive(app, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(2, scheduledRtc().size)
        assertTrue(scheduledRtc().all { it.alarmClockInfo != null })
    }

    @Test fun `time zone change reschedules`() {
        graph.alarms.add(6, 0)
        wipeAlarmManager()
        SystemEventReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(1, scheduledRtc().size)
    }

    @Test fun `firing a one-off alarm starts ringing and turns it off`() {
        val alarm = graph.alarms.add(7, 30)
        ClockReceiver().onReceive(app, fired(alarm.id))
        val service = shadowOf(app).nextStartedService
        assertEquals(RingingService.ACTION_RING_ALARM, service.action)
        assertEquals(alarm.id, service.getIntExtra(ClockReceiver.EXTRA_ID, -1))
        assertFalse(graph.alarms.get(alarm.id)!!.enabled)
        assertTrue(scheduledRtc().isEmpty())
    }

    @Test fun `firing a repeating alarm keeps it on and schedules the next one`() {
        val alarm = graph.alarms.add(7, 30, days = DayOfWeek.entries.toSet())
        val first = scheduledRtc().single().triggerAtMs
        ClockReceiver().onReceive(app, fired(alarm.id))
        assertTrue(graph.alarms.get(alarm.id)!!.enabled)
        assertEquals(1, scheduledRtc().size)
        assertTrue(scheduledRtc().single().triggerAtMs >= first)
    }

    @Test fun `firing an alarm that was deleted does nothing`() {
        ClockReceiver().onReceive(app, fired(42))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test fun `other apps cannot spoof an alarm firing through the exported receiver`() {
        val alarm = graph.alarms.add(7, 30)
        SystemEventReceiver().onReceive(app, fired(alarm.id))
        assertNull(shadowOf(app).nextStartedService)
        assertTrue(graph.alarms.get(alarm.id)!!.enabled)
    }

    @Test fun `snooze schedules the alarm for the snooze length from now`() {
        val alarm = graph.alarms.add(7, 30)
        ClockReceiver().onReceive(app, fired(alarm.id))
        val before = System.currentTimeMillis()
        graph.alarms.snooze(alarm.id)
        val trigger = scheduledRtc().single().triggerAtMs
        val tenMinutes = 10 * 60_000L
        assertTrue(trigger in (before + tenMinutes)..(System.currentTimeMillis() + tenMinutes))
        // Dismissing the snooze turns a one-off alarm back off.
        graph.alarms.dismiss(alarm.id)
        assertTrue(scheduledRtc().isEmpty())
    }

    @Test fun `revoked exact alarm permission still schedules something`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        graph.alarms.add(7, 30)
        assertEquals(1, scheduledRtc().size)
    }

    @Test fun `timers are scheduled on the elapsed clock and ring when they expire`() {
        val timer = graph.timers.create(60_000)
        val scheduled = shadowAlarms.scheduledAlarms.single { it.getType() == AlarmManager.ELAPSED_REALTIME_WAKEUP }
        assertEquals(timer.endElapsed, scheduled.triggerAtMs)

        ClockReceiver().onReceive(
            app,
            Intent(
                app,
                ClockReceiver::class.java,
            ).setAction(ClockReceiver.ACTION_TIMER_EXPIRED).putExtra(ClockReceiver.EXTRA_ID, timer.id),
        )
        assertEquals(ClockTimer.State.Expired, graph.timers.get(timer.id)!!.state)
        assertEquals(RingingService.ACTION_RING_TIMER, shadowOf(app).nextStartedService.action)

        graph.timers.pause(timer.id) // expired timers can't be paused
        graph.timers.reset(timer.id)
        assertTrue(shadowAlarms.scheduledAlarms.none { it.getType() == AlarmManager.ELAPSED_REALTIME_WAKEUP })
    }

    @Test fun `app update reschedules running timers but does not restart expired ones`() {
        val running = graph.timers.create(60_000)
        val expired = graph.timers.create(1_000).also { graph.timers.expire(it.id) }
        wipeAlarmManager()
        SystemEventReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(
            running.endElapsed,
            shadowAlarms.scheduledAlarms
                .single {
                    it.getType() ==
                        AlarmManager.ELAPSED_REALTIME_WAKEUP
                }.triggerAtMs,
        )
        assertEquals(ClockTimer.State.Expired, graph.timers.get(expired.id)!!.state)
    }

    /** What a reboot does to AlarmManager. */
    private fun wipeAlarmManager() {
        graph.alarms.alarms.value
            .forEach { graph.scheduler.cancelAlarm(it.id) }
        graph.timers.timers.value
            .forEach { graph.scheduler.cancelTimer(it.id) }
    }

    private fun fired(id: Int) =
        Intent(
            app,
            ClockReceiver::class.java,
        ).setAction(ClockReceiver.ACTION_ALARM_FIRED).putExtra(ClockReceiver.EXTRA_ID, id)
}
