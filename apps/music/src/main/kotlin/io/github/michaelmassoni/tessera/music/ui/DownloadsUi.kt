package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.data.Connectivity
import io.github.michaelmassoni.tessera.music.data.Downloads
import io.github.michaelmassoni.tessera.music.engine.Offline
import io.github.michaelmassoni.tessera.music.engine.OfflineKind
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Download for offline listening, beside Play and Shuffle on an album, playlist or Liked songs
 * (docs/music.md, Downloads). Only for songs from a server. Filled once everything is on the phone.
 */
@Composable
internal fun DownloadToggle(
    kind: OfflineKind,
    id: Long,
    songs: List<Song>,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    if (songs.none { Offline.serverId(it) != null }) return
    val state by Downloads.state.collectAsState()
    val pinned = state.pins.has(kind, id)
    val complete = pinned && Offline.downloadedCount(songs, state.onPhone) >= songs.count { Offline.serverId(it) != null }
    FilledTonalIconToggleButton(checked = pinned, onCheckedChange = { actions.setDownload(kind, id, it) }, modifier = modifier) {
        Icon(
            painterResource(if (complete) DesignR.drawable.ic_download_done else DesignR.drawable.ic_download),
            contentDescription = stringResource(if (pinned) R.string.downloaded else R.string.download),
        )
    }
}

/** "3 of 12 downloaded" under the buttons while a download isn't finished. */
@Composable
internal fun DownloadStatus(
    kind: OfflineKind,
    id: Long,
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    val state by Downloads.state.collectAsState()
    if (!state.pins.has(kind, id)) return
    val unmetered by Connectivity.unmetered.collectAsState()
    val total = songs.count { Offline.serverId(it) != null }
    val done = Offline.downloadedCount(songs, state.onPhone)
    if (done >= total) return
    Text(
        pluralStringResource(R.plurals.downloaded_count, total, done, total) +
            // Nothing moves on mobile data when downloads wait for Wi-Fi: say so.
            if (state.progress == null && state.wifiOnly && !unmetered) " · " + stringResource(R.string.download_waiting_wifi) else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** "Downloaded": shows only what's on the phone, in Songs, Albums and Playlists. */
@Composable
internal fun DownloadedChip(
    selected: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(stringResource(R.string.downloaded)) },
        leadingIcon = {
            Icon(
                painterResource(DesignR.drawable.ic_download_done),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/** Above the library while the phone is offline: what still works. */
@Composable
internal fun OfflineNotice(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.offline_notice),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
