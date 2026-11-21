package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The Playlists tab: a New playlist button and every playlist with its song count. */
@Composable
internal fun PlaylistList(
    playlists: List<Playlist>,
    actions: LibraryActions,
    onOpen: (Long) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "new") {
            FilledTonalButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.new_playlist), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.empty_playlists),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        items(Playlists.sorted(playlists), key = { it.id }) { p ->
            ListItem(
                onClick = { onOpen(p.id) },
                leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = null) },
                supportingContent = { Text(pluralStringResource(R.plurals.song_count, p.songIds.size, p.songIds.size)) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (creating) {
        NameDialog(R.string.new_playlist, "", onDismiss = { creating = false }) { name ->
            creating = false
            actions.createPlaylist(name, emptyList())
        }
    }
}

/** One playlist: Play and Shuffle, its songs (long-press to remove one), and rename or delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistScreen(
    playlist: Playlist,
    songs: List<Song>,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onBack: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf<Pair<Int, Song>?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
                                menu = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete_playlist)) }, onClick = {
                                menu = false
                                deleting = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(songs, actions, showShuffle = false, onLongClick = { i, s -> options = i to s }, header = {
                item(key = "header") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            pluralStringResource(R.plurals.song_count, songs.size, songs.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { actions.play(songs, 0) }, enabled = songs.isNotEmpty()) {
                                Icon(painterResource(DesignR.drawable.ic_play_arrow_filled), contentDescription = null)
                                Text(stringResource(R.string.play), modifier = Modifier.padding(start = 8.dp))
                            }
                            FilledTonalButton(onClick = { actions.shuffle(songs) }, enabled = songs.isNotEmpty()) {
                                Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null)
                                Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        if (songs.isEmpty()) {
                            Text(stringResource(R.string.empty_playlist), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            })
        }
    }
    options?.let { (index, song) ->
        SongOptions(song, playlists, actions, onDismiss = { options = null }, onRemove = { actions.removeFromPlaylist(playlist.id, index) })
    }
    if (renaming) {
        NameDialog(R.string.rename, playlist.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            actions.renamePlaylist(playlist.id, name)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_playlist_title, playlist.name)) },
            text = { Text(stringResource(R.string.delete_playlist_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onBack()
                    actions.deletePlaylist(playlist.id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * What a long press on a song offers: add it to a playlist (or a new one), and, inside a
 * playlist, remove it from there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongOptions(
    song: Song,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    when {
        creating -> {
            NameDialog(R.string.new_playlist, "", onDismiss = onDismiss) { name ->
                actions.createPlaylist(name, listOf(song.id))
                onDismiss()
            }
        }

        picking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.add_to_playlist)) },
                text = {
                    LazyColumn {
                        item(key = "new") {
                            ListItem(
                                onClick = { creating = true },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null) },
                            ) { Text(stringResource(R.string.new_playlist)) }
                        }
                        items(Playlists.sorted(playlists), key = { it.id }) { p ->
                            ListItem(
                                onClick = {
                                    actions.addToPlaylist(p.id, listOf(song.id))
                                    onDismiss()
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = null) },
                            ) { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }

        else -> {
            ModalBottomSheet(onDismissRequest = onDismiss) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    ListItem(supportingContent = { Text(song.artist.ifBlank { stringResource(R.string.unknown_artist) }, maxLines = 1) }) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ListItem(
                        onClick = { picking = true },
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = null) },
                    ) { Text(stringResource(R.string.add_to_playlist)) }
                    if (onRemove != null) {
                        ListItem(
                            onClick = {
                                onRemove()
                                onDismiss()
                            },
                            leadingContent = { Icon(painterResource(DesignR.drawable.ic_close), contentDescription = null) },
                        ) { Text(stringResource(R.string.remove_from_playlist)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: Int,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val text = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                state = text,
                label = { Text(stringResource(R.string.playlist_name)) },
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.text.toString()) }, enabled = text.text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
