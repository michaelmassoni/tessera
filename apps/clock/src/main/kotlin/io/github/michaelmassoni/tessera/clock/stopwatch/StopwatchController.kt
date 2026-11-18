package io.github.michaelmassoni.tessera.clock.stopwatch

import io.github.michaelmassoni.tessera.clock.data.JsonStore
import io.github.michaelmassoni.tessera.clock.data.TimeSource

class StopwatchController(
    private val store: JsonStore<Stopwatch>,
    private val time: TimeSource,
    private val onChanged: (Stopwatch) -> Unit = {},
) {
    val stopwatch get() = store.state

    fun start() = change { it.start(time.elapsedRealtime()) }

    fun pause() = change { it.pause(time.elapsedRealtime()) }

    fun lap() = change { it.lap(time.elapsedRealtime()) }

    fun reset() = change { it.reset() }

    private fun change(transform: (Stopwatch) -> Stopwatch) = onChanged(store.update(transform))
}
