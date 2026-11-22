package io.github.michaelmassoni.tessera.recorder

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.data.Recordings
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import io.github.michaelmassoni.tessera.recorder.service.RecordingService
import io.github.michaelmassoni.tessera.recorder.ui.RecorderActions
import io.github.michaelmassoni.tessera.recorder.ui.RecorderApp
import io.github.michaelmassoni.tessera.recorder.ui.RecorderUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val recordings by lazy { Recordings(this) }
    private val snackbar = SnackbarHostState()
    private var list by mutableStateOf(emptyList<Recording>())
    private var loaded by mutableStateOf(false)

    /** Bumped to show the Recording screen (from the notification, the tile or a shortcut). */
    private var showRecording by mutableIntStateOf(0)
    private val waves = HashMap<Long, Waveform?>()

    private val askMicrophone =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startRecording() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingService.ensureChannel(this)
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch {
            recordings.changes().conflate().collect {
                list = withContext(Dispatchers.IO) { recordings.list() }
                loaded = true
            }
        }
        lifecycleScope.launch {
            // Left pending when the app died mid-recording: keep what plays.
            if (RecordingService.state.value == RecorderState.Idle) {
                val lost = withContext(Dispatchers.IO) { recordings.recoverPending(RecordingService.recordingUri) }
                if (lost > 0) snackbar.showSnackbar(getString(R.string.lost_recording))
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RecordingService.saved.collect { saved ->
                    snackbar.showSnackbar(getString(saved.message ?: R.string.saved))
                }
            }
        }
        val actions = Actions()
        setTesseraContent {
            SetupGate(recorderSetup) {
                val state by RecordingService.state.collectAsStateWithLifecycle()
                val live by RecordingService.live.collectAsStateWithLifecycle()
                RecorderApp(
                    RecorderUi(list, loaded, state, live),
                    actions,
                    snackbar,
                    showRecording,
                    player = { recording, content -> MediaPlayerHost(recording, content) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when {
            intent?.action == ACTION_RECORD -> {
                if (RecordingService.state.value == RecorderState.Idle) startRecording()
                showRecording++
            }

            intent?.getBooleanExtra(EXTRA_SHOW_RECORDING, false) == true -> {
                showRecording++
            }
        }
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        RecordingService.send(this, RecordingService.ACTION_START)
        showRecording++
    }

    private inner class Actions : RecorderActions {
        override fun record() = startRecording()

        override fun pause() = RecordingService.send(this@MainActivity, RecordingService.ACTION_PAUSE)

        override fun resume() = RecordingService.send(this@MainActivity, RecordingService.ACTION_RESUME)

        override fun stop() = RecordingService.send(this@MainActivity, RecordingService.ACTION_STOP)

        override fun discard() = RecordingService.send(this@MainActivity, RecordingService.ACTION_DISCARD)

        override fun rename(
            recording: Recording,
            title: String,
        ) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { recordings.rename(recording, title) }
                if (!ok) snackbar.showSnackbar(getString(R.string.rename_failed))
            }
        }

        override fun delete(recordings: List<Recording>) {
            lifecycleScope.launch {
                val trashed = withContext(Dispatchers.IO) { recordings.filter { this@MainActivity.recordings.trash(it.uri) } }
                if (trashed.isEmpty()) {
                    snackbar.showSnackbar(getString(R.string.delete_failed))
                    return@launch
                }
                val text = resources.getQuantityString(R.plurals.deleted, trashed.size, trashed.size)
                if (snackbar.showSnackbar(text, getString(R.string.undo)) == SnackbarResult.ActionPerformed) {
                    withContext(Dispatchers.IO) { trashed.forEach { this@MainActivity.recordings.untrash(it.uri) } }
                }
            }
        }

        override fun share(recordings: List<Recording>) {
            if (recordings.isEmpty()) return
            val uris = ArrayList(recordings.map { it.uri })
            val send =
                if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            send.type = Recordings.MIME
            send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, null))
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun waveform(recording: Recording): Waveform? = waves.getOrPut(recording.id) { recordings.waveform(recording.id) }
    }

    companion object {
        const val EXTRA_SHOW_RECORDING = "show_recording"

        /** From the Quick Settings tile and the app shortcut: start recording. */
        const val ACTION_RECORD = "io.github.michaelmassoni.tessera.recorder.RECORD"
    }
}
