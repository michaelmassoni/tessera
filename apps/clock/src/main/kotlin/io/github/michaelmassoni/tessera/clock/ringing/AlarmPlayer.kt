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

    /** @param sound null = default alarm sound, "" = silent. */
    fun start(
        sound: String?,
        vibrate: Boolean,
    ) {
        stop()
        if (sound != "") {
            val candidates = listOfNotNull(sound?.let(Uri::parse), Settings.System.DEFAULT_ALARM_ALERT_URI)
            if (candidates.none(::tryPlay)) startBeeping()
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

    private fun tryPlay(uri: Uri): Boolean =
        try {
            player =
                MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
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
