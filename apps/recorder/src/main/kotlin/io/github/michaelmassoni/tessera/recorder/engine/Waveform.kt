package io.github.michaelmassoni.tessera.recorder.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A recording's loudness over time: one level (0..255) every [STEP_MS]. Kept beside each
 * recording so the player draws it without decoding the audio.
 */
class Waveform(
    val levels: ByteArray,
) {
    val size: Int get() = levels.size

    fun level(i: Int): Int = levels[i].toInt() and 0xFF

    /** Duration this waveform covers. */
    val durationMs: Long get() = size.toLong() * STEP_MS

    /**
     * [bars] levels (0..1) for drawing at a given width: each bar is the loudest level in its
     * share of the recording, so short sounds stay visible however long the recording is.
     */
    fun bars(bars: Int): FloatArray {
        if (bars <= 0) return FloatArray(0)
        if (size == 0) return FloatArray(bars)
        return FloatArray(bars) { b ->
            val from = (b.toLong() * size / bars).toInt()
            val to = max(from + 1, ((b + 1).toLong() * size / bars).toInt()).coerceAtMost(size)
            var peak = 0
            for (i in from until to) peak = max(peak, level(i))
            peak / 255f
        }
    }

    /** The file format: "TWAV", a version byte, then the levels. */
    fun encode(): ByteArray = MAGIC + byteArrayOf(VERSION) + levels

    companion object {
        const val STEP_MS = 50L
        private val MAGIC = "TWAV".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 1

        /** Reads [encode]'s format; null for anything else. */
        fun decode(bytes: ByteArray): Waveform? {
            if (bytes.size < MAGIC.size + 1) return null
            if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || bytes[MAGIC.size] != VERSION) return null
            return Waveform(bytes.copyOfRange(MAGIC.size + 1, bytes.size))
        }

        /**
         * MediaRecorder's amplitude (0..32767, the peak since it was last asked) as a level
         * 0..255, on a log scale from about -50 dB so quiet speech still shows.
         */
        fun levelOf(amplitude: Int): Int {
            if (amplitude <= 0) return 0
            val db = 20 * ln(amplitude / 32767.0) / ln(10.0)
            val fraction = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0)
            return (fraction * 255).roundToInt()
        }

        private const val FLOOR_DB = -50.0
    }
}

/** Collects levels while recording, growing as needed. */
class WaveformBuilder {
    private var data = ByteArray(INITIAL)
    var size = 0
        private set

    fun add(level: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = level.coerceIn(0, 255).toByte()
    }

    /** The last [count] levels (fewer at the start), 0..1, for the live view. */
    fun tail(count: Int): FloatArray {
        val n = minOf(count, size)
        return FloatArray(n) { (data[size - n + it].toInt() and 0xFF) / 255f }
    }

    fun build(): Waveform = Waveform(data.copyOf(size))

    private companion object {
        const val INITIAL = 1024
    }
}
