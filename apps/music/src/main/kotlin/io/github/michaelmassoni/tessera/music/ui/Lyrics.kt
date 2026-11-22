package io.github.michaelmassoni.tessera.music.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.Progress
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.Lyrics
import io.github.michaelmassoni.tessera.music.engine.LyricsSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * The line being sung. The player reports the position twice a second; between reports this
 * waits exactly until the next line is due, so lines change on time without redrawing every frame.
 */
@Composable
private fun rememberCurrentLine(
    lyrics: Lyrics,
    progress: () -> Progress,
    playing: Boolean,
): State<Int> {
    val line = remember(lyrics) { mutableIntStateOf(lyrics.current(progress().positionMs)) }
    val read by rememberUpdatedState(progress)
    LaunchedEffect(lyrics, playing) {
        snapshotFlow { read().positionMs }.collectLatest { position ->
            val sampledAt = SystemClock.uptimeMillis()
            while (true) {
                val at = if (playing) position + (SystemClock.uptimeMillis() - sampledAt) else position
                val i = lyrics.current(at)
                line.intValue = i
                val next = lyrics.lines.getOrNull(i + 1)?.timeMs
                if (!playing || next == null) break
                delay((next - at).coerceAtLeast(1))
            }
        }
    }
    return line
}

/** Under the controls: the line being sung and the next, or why there are none. Opens [LyricsScreen]. */
@Composable
fun LyricsCard(
    lyrics: LyricsState,
    progress: () -> Progress,
    playing: Boolean,
    onOpen: () -> Unit,
    onLookOnline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val found = (lyrics as? LyricsState.Found)?.lyrics
    Card(
        onClick = onOpen,
        enabled = found != null,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lyrics), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (found != null) {
                    Icon(painterResource(DesignR.drawable.ic_open_in_full), contentDescription = stringResource(R.string.show_lyrics))
                }
            }
            when (lyrics) {
                LyricsState.Loading -> {
                    Text(stringResource(R.string.lyrics_loading), style = MaterialTheme.typography.bodyLarge)
                }

                is LyricsState.None -> {
                    Text(stringResource(R.string.no_lyrics), style = MaterialTheme.typography.bodyLarge)
                    if (lyrics.canLookOnline) {
                        Text(stringResource(R.string.online_lyrics_explain), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onLookOnline, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(R.string.look_up_lyrics))
                        }
                    }
                }

                is LyricsState.Found -> {
                    val l = lyrics.lyrics
                    if (l.synced) {
                        val current by rememberCurrentLine(l, progress, playing)
                        val now =
                            l.lines
                                .getOrNull(current)
                                ?.text
                                ?.ifBlank { null } ?: "♪"
                        val next =
                            l.lines
                                .drop(current + 1)
                                .firstOrNull { it.text.isNotBlank() }
                                ?.text
                        Text(
                            now,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (next != null) {
                            Text(next, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(
                            l.lines
                                .filter { it.text.isNotBlank() }
                                .take(4)
                                .joinToString("\n") { it.text },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The whole song's lyrics, following the song: the line being sung stands out and stays in view,
 * unless the person has just scrolled (it picks up again a few seconds later). Tapping a synced
 * line plays from there.
 */
@Composable
fun LyricsScreen(
    lyrics: Lyrics,
    state: PlaybackState,
    progress: () -> Progress,
    actions: PlayerActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberCurrentLine(lyrics, progress, state.isPlaying)
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    val lastTouch = remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastTouch.longValue = System.currentTimeMillis() }
    LaunchedEffect(current) {
        val idle = System.currentTimeMillis() - lastTouch.longValue > FOLLOW_AGAIN_MS
        if (lyrics.synced && current >= 0 && idle && !dragged) {
            // Keep the current line about a third of the way down.
            list.animateScrollToItem(current, scrollOffset = -list.layoutInfo.viewportSize.height / 3)
        }
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = stringResource(R.string.close_lyrics))
                }
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = actions::togglePlay) {
                    Icon(
                        painterResource(if (state.isPlaying) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled),
                        contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                ) {
                    itemsIndexed(lyrics.lines) { i, line ->
                        val sung = lyrics.synced && i <= current
                        val time = line.timeMs
                        Text(
                            line.text.ifBlank { if (lyrics.synced) "♪" else "" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = if (lyrics.synced) FontWeight.Bold else FontWeight.Normal,
                            color =
                                when {
                                    !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                                    i == current -> MaterialTheme.colorScheme.primary
                                    sung -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .let { m -> if (time != null) m.clickable { actions.seekTo(time) } else m },
                        )
                    }
                    item {
                        Spacer(Modifier.padding(top = 16.dp))
                        Text(
                            stringResource(
                                if (lyrics.source ==
                                    LyricsSource.LrcLib
                                ) {
                                    R.string.lyrics_from_lrclib
                                } else {
                                    R.string.lyrics_from_server
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val FOLLOW_AGAIN_MS = 4_000L
