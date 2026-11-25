package io.github.michaelmassoni.tessera.clock.data

import kotlinx.serialization.Serializable

@Serializable
data class ClockSettings(
    val snoozeMinutes: Int = 10,
    /** 0 = never silence automatically. */
    val silenceAfterMinutes: Int = 10,
    /** Sleep calculator: one sleep cycle, and how long it takes to fall asleep. */
    val sleepCycleMinutes: Int = 90,
    val fallAsleepMinutes: Int = 15,
) {
    companion object {
        val SNOOZE_CHOICES = listOf(5, 10, 15, 20)
        val SILENCE_CHOICES = listOf(5, 10, 15, 0)
        val CYCLE_CHOICES = listOf(80, 90, 100)
        val FALL_ASLEEP_CHOICES = listOf(0, 5, 10, 15, 20, 30)
    }
}
