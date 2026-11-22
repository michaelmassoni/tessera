package io.github.michaelmassoni.tessera.recorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import io.github.michaelmassoni.tessera.recorder.engine.Waveform

data class RecorderUi(
    val recordings: List<Recording> = emptyList(),
    val loaded: Boolean = false,
    val state: RecorderState = RecorderState.Idle,
    val live: FloatArray = FloatArray(0),
)

/** What the screens ask for. */
interface RecorderActions {
    fun record()

    fun pause()

    fun resume()

    fun stop()

    fun discard()

    fun rename(
        recording: Recording,
        title: String,
    )

    /** Moves to the trash; the list offers Undo. */
    fun delete(recordings: List<Recording>)

    fun share(recordings: List<Recording>)

    fun openSettings()

    fun waveform(recording: Recording): Waveform?
}

/** The player's side of playback, so previews can stand in for MediaPlayer. */
data class Playback(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
)

interface PlaybackControls {
    fun toggle()

    fun seekTo(ms: Long)

    fun skip(byMs: Long)

    fun nextSpeed()
}

/**
 * The app: the list, the Recording screen while recording, and the player. [showRecording] is
 * set when the notification is tapped; [player] gives the playback for an open recording.
 */
@Composable
fun RecorderApp(
    ui: RecorderUi,
    actions: RecorderActions,
    snackbar: SnackbarHostState,
    showRecording: Int,
    player: @Composable (Recording, @Composable (Playback, PlaybackControls) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordingOpen by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val active = ui.state is RecorderState.Active
    // Asked for before the service has started (the tile, a shortcut): it shows once recording is on.
    LaunchedEffect(showRecording) { if (showRecording > 0) recordingOpen = true }
    // Stopped (here, from the notification or the tile): back to the list.
    LaunchedEffect(active) { if (!active) recordingOpen = false }
    val open = ui.recordings.firstOrNull { it.id == openId }
    LaunchedEffect(open, ui.loaded) { if (ui.loaded && open == null) openId = null }

    when {
        recordingOpen && ui.state is RecorderState.Active -> {
            BackHandler { recordingOpen = false }
            RecordingScreen(ui.state, ui.live, actions, onBack = { recordingOpen = false }, modifier = modifier)
        }

        open != null -> {
            BackHandler { openId = null }
            player(open) { playback, controls ->
                PlayerScreen(open, actions.waveform(open), playback, controls, actions, onBack = { openId = null }, modifier = modifier)
            }
        }

        else -> {
            ListScreen(
                ui,
                actions,
                snackbar,
                onOpen = { openId = it.id },
                onShowRecording = { recordingOpen = true },
                onRecord = {
                    actions.record()
                    recordingOpen = true
                },
                modifier = modifier,
            )
        }
    }
}
