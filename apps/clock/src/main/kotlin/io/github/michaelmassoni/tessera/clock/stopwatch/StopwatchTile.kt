package io.github.michaelmassoni.tessera.clock.stopwatch

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.graph
import io.github.michaelmassoni.tessera.clock.ui.DurationFormat
import kotlin.concurrent.thread

/** Quick Settings "Stopwatch": starts and pauses it without opening the app. */
class StopwatchTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        show()
    }

    override fun onClick() {
        super.onClick()
        val stopwatch = graph.stopwatch
        // The stopwatch is saved to a file: not on the main thread.
        thread(name = "stopwatch-tile") {
            if (stopwatch.stopwatch.value.running) stopwatch.pause() else stopwatch.start()
            show()
        }
    }

    private fun show() {
        val tile = qsTile ?: return
        val now = graph.time.elapsedRealtime()
        val s = graph.stopwatch.stopwatch.value
        tile.state = if (s.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle =
            when {
                s.running -> getString(R.string.tile_running)
                s.isReset -> null
                else -> DurationFormat.stopwatch(s.elapsed(now))
            }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile (the stopwatch changed in the app). */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, StopwatchTile::class.java)) }
        }
    }
}
