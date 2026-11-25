package io.github.michaelmassoni.tessera.podcasts.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.EpisodeState
import io.github.michaelmassoni.tessera.podcasts.engine.Episodes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A show's (or episode's) cover, rounded; a podcast glyph while it loads or when there's none. */
@Composable
fun Cover(
    url: String?,
    size: Dp?,
    modifier: Modifier = Modifier,
) {
    val shape = if (size == null || size >= 120.dp) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    Box(
        modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_podcasts),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
            modifier = Modifier.fillMaxSize(0.4f),
        )
        // Previews and screenshot tests have no network: the placeholder stands in.
        if (url != null && !LocalInspectionMode.current) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** "25 Nov" this year, "25 Nov 2024" before; in the locale's order. */
@Composable
fun dateText(
    millis: Long,
    now: Long,
): String {
    if (millis <= 0) return ""
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val thisYear = Instant.ofEpochMilli(now).atZone(zone).year
    val pattern = DateFormat.getBestDateTimePattern(locale, if (date.year == thisYear) "dMMM" else "dMMMyyyy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** "1 hr 5 min", "45 min"; for time left, "12 min left". */
@Composable
fun durationText(
    ms: Long,
    left: Boolean = false,
): String {
    if (ms <= 0) return ""
    val minutes = ((ms + 30_000) / 60_000).coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    val text =
        when {
            h == 0L -> stringResource(R.string.duration_min, m)
            m == 0L -> stringResource(R.string.duration_hr, h)
            else -> stringResource(R.string.duration_hr_min, h, m)
        }
    return if (left) stringResource(R.string.time_left, text) else text
}

/** "0:42", "12:05", "1:02:03". */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Play/pause with a ring showing how far through the episode is. */
@Composable
fun PlayButton(
    playing: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
) {
    val label = stringResource(if (playing) R.string.pause else R.string.play)
    IconButton(onClick = onClick, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            if (buffering) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
            } else if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                )
            }
            Icon(
                painterResource(if (playing) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Download: start, progress (tap to cancel), or done. */
@Composable
fun DownloadButton(
    downloaded: Boolean,
    percent: Int?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        downloaded -> {
            Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_download_done),
                    contentDescription = stringResource(R.string.downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        percent != null -> {
            IconButton(onClick = onCancel, modifier = modifier) {
                Box(contentAlignment = Alignment.Center) {
                    if (percent < 0) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            gapSize = 0.dp,
                        )
                    }
                    Icon(
                        painterResource(DesignR.drawable.ic_close),
                        contentDescription = stringResource(R.string.cancel_download),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        else -> {
            IconButton(onClick = onDownload, modifier = modifier) {
                Icon(painterResource(DesignR.drawable.ic_download), contentDescription = stringResource(R.string.download))
            }
        }
    }
}

/**
 * An episode in a list: date and length, title, summary; play (with progress), download and a
 * menu. Played episodes are dimmed.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    state: EpisodeState,
    podcasts: PodcastsState,
    actions: PodcastsActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: String? = null,
    artworkUrl: String? = null,
    onGoToShow: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    // Off in Up Next itself, where it goes without saying.
    showQueued: Boolean = true,
) {
    val player = podcasts.player
    val current = player.episodeId == episode.id
    val playing = current && player.isPlaying
    val queued = episode.id in player.queue
    val remaining = Episodes.remaining(episode, state)
    val started = state.positionMs > 0 && !state.played
    val meta =
        listOfNotNull(
            dateText(episode.publishedAt, podcasts.now).ifEmpty { null },
            when {
                state.played -> stringResource(R.string.played)
                started -> durationText(remaining, left = true).ifEmpty { null }
                else -> durationText(episode.durationMs).ifEmpty { null }
            },
        ).joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (artworkUrl != null || showTitle != null) {
            Cover(artworkUrl, 56.dp, Modifier.alpha(if (state.played) 0.6f else 1f))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).alpha(if (state.played) 0.6f else 1f)) {
            if (showTitle != null) {
                Text(
                    showTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                episode.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (episode.summary.isNotBlank()) {
                Text(
                    episode.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                val progressLabel = if (started) durationText(remaining, left = true) else ""
                PlayButton(
                    playing = playing,
                    progress = if (started) Episodes.progress(episode, state) else 0f,
                    buffering = current && player.buffering,
                    onClick = { if (current) actions.togglePlay() else actions.play(episode) },
                    modifier = Modifier.semantics { if (progressLabel.isNotEmpty()) contentDescription = progressLabel },
                )
                DownloadButton(
                    downloaded = state.download != null,
                    percent = podcasts.downloading[episode.id],
                    onDownload = { actions.download(episode.id) },
                    onCancel = { actions.cancelDownload(episode.id) },
                )
                if (showQueued && queued && !current) {
                    Icon(
                        painterResource(DesignR.drawable.ic_queue_music),
                        contentDescription = stringResource(R.string.in_up_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
                EpisodeMenu(episode, state, queued, current, actions, onGoToShow)
            }
        }
    }
}

@Composable
fun EpisodeMenu(
    episode: Episode,
    state: EpisodeState,
    queued: Boolean,
    current: Boolean,
    actions: PodcastsActions,
    onGoToShow: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(
                label: Int,
                icon: Int,
                action: () -> Unit,
            ) = DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                onClick = {
                    open = false
                    action()
                },
            )
            if (!current) {
                item(R.string.play_next, DesignR.drawable.ic_queue_play_next) { actions.playNext(episode) }
                if (queued) {
                    item(R.string.remove_from_up_next, DesignR.drawable.ic_playlist_add_check) { actions.removeFromQueue(episode.id) }
                } else {
                    item(R.string.add_to_up_next, DesignR.drawable.ic_playlist_add) { actions.addToQueue(episode) }
                }
            }
            if (state.played) {
                item(R.string.mark_unplayed, DesignR.drawable.ic_radio_button_unchecked) { actions.markPlayed(episode.id, false) }
            } else {
                item(R.string.mark_played, DesignR.drawable.ic_check_circle) { actions.markPlayed(episode.id, true) }
            }
            if (state.download != null) {
                item(R.string.delete_download, DesignR.drawable.ic_delete) { actions.deleteDownload(episode.id) }
            }
            if (onGoToShow != null) {
                item(R.string.go_to_show, R.drawable.ic_podcasts, onGoToShow)
            }
        }
    }
}

/** A message in the middle of an empty page, with an optional button. */
@Composable
fun EmptyMessage(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_podcasts),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun episodeCount(count: Int) = pluralStringResource(R.plurals.episodes, count, count)
