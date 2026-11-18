package io.github.michaelmassoni.tessera.clock.data

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** All clocks the app reads, behind one seam so tests can control time. */
interface TimeSource {
    fun elapsedRealtime(): Long

    fun currentTimeMillis(): Long

    fun zone(): ZoneId

    fun now(): ZonedDateTime = Instant.ofEpochMilli(currentTimeMillis()).atZone(zone())
}

object SystemTimeSource : TimeSource {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
