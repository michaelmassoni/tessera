package io.github.michaelmassoni.tessera.recorder.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.michaelmassoni.tessera.recorder.MainActivity
import io.github.michaelmassoni.tessera.recorder.engine.Durations
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState

/**
 * Quick Settings "Record": opens the app recording (a tile may not start a microphone service
 * itself), or stops the recording that's running.
 */
class RecordTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val state = RecordingService.state.value
        tile.state = if (state is RecorderState.Active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle =
            when (state) {
                is RecorderState.Active -> Durations.clock(state.elapsedMs)
                else -> null
            }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (RecordingService.state.value is RecorderState.Active) {
            RecordingService.send(this, RecordingService.ACTION_STOP)
            return
        }
        val intent =
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_RECORD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startOnOlderAndroid(intent)
        }
    }

    // Android 12 and 13 have only the Intent version; the PendingIntent one is 14+.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startOnOlderAndroid(intent: Intent) = startActivityAndCollapse(intent)

    companion object {
        /** Asks the system to redraw the tile (after recording starts or stops). */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, RecordTile::class.java)) }
        }
    }
}
