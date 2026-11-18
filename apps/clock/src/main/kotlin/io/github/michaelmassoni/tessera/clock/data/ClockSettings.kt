package io.github.michaelmassoni.tessera.clock.data

import kotlinx.serialization.Serializable

@Serializable
data class ClockSettings(
    val snoozeMinutes: Int = 10,
    /** 0 = never silence automatically. */
    val silenceAfterMinutes: Int = 10,
) {
    companion object {
        val SNOOZE_CHOICES = listOf(5, 10, 15, 20)
        val SILENCE_CHOICES = listOf(5, 10, 15, 0)
    }
}
