package io.github.michaelmassoni.tessera.podcasts.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.Progress
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.SleepTimer
import io.github.michaelmassoni.tessera.core.cast.R as CastR
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The bar above the navigation: cover, title, show, play/pause, with a progress line. */
@Composable
fun MiniPlayer(
    state: PodcastsState,
    progress: () -> Progress,
    actions: PodcastsActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = state.player.episodeId ?: return
    val episode = state.episode(id)
    val show = episode?.let { state.show(it.showId) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.open_player), onClick = onOpen),
    ) {
        Column {
            LinearProgressIndicator(progress = { progress().fraction }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(episode?.artworkUrl ?: show?.artworkUrl, 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        episode?.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (state.player.failed) stringResource(R.string.cant_play) else show?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.player.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = actions::back) {
                    Icon(painterResource(R.drawable.ic_back_10), contentDescription = stringResource(R.string.back_10))
                }
                IconButton(onClick = actions::togglePlay) {
                    Icon(
                        painterResource(
                            if (state.player.isPlaying) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled,
                        ),
                        contentDescription = stringResource(if (state.player.isPlaying) R.string.pause else R.string.play),
                    )
                }
            }
        }
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

fun speedLabel(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.', ',')
    return "$text×"
}

/** The full player: cover, titles, seek, back 10 / play / forward 30, speed and sleep timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    state: PodcastsState,
    progress: () -> Progress,
    actions: PodcastsActions,
    onClose: () -> Unit,
    onOpenShow: (String) -> Unit,
    modifier: Modifier = Modifier,
    // The Cast button (a slot, so previews show it without discovery) and the device being cast to.
    castButton: @Composable () -> Unit = {},
    castingTo: String? = null,
) {
    val id = state.player.episodeId
    val episode = id?.let(state::episode)
    val show = episode?.let { state.show(it.showId) }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth > maxHeight
            val width = maxWidth
            val height = maxHeight
            Column(Modifier.fillMaxSize()) {
                IconButton(onClick = onClose, modifier = Modifier.padding(4.dp)) {
                    Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = stringResource(R.string.close_player))
                }
                val details: @Composable (Modifier) -> Unit = { m ->
                    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            show?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.clickable(enabled = show != null) {
                                    onClose()
                                    show?.let { onOpenShow(it.id) }
                                },
                        )
                        Text(
                            episode?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (state.player.failed) {
                            Text(
                                stringResource(R.string.cant_play),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Seek(progress, actions, Modifier.padding(top = 16.dp))
                        Controls(state, actions)
                        Extras(state, actions, castButton)
                        castingTo?.let {
                            Text(
                                stringResource(CastR.string.cast_casting_to, it),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Cover(episode?.artworkUrl ?: show?.artworkUrl, (height - 96.dp).coerceIn(120.dp, 360.dp))
                        details(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Cover(episode?.artworkUrl ?: show?.artworkUrl, (width - 64.dp).coerceIn(160.dp, 360.dp))
                        details(Modifier.widthIn(max = 480.dp).padding(top = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Seek(
    progress: () -> Progress,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
) {
    val p = progress()
    val slider = remember { SliderState(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: p.fraction
    slider.value = shown
    val position = (shown * p.durationMs).toLong()
    val elapsed = clock(position)
    Column(modifier.fillMaxWidth()) {
        Slider(
            state = slider,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { actions.seekTo((it * p.durationMs).toLong()) }
                dragging = null
            },
            enabled = p.durationMs > 0,
            modifier = Modifier.semantics { stateDescription = elapsed },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(elapsed, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                if (p.durationMs > 0) "-" + clock(p.durationMs - position) else "",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Controls(
    state: PodcastsState,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::back, modifier = Modifier.size(64.dp)) {
            Icon(
                painterResource(R.drawable.ic_back_10),
                contentDescription = stringResource(R.string.back_10),
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(onClick = actions::togglePlay, modifier = Modifier.size(80.dp)) {
            Icon(
                painterResource(if (state.player.isPlaying) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled),
                contentDescription = stringResource(if (state.player.isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = actions::forward, modifier = Modifier.size(64.dp)) {
            Icon(
                painterResource(R.drawable.ic_forward_30),
                contentDescription = stringResource(R.string.forward_30),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** Cast, speed and sleep timer. */
@Composable
private fun Extras(
    state: PodcastsState,
    actions: PodcastsActions,
    castButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        castButton()
        Box {
            TextButton(onClick = { speedMenu = true }) {
                Icon(painterResource(DesignR.drawable.ic_speed), contentDescription = null)
                Text(speedLabel(state.player.speed), Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                speeds.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(speedLabel(s)) },
                        trailingIcon =
                            if (s == state.player.speed) {
                                { Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) }
                            } else {
                                null
                            },
                        onClick = {
                            speedMenu = false
                            actions.setSpeed(s)
                        },
                    )
                }
            }
        }
        Box {
            val sleep = state.player.sleep
            TextButton(onClick = { sleepMenu = true }) {
                Icon(painterResource(R.drawable.ic_bedtime), contentDescription = null)
                Text(
                    when (sleep) {
                        SleepTimer.Sleep.Off -> {
                            stringResource(R.string.sleep_timer)
                        }

                        SleepTimer.Sleep.EndOfEpisode -> {
                            stringResource(R.string.sleep_end_of_episode)
                        }

                        is SleepTimer.Sleep.At -> {
                            stringResource(
                                R.string.sleep_in,
                                ((sleep.endsAt - SystemClock.elapsedRealtime()) / 60_000 + 1).coerceAtLeast(1),
                            )
                        }
                    },
                    Modifier.padding(start = 8.dp),
                )
            }
            DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                listOf(5, 15, 30, 45, 60).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes)) },
                        onClick = {
                            sleepMenu = false
                            actions.setSleep(SleepTimer.Sleep.At(SystemClock.elapsedRealtime() + minutes * 60_000L))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_end_of_episode)) },
                    onClick = {
                        sleepMenu = false
                        actions.setSleep(SleepTimer.Sleep.EndOfEpisode)
                    },
                )
                if (sleep != SleepTimer.Sleep.Off) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sleep_off)) },
                        onClick = {
                            sleepMenu = false
                            actions.setSleep(SleepTimer.Sleep.Off)
                        },
                    )
                }
            }
        }
    }
}
