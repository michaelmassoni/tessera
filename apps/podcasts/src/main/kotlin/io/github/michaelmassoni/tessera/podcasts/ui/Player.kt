package io.github.michaelmassoni.tessera.podcasts.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.Progress
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.SleepTimer
import io.github.michaelmassoni.tessera.podcasts.engine.Chapter
import io.github.michaelmassoni.tessera.podcasts.engine.Chapters
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
                    Icon(painterResource(R.drawable.ic_back_15), contentDescription = stringResource(R.string.back_15))
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
    var chaptersOpen by rememberSaveable { mutableStateOf(false) }
    var upNext by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (chaptersOpen && state.player.chapters.isNotEmpty()) {
            ChaptersSheet(state.player.chapters, progress, actions, onDismiss = { chaptersOpen = false })
        }
        if (upNext) UpNextSheet(state, actions, onDismiss = { upNext = false })
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
                        if (state.player.chapters.isNotEmpty()) {
                            AssistChip(
                                onClick = { chaptersOpen = true },
                                label = { Text(stringResource(R.string.chapters)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_view_list), contentDescription = null) },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
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
                        Seek(progress, actions, state.player.chapters, Modifier.padding(top = 16.dp))
                        Controls(state, actions)
                        OutputRow(castButton, castingTo, onQueue = { upNext = true })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Seek(
    progress: () -> Progress,
    actions: PodcastsActions,
    chapters: List<Chapter>,
    modifier: Modifier = Modifier,
) {
    val p = progress()
    val slider = remember { SliderState(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: p.fraction
    slider.value = shown
    val position = (shown * p.durationMs).toLong()
    val elapsed = clock(position)
    val marks = remember(chapters, p.durationMs) { Chapters.marks(chapters, p.durationMs) }
    val chapter = chapters.getOrNull(Chapters.current(chapters, position))
    val gap = MaterialTheme.colorScheme.surface
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
            // Where each chapter begins: a small break in the track.
            track = { s ->
                SliderDefaults.Track(
                    sliderState = s,
                    modifier =
                        Modifier.drawWithContent {
                            drawContent()
                            val w = 3.dp.toPx()
                            marks.forEach { f -> drawRect(gap, Offset(size.width * f - w / 2, 0f), Size(w, size.height)) }
                        },
                )
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(elapsed, style = MaterialTheme.typography.labelMedium)
            Text(
                chapter?.let { chapterTitle(chapters, it) }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text(
                if (p.durationMs > 0) "-" + clock(p.durationMs - position) else "",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun chapterTitle(
    chapters: List<Chapter>,
    chapter: Chapter,
): String = chapter.title.ifBlank { stringResource(R.string.chapter_n, chapters.indexOf(chapter) + 1) }

/** Speed, back 15, play/pause, forward 15, sleep timer. */
@Composable
private fun Controls(
    state: PodcastsState,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().alignedToEdges().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The two ends take the same width, so play/pause stays in the middle whatever the speed reads.
        Box(Modifier.width(SIDE), contentAlignment = Alignment.CenterStart) { SpeedButton(state.player.speed, actions) }
        IconButton(onClick = actions::back, modifier = Modifier.size(64.dp)) {
            Icon(
                painterResource(R.drawable.ic_back_15),
                contentDescription = stringResource(R.string.back_15),
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
                painterResource(R.drawable.ic_forward_15),
                contentDescription = stringResource(R.string.forward_15),
                modifier = Modifier.size(36.dp),
            )
        }
        Box(Modifier.width(SIDE), contentAlignment = Alignment.CenterEnd) { SleepButton(state.player.sleep, actions) }
    }
}

@Composable
private fun SpeedButton(
    speed: Float,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val label = stringResource(R.string.playback_speed, speedLabel(speed))
    Box(modifier) {
        TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = label }) {
            Text(speedLabel(speed), style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            speeds.forEach { s ->
                DropdownMenuItem(
                    text = { Text(speedLabel(s)) },
                    trailingIcon =
                        if (s == speed) {
                            { Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) }
                        } else {
                            null
                        },
                    onClick = {
                        menu = false
                        actions.setSpeed(s)
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepButton(
    sleep: SleepTimer.Sleep,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val status =
        when (sleep) {
            SleepTimer.Sleep.Off -> {
                null
            }

            SleepTimer.Sleep.EndOfEpisode -> {
                stringResource(R.string.sleep_end_of_episode)
            }

            is SleepTimer.Sleep.At -> {
                stringResource(R.string.sleep_in, ((sleep.endsAt - SystemClock.elapsedRealtime()) / 60_000 + 1).coerceAtLeast(1))
            }
        }
    Box(modifier) {
        IconButton(onClick = { menu = true }) {
            Icon(
                painterResource(DesignR.drawable.ic_timer),
                contentDescription = listOfNotNull(stringResource(R.string.sleep_timer), status).joinToString(", "),
                tint = if (status != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            // While it's set, what it will do, first.
            status?.let { DropdownMenuItem(text = { Text(it, color = MaterialTheme.colorScheme.primary) }, onClick = {}, enabled = false) }
            listOf(5, 15, 30, 45, 60).forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes)) },
                    onClick = {
                        menu = false
                        actions.setSleep(SleepTimer.Sleep.At(SystemClock.elapsedRealtime() + minutes * 60_000L))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sleep_end_of_episode)) },
                onClick = {
                    menu = false
                    actions.setSleep(SleepTimer.Sleep.EndOfEpisode)
                },
            )
            if (sleep != SleepTimer.Sleep.Off) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_off)) },
                    onClick = {
                        menu = false
                        actions.setSleep(SleepTimer.Sleep.Off)
                    },
                )
            }
        }
    }
}

/**
 * Under the controls: where it's playing on the left (the Cast button, and the device while
 * casting) and Up Next on the right, the icons in line with the seek bar.
 */
@Composable
private fun OutputRow(
    castButton: @Composable () -> Unit,
    castingTo: String?,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().alignedToEdges(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        castButton()
        Text(
            castingTo?.let { stringResource(CastR.string.cast_casting_to, it) }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onQueue) {
            Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = stringResource(R.string.up_next))
        }
    }
}

