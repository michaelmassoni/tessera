package io.github.michaelmassoni.tessera.recorder.data

import android.content.Context
import androidx.core.content.edit

/** Recording quality (Settings). AAC in an .m4a file either way. */
enum class Quality(
    val channels: Int,
    val sampleRate: Int,
    val bitRate: Int,
) {
    /** Speech: about 1 MB a minute. */
    Standard(1, 44_100, 128_000),

    /** Music and stereo: about 2 MB a minute. */
    High(2, 48_000, 256_000),
    ;

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "quality"

        fun get(context: Context): Quality =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
                ?.let { name -> entries.firstOrNull { it.name == name } } ?: Standard

        fun set(
            context: Context,
            quality: Quality,
        ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, quality.name) }
    }
}
