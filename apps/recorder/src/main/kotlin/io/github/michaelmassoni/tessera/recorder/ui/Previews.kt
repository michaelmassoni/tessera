package io.github.michaelmassoni.tessera.recorder.ui

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private fun at(
    day: Int,
    hour: Int,
    minute: Int,
) = LocalDateTime
    .of(2026, 11, day, hour, minute)
    .atZone(ZoneId.of("UTC"))
    .toInstant()
    .toEpochMilli()

private val sample =
    listOf(
        Recording(1, Uri.EMPTY, "Team stand-up", "Team stand-up.m4a", at(23, 9, 30), 754_000, 6_100_000),
        Recording(2, Uri.EMPTY, "Guitar idea", "Guitar idea.m4a", at(22, 21, 14), 96_000, 1_500_000),
        Recording(3, Uri.EMPTY, "Recording 21 Nov 18:02", "Recording 21 Nov 18:02.m4a", at(21, 18, 2), 42_000, 340_000),
        Recording(4, Uri.EMPTY, "Lecture: week 9", "Lecture- week 9.m4a", at(19, 14, 0), 3_212_000, 26_000_000),
    )

/** Speech-like: syllables in phrases, with pauses. */
private fun speech(count: Int): ByteArray =
    ByteArray(count) { i ->
        val phrase = sin(i * 2 * PI / 90)
        val syllable = abs(sin(i * 2 * PI / 7.3)) * (0.6 + 0.4 * abs(sin(i * 0.37)))
        val level = if (phrase < -0.55) 0.04 else syllable * 0.9
        (level * 255).toInt().coerceIn(0, 255).toByte()
    }

private object NoActions : RecorderActions {
    override fun record() = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit

    override fun discard() = Unit

    override fun rename(
        recording: Recording,
        title: String,
    ) = Unit

    override fun delete(recordings: List<Recording>) = Unit

    override fun share(recordings: List<Recording>) = Unit

    override fun openSettings() = Unit

    override fun waveform(recording: Recording): Waveform = Waveform(speech(400))
}

private object NoControls : PlaybackControls {
    override fun toggle() = Unit

    override fun seekTo(ms: Long) = Unit

    override fun skip(byMs: Long) = Unit

    override fun nextSpeed() = Unit
}

@TesseraPreviews
@Composable
private fun ListPreview() {
    TesseraTheme(dynamicColor = false) {
        ListScreen(RecorderUi(sample, loaded = true), NoActions, remember { SnackbarHostState() }, {}, {}, {})
    }
}

@TesseraPreviews
@Composable
private fun ListWhileRecordingPreview() {
    TesseraTheme(dynamicColor = false) {
        ListScreen(
            RecorderUi(sample, loaded = true, state = RecorderState.Active(192_400, paused = false)),
            NoActions,
            remember { SnackbarHostState() },
            {},
            {},
            {},
        )
    }
}

@TesseraPreviews
@Composable
private fun EmptyPreview() {
    TesseraTheme(dynamicColor = false) {
        ListScreen(RecorderUi(emptyList(), loaded = true), NoActions, remember { SnackbarHostState() }, {}, {}, {})
    }
}

@TesseraPreviews
@Composable
private fun RecordingPreview() {
    TesseraTheme(dynamicColor = false) {
        val levels = speech(300)
        RecordingScreen(
            RecorderState.Active(192_400, paused = false),
            FloatArray(levels.size) { (levels[it].toInt() and 0xFF) / 255f },
            NoActions,
            {},
        )
    }
}

@TesseraPreviews
@Composable
private fun PlayerPreview() {
    TesseraTheme(dynamicColor = false) {
        val r = sample[0]
        PlayerScreen(
            r,
            NoActions.waveform(r),
            Playback(positionMs = 290_000, durationMs = r.durationMs, playing = true, speed = 1.25f),
            NoControls,
            NoActions,
            {},
        )
    }
}
