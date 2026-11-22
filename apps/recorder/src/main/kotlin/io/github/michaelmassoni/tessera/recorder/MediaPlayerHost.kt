package io.github.michaelmassoni.tessera.recorder

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.ui.Playback
import io.github.michaelmassoni.tessera.recorder.ui.PlaybackControls
import kotlinx.coroutines.delay

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Plays [recording] with MediaPlayer while the player is open: it pauses when the app leaves the
 * screen or another app takes audio focus, and is released when the player closes.
 */
@Composable
internal fun MediaPlayerHost(
    recording: Recording,
    content: @Composable (Playback, PlaybackControls) -> Unit,
) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var playback by remember(recording.uri) { mutableStateOf(Playback(durationMs = recording.durationMs)) }
    val player =
        remember(recording.uri) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(context, recording.uri)
                    prepare()
                }
            }.getOrNull()
        }
    val focus =
        remember(player) {
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setOnAudioFocusChangeListener { change ->
                    if (change < 0 && player?.isPlaying == true) {
                        player.pause()
                        playback = playback.copy(playing = false)
                    }
                }.build()
        }
    DisposableEffect(player) {
        player?.setOnCompletionListener {
            playback = playback.copy(playing = false, positionMs = playback.durationMs)
            audio.abandonAudioFocusRequest(focus)
        }
        if (player != null) playback = playback.copy(durationMs = player.duration.toLong().coerceAtLeast(0))
        onDispose {
            audio.abandonAudioFocusRequest(focus)
            player?.release()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE && player?.isPlaying == true) {
                    player.pause()
                    playback = playback.copy(playing = false)
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(playback.playing, player) {
        while (playback.playing && player != null) {
            playback = playback.copy(positionMs = player.currentPosition.toLong())
            delay(POLL_MS)
        }
    }
    val controls =
        remember(player) {
            object : PlaybackControls {
                override fun toggle() {
                    val p = player ?: return
                    if (p.isPlaying) {
                        p.pause()
                        playback = playback.copy(playing = false)
                        return
                    }
                    if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
                    if (playback.positionMs >= playback.durationMs - END_SLACK_MS) p.seekTo(0)
                    // Setting the speed starts playback, so it's set only here.
                    p.playbackParams = PlaybackParams().setSpeed(playback.speed)
                    p.start()
                    playback = playback.copy(playing = true, positionMs = p.currentPosition.toLong())
                }

                override fun seekTo(ms: Long) {
                    val p = player ?: return
                    val to = ms.coerceIn(0, playback.durationMs)
                    p.seekTo(to, MediaPlayer.SEEK_CLOSEST)
                    playback = playback.copy(positionMs = to)
                }

                override fun skip(byMs: Long) = seekTo(playback.positionMs + byMs)

                override fun nextSpeed() {
                    val next = Speeds[(Speeds.indexOf(playback.speed) + 1) % Speeds.size]
                    playback = playback.copy(speed = next)
                    val p = player ?: return
                    if (p.isPlaying) p.playbackParams = PlaybackParams().setSpeed(next)
                }
            }
        }
    content(playback, controls)
}

private const val POLL_MS = 50L
private const val END_SLACK_MS = 100L
