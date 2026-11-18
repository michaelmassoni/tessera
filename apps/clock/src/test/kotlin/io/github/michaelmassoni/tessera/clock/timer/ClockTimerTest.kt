package io.github.michaelmassoni.tessera.clock.timer

import io.github.michaelmassoni.tessera.clock.timer.ClockTimer.State
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTimerTest {
    private val fiveMin = ClockTimer(id = 1, durationMillis = 300_000)

    @Test fun `start pause resume`() {
        val running = fiveMin.start(elapsedNow = 1_000, wallNow = 50_000)
        assertEquals(State.Running, running.state)
        assertEquals(301_000L, running.endElapsed)
        assertEquals(350_000L, running.endWall)
        assertEquals(240_000L, running.remaining(61_000))

        val paused = running.pause(61_000)
        assertEquals(State.Paused, paused.state)
        assertEquals(240_000L, paused.remaining(999_999)) // time doesn't pass while paused

        val resumed = paused.start(elapsedNow = 100_000, wallNow = 200_000)
        assertEquals(340_000L, resumed.endElapsed)
    }

    @Test fun `expired timer counts past zero and restarts in full`() {
        val expired = fiveMin.start(0, 0).expire()
        assertEquals(State.Expired, expired.state)
        assertEquals(-5_000L, expired.remaining(305_000))
        assertEquals(300_000L, expired.start(400_000, 400_000).remaining(400_000))
    }

    @Test fun `add a minute in every state`() {
        val running = fiveMin.start(0, 0).addMinute(10_000, 10_000)
        assertEquals(360_000L, running.endElapsed)
        assertEquals(360_000L, running.durationMillis)
        assertEquals(360_000L, fiveMin.addMinute(0, 0).remainingMillis)
        val restarted = fiveMin.start(0, 0).expire().addMinute(500_000, 900_000)
        assertEquals(State.Running, restarted.state)
        assertEquals(60_000L, restarted.remaining(500_000))
    }

    @Test fun `reset restores the full duration`() {
        assertEquals(
            300_000L,
            fiveMin
                .start(0, 0)
                .pause(100_000)
                .reset()
                .remaining(0),
        )
    }

    @Test fun `progress goes from one to zero`() {
        val running = fiveMin.start(0, 0)
        assertEquals(1f, running.progress(0), 0f)
        assertEquals(0.5f, running.progress(150_000), 0.0001f)
        assertEquals(0f, running.progress(400_000), 0f)
    }

    @Test fun `restore after reboot uses the wall clock`() {
        val running = fiveMin.start(elapsedNow = 10_000_000, wallNow = 1_000_000)
        // Rebooted: elapsed restarted near zero, two minutes of wall time passed.
        val restored = running.restoreAfterBoot(elapsedNow = 5_000, wallNow = 1_120_000)
        assertEquals(State.Running, restored.state)
        assertEquals(180_000L, restored.remaining(5_000))
        // Deadline passed while off.
        val missed = running.restoreAfterBoot(elapsedNow = 5_000, wallNow = 2_000_000)
        assertEquals(State.Expired, missed.state)
    }

    @Test fun `keypad digits shift in from the right`() {
        var input = TimerInput()
        "130".forEach { input = input.type(it) }
        assertEquals("000130", input.padded)
        assertEquals(90_000L, input.durationMillis)
        assertEquals(TimerInput(), TimerInput().type('0')) // leading zeros are ignored
        "9999999".forEach { input = TimerInput(input.digits).type(it) }
        var full = TimerInput()
        "999999".forEach { full = full.type(it) }
        assertEquals((99 * 3600L + 99 * 60 + 99) * 1000, full.durationMillis)
        assertEquals("99999", full.backspace().digits)
        assertEquals("100", TimerInput("1").typeDoubleZero().digits)
    }
}