/** The episode's chapters: each with where it starts; the one playing marked; tap to go there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(
    chapters: List<Chapter>,
    progress: () -> Progress,
    actions: PodcastsActions,
    onDismiss: () -> Unit,
) {
    val current = Chapters.current(chapters, progress().positionMs)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(chapters) { i, chapter ->
                ListItem(
                    selected = i == current,
                    onClick = {
                        actions.seekTo(chapter.startMs)
                        onDismiss()
                    },
                    leadingContent = chapter.imageUrl?.let { url -> { Cover(url, 40.dp) } },
                    trailingContent = { Text(clock(chapter.startMs), style = MaterialTheme.typography.labelMedium) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) { Text(chapterTitle(chapters, chapter), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Up Next from the player: the episodes after this one; tap one to play it, or take it out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextSheet(
    state: PodcastsState,
    actions: PodcastsActions,
    onDismiss: () -> Unit,
) {
    val next =
        state.player.queue
            .drop(1)
            .mapNotNull(state::episode)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.up_next),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (next.isEmpty()) {
            Text(
                stringResource(R.string.up_next_nothing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(next, key = { it.id }) { episode ->
                val show = state.show(episode.showId)
                ListItem(
                    onClick = {
                        actions.play(episode)
                        onDismiss()
                    },
                    leadingContent = { Cover(episode.artworkUrl ?: show?.artworkUrl, 48.dp) },
                    supportingContent = { Text(show?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { actions.removeFromQueue(episode.id) }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_close),
                                contentDescription = stringResource(R.string.remove_from_up_next),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Widened by the buttons' padding, so the first and last icons line up with the seek bar's ends. */
private fun Modifier.alignedToEdges(): Modifier =
    layout { measurable, constraints ->
        val extra = 24.dp.roundToPx()
        val wider = constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
        val placeable = measurable.measure(wider)
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }

private val SIDE = 80.dp
