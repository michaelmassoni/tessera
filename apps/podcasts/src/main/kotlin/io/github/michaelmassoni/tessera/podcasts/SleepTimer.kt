package io.github.michaelmassoni.tessera.podcasts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The sleep timer: set from the player screen, carried out by [PlaybackService] (same process). */
object SleepTimer {
    sealed interface Sleep {
        data object Off : Sleep

        /** Pause at [endsAt] (elapsed realtime, ms). */
        data class At(
            val endsAt: Long,
        ) : Sleep

        data object EndOfEpisode : Sleep
    }

    private val _state = MutableStateFlow<Sleep>(Sleep.Off)
    val state: StateFlow<Sleep> = _state.asStateFlow()

    fun set(sleep: Sleep) {
        _state.value = sleep
    }
}
