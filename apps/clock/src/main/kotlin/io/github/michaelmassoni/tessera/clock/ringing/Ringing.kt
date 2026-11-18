package io.github.michaelmassoni.tessera.clock.ringing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What is ringing right now. Persisted so a ringing alarm survives the process being killed. */
@Serializable
sealed interface Ringing {
    val id: Int
    val label: String

    @Serializable
    @SerialName("alarm")
    data class Alarm(
        override val id: Int,
        override val label: String,
        val hour: Int,
        val minute: Int,
        val sound: String?,
        val vibrate: Boolean,
    ) : Ringing

    @Serializable
    @SerialName("timer")
    data class Timer(
        override val id: Int,
        override val label: String,
        val durationMillis: Long,
    ) : Ringing
}
