package io.github.michaelmassoni.tessera.clock.sleep

import io.github.michaelmassoni.tessera.clock.data.JsonStore
import io.github.michaelmassoni.tessera.clock.data.TimeSource
import kotlinx.serialization.builtins.nullable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

class BedtimeControllerTest {
    @get:Rule val folder = TemporaryFolder()

    private val scheduled = mutableListOf<Long?>()
    private val schedule =
        object : BedtimeSchedule {
            override fun scheduleBedtime(atMillis: Long) {
                scheduled += atMillis
            }

            override fun cancelBedtime() {
                scheduled += null
            }
        }
    private var nowMillis = 1_000_000L
    private val time =
        object : TimeSource {
            override fun elapsedRealtime() = 0L

            override fun currentTimeMillis() = nowMillis

            override fun zone(): ZoneId = ZoneId.of("UTC")
        }

    private fun store(file: File = File(folder.root, "bedtime.json")) = JsonStore(file, BedtimeReminder.serializer().nullable, null)

    @Test fun `a new reminder replaces the old one and is kept on disk`() {
        val file = File(folder.root, "bedtime.json")
        val c = BedtimeController(store(file), schedule, time)
        c.set(2_000_000, 9_000_000)
        c.set(3_000_000)
        assertEquals(listOf<Long?>(2_000_000, 3_000_000), scheduled)
        assertEquals(BedtimeReminder(3_000_000), store(file).value)
    }

    @Test fun `firing shows it once and forgets it`() {
        val c = BedtimeController(store(), schedule, time)
        c.set(2_000_000, 9_000_000)
        assertEquals(BedtimeReminder(2_000_000, 9_000_000), c.onFired())
        assertNull(c.onFired())
        assertNull(c.reminder.value)
    }

    @Test fun `after a reboot it is rescheduled, or dropped if past`() {
        val c = BedtimeController(store(), schedule, time)
        c.set(2_000_000)
        scheduled.clear()
        c.reschedule()
        assertEquals(listOf<Long?>(2_000_000), scheduled)
        nowMillis = 2_000_000
        scheduled.clear()
        c.reschedule()
        assertEquals(listOf<Long?>(null), scheduled)
        assertNull(c.reminder.value)
    }
}
