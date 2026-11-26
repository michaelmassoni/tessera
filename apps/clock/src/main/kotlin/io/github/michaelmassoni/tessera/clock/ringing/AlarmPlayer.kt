package io.github.michaelmassoni.tessera.clock.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

/**
 * Plays an alarm sound and vibration. Something always plays (docs/clock.md):
 * chosen sound → system default alarm → a generated beep on the alarm stream.
 */
class AlarmPlayer(
    private val context: Context,
) {
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    private val audioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /**
     * @param sound null = default alarm sound, "" = silent.
     * @param crescendoMs how long the sound takes to rise to full volume ([Crescendo]); 0 = at once.
     *   The generated beep (the last resort) plays at full volume.
     */
    fun start(
        sound: String?,
        vibrate: Boolean,
        crescendoMs: Long = 0,
    ) {
        stop()
        if (sound != "") {
            val candidates = listOfNotNull(sound?.let(Uri::parse), Settings.System.DEFAULT_ALARM_ALERT_URI)
            if (candidates.none { tryPlay(it, crescendoMs) }) startBeeping() else rampUp(crescendoMs)
        }
        if (vibrate) startVibrating()
    }

    fun stop() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        handler.removeCallbacksAndMessages(null)
        tone?.release()
        tone = null
        vibrator.cancel()
    }

    private fun tryPlay(
        uri: Uri,
        crescendoMs: Long,
    ): Boolean =
        try {
            player =
                MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    Crescendo.volume(0, crescendoMs).let { setVolume(it, it) }
                    start()
                }
            true
        } catch (e: Exception) {
            // Any failure (missing file, locked storage before first unlock, bad URI) falls through.
            Log.w(TAG, "Can't play $uri", e)
            player?.release()
            player = null
            false
        }

    /** Raises the player's volume step by step to full over [durationMs] (stopped by [stop]). */
    private fun rampUp(durationMs: Long) {
        if (durationMs <= 0) return
        val started = SystemClock.elapsedRealtime()
        val step =
            object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val volume = Crescendo.volume(SystemClock.elapsedRealtime() - started, durationMs)
                    runCatching { p.setVolume(volume, volume) }
                    if (volume < 1f) handler.postDelayed(this, Crescendo.STEP_MS)
                }
            }
        handler.postDelayed(step, Crescendo.STEP_MS)
    }

    private fun startBeeping() {
        val generator =
            runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return
        tone = generator
        val beep =
            object : Runnable {
                override fun run() {
                    generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MILLIS)
                    handler.postDelayed(this, BEEP_PERIOD_MILLIS)
                }
            }
        handler.post(beep)
    }

    private fun startVibrating() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        }
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        const val BEEP_MILLIS = 700
        const val BEEP_PERIOD_MILLIS = 1_200L
    }
}
