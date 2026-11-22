package io.github.michaelmassoni.tessera.recorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.michaelmassoni.tessera.recorder.MainActivity
import io.github.michaelmassoni.tessera.recorder.R
import io.github.michaelmassoni.tessera.recorder.data.Quality
import io.github.michaelmassoni.tessera.recorder.data.Recordings
import io.github.michaelmassoni.tessera.recorder.engine.Durations
import io.github.michaelmassoni.tessera.recorder.engine.Names
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import io.github.michaelmassoni.tessera.recorder.engine.Session
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import io.github.michaelmassoni.tessera.recorder.engine.WaveformBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Holds the microphone while recording, as a foreground service of type microphone, so recording
 * carries on with the screen off or in another app. Started only from the app's own activities
 * (Android 14+ refuses a microphone service started from the background).
 */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var file: ParcelFileDescriptor? = null
    private var session: Session? = null
    private var levels = WaveformBuilder()
    private var ticker: Job? = null
    private var silenced = false
    private var lastNotified = -1L
    private val recordings by lazy { Recordings(this) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    private val recordingCallback =
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val now = recorder?.activeRecordingConfiguration?.isClientSilenced ?: return
                if (now != silenced) {
                    silenced = now
                    publish(force = true)
                }
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop(save = true)
            ACTION_DISCARD -> stop(save = false)
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Killed while recording (rare for a foreground service): keep what was written. The next
        // start of the app finishes it (Recordings.recoverPending).
        if (recorder != null) releaseRecorder()
        audio.unregisterAudioRecordingCallback(recordingCallback)
        scope.cancel()
        if (_state.value != RecorderState.Saving) _state.value = RecorderState.Idle
        super.onDestroy()
    }

    private fun start() {
        if (recorder != null) return
        ensureChannel(this)
        startForeground(
            NOTIFICATION_ID,
            notification(RecorderState.Active(0, paused = false)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        val title =
            Names.defaultTitle(
                getString(R.string.recording_word),
                LocalDateTime.now(),
                Locale.getDefault(),
                DateFormat.is24HourFormat(this),
            )
        val target = recordings.create(title)
        if (target == null) {
            fail(R.string.error_storage)
            return
        }
        val quality = Quality.get(this)
        try {
            val fd = contentResolver.openFileDescriptor(target, "rw") ?: error("no file")
            val r =
                MediaRecorder(this).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(quality.channels)
                    setAudioSamplingRate(quality.sampleRate)
                    setAudioEncodingBitRate(quality.bitRate)
                    setOutputFile(fd.fileDescriptor)
                    prepare()
                    start()
                }
            recorder = r
            file = fd
            recordingUri = target
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start recording", e)
            recordings.delete(target)
            fail(R.string.error_microphone)
            return
        }
        levels = WaveformBuilder()
        session = Session(SystemClock.elapsedRealtime())
        silenced = false
        _live.value = FloatArray(0)
        audio.registerAudioRecordingCallback(recordingCallback, null)
        publish(force = true)
        RecordTile.refresh(this)
        ticker =
            scope.launch {
                var n = 0
                while (isActive) {
                    delay(Waveform.STEP_MS)
                    val r = recorder ?: break
                    val s = session ?: break
                    if (!s.paused) {
                        levels.add(Waveform.levelOf(runCatching { r.maxAmplitude }.getOrDefault(0)))
                        _live.value = levels.tail(LIVE_BARS)
                    }
                    publish(force = false)
                    if (++n % STORAGE_CHECK_TICKS == 0 && lowOnSpace()) {
                        stop(save = true, reason = R.string.stopped_low_storage)
                        break
                    }
                }
            }
    }

    private fun pause() {
        val r = recorder ?: return
        if (session?.pause(SystemClock.elapsedRealtime()) == true) runCatching { r.pause() }
        publish(force = true)
    }

    private fun resume() {
        val r = recorder ?: return
        if (session?.resume(SystemClock.elapsedRealtime()) == true) runCatching { r.resume() }
        publish(force = true)
    }

    private fun stop(
        save: Boolean,
        reason: Int? = null,
    ) {
        val r =
            recorder ?: run {
                stopSelf()
                return
            }
        ticker?.cancel()
        audio.unregisterAudioRecordingCallback(recordingCallback)
        _state.value = RecorderState.Saving
        // stop() throws when nothing was recorded yet (a tap on Stop straight away).
        val wrote = runCatching { r.stop() }.isSuccess
        releaseRecorder()
        val target = recordingUri
        recordingUri = null
        session = null
        scope.launch(Dispatchers.IO) {
            if (target != null) {
                if (save && wrote) {
                    recordings.publish(target, levels.build())
                    _saved.tryEmit(Saved(target, reason))
                } else {
                    recordings.delete(target)
                }
            }
            launch(Dispatchers.Main) {
                _state.value = RecorderState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                RecordTile.refresh(this@RecordingService)
                stopSelf()
            }
        }
    }

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        runCatching { file?.close() }
        file = null
    }

    private fun fail(message: Int) {
        _saved.tryEmit(Saved(null, message))
        _state.value = RecorderState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun lowOnSpace(): Boolean = StatFs(Environment.getExternalStorageDirectory().path).availableBytes < MIN_FREE_BYTES

    /** Updates the state everyone watches, and the notification once a second. */
    private fun publish(force: Boolean) {
        val s = session ?: return
        val state = s.state(SystemClock.elapsedRealtime(), silenced)
        _state.value = state
        val second = state.elapsedMs / 1000
        if (force || second != lastNotified) {
            lastNotified = second
            // The service shows its notification regardless (startForeground); updates need the permission.
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(state))
            }
        }
    }

    private fun notification(state: RecorderState.Active): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHOW_RECORDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val time = Durations.clock(state.elapsedMs)
        val text =
            when {
                state.silenced -> getString(R.string.notification_silenced)
                state.paused -> getString(R.string.notification_paused, time)
                else -> getString(R.string.notification_recording, time)
            }
        val toggle =
            if (state.paused) {
                NotificationCompat.Action(DesignR.drawable.ic_mic, getString(R.string.resume), action(ACTION_RESUME, 1))
            } else {
                NotificationCompat.Action(DesignR.drawable.ic_pause, getString(R.string.pause), action(ACTION_PAUSE, 2))
            }
        return NotificationCompat
            .Builder(this, CHANNEL)
            .setSmallIcon(DesignR.drawable.ic_mic)
            .setContentTitle(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggle)
            .addAction(NotificationCompat.Action(DesignR.drawable.ic_stop, getString(R.string.stop), action(ACTION_STOP, 3)))
            .build()
    }

    private fun action(
        name: String,
        code: Int,
    ) = PendingIntent.getService(this, code, Intent(this, RecordingService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE)

    /** A recording that was saved (uri) or couldn't be (message only). */
    data class Saved(
        val uri: Uri?,
        val message: Int?,
    )

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "io.github.michaelmassoni.tessera.recorder.START"
        const val ACTION_PAUSE = "io.github.michaelmassoni.tessera.recorder.PAUSE"
        const val ACTION_RESUME = "io.github.michaelmassoni.tessera.recorder.RESUME"
        const val ACTION_STOP = "io.github.michaelmassoni.tessera.recorder.STOP"
        const val ACTION_DISCARD = "io.github.michaelmassoni.tessera.recorder.DISCARD"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        private const val LIVE_BARS = 200
        private const val STORAGE_CHECK_TICKS = 100
        private const val MIN_FREE_BYTES = 50L * 1024 * 1024

        private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val state: StateFlow<RecorderState> = _state.asStateFlow()

        private val _live = MutableStateFlow(FloatArray(0))

        /** The last few seconds' levels (0..1), newest last, for the live waveform. */
        val live: StateFlow<FloatArray> = _live.asStateFlow()

        private val _saved = MutableSharedFlow<Saved>(extraBufferCapacity = 4)
        val saved: SharedFlow<Saved> = _saved.asSharedFlow()

        /** The uri being recorded into, so recovery leaves it alone. */
        @Volatile
        var recordingUri: Uri? = null
            private set

        fun send(
            context: Context,
            action: String,
        ) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                // Default importance so it sits with Pause and Stop showing, not folded into "Silent"; no
                // sound or vibration.
                NotificationChannel(CHANNEL, context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    description = context.getString(R.string.channel_recording_description)
                    setShowBadge(false)
                },
            )
        }
    }
}
