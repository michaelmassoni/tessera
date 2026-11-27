package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraTextField
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.Song
import androidx.compose.foundation.lazy.grid.items as gridItems
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The Playlists tab: a New playlist button and every playlist with its song count. */
@Composable
internal fun PlaylistList(
    playlists: List<Playlist>,
    byId: Map<Long, Song>,
    grid: Boolean,
    actions: LibraryActions,
    onOpen: (Long) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    if (grid) {
        PlaylistGrid(playlists, byId, onNew = { creating = true }, onOpen = onOpen)
    } else {
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
            items(playlists, key = { it.id }) { p ->
                ListItem(
                    onClick = { onOpen(p.id) },
                    leadingContent = { PlaylistArt(p, byId, 56.dp) },
                    supportingContent = {
                        val count = pluralStringResource(R.plurals.song_count, p.songIds.size, p.songIds.size)
                        Text(if (p.readOnly) stringResource(R.string.read_only_playlist, count) else count)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
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
    onAlbum: ((Long) -> Unit)? = null,
    onArtist: ((Long) -> Unit)? = null,
    playingId: Long = 0,
    // The current song is playing (not paused): its indicator moves.
    playing: Boolean = false,
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
                    // A smart playlist (or someone else's) can be played, not changed.
                    if (!playlist.readOnly) {
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
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(
                songs,
                actions,
                PlayingFrom(
                    PlayingFrom.Kind.Playlist,
                    playlist.name,
                    playlist.id,
                ),
                showShuffle = false,
                playingId = playingId,
                playing = playing,
                onLongClick = {
                    i,
                    s,
                    ->
                    options =
                        i to s
                },
                header = {
                    item(key = "header") {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                pluralStringResource(R.plurals.song_count, songs.size, songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        actions.play(
                                            songs,
                                            0,
                                            PlayingFrom(PlayingFrom.Kind.Playlist, playlist.name, playlist.id),
                                        )
                                    },
                                    enabled = songs.isNotEmpty(),
                                ) {
                                    Icon(painterResource(DesignR.drawable.ic_play_arrow_filled), contentDescription = null)
                                    Text(stringResource(R.string.play), modifier = Modifier.padding(start = 8.dp))
                                }
                                FilledTonalButton(onClick = {
                                    actions.shuffle(songs, PlayingFrom(PlayingFrom.Kind.Playlist, playlist.name, playlist.id))
                                }, enabled = songs.isNotEmpty()) {
                                    Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null)
                                    Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            if (songs.isEmpty()) {
                                Text(stringResource(R.string.empty_playlist), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
            )
        }
    }
    options?.let { (index, song) ->
        SongOptions(
            song,
            playlists,
            actions,
            onDismiss = {
                options = null
            },
            onRemove =
                if (playlist.readOnly) {
                    null
                } else {
                    (
                        {
                            actions.removeFromPlaylist(
                                playlist.id,
                                index,
                            )
                        }
                    )
                },
            onAlbum = onAlbum,
            onArtist = onArtist,
        )
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
 * What a long press on a song offers: play it next or queue it, add it to a playlist (or a new
 * one), go to its album or artist (where given), and, inside a playlist, remove it from there.
 */
@Composable
internal fun SongOptions(
    song: Song,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onAlbum: ((Long) -> Unit)? = null,
    onArtist: ((Long) -> Unit)? = null,
) = SongsOptions(
    title = song.title,
    subtitle = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
    songs = listOf(song),
    playlists = playlists,
    actions = actions,
    onDismiss = onDismiss,
    onRemove = onRemove,
    onAlbum = onAlbum?.let { go -> { go(song.albumId) } },
    onArtist = onArtist?.let { go -> { go(song.artistId) } },
)

/**
 * The same for a whole album or artist (its page's More options): play it next, queue it,
 * shuffle it, add it to a playlist, and go to its artist where there is one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongsOptions(
    title: String,
    subtitle: String,
    songs: List<Song>,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onArtist: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val ids = songs.map { it.id }
    when {
        creating -> {
            NameDialog(R.string.new_playlist, "", onDismiss = onDismiss) { name ->
                actions.createPlaylist(name, ids)
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
                        items(Playlists.sorted(playlists.filterNot { it.readOnly }), key = { it.id }) { p ->
                            ListItem(
                                onClick = {
                                    actions.addToPlaylist(p.id, ids)
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
                    ListItem(supportingContent = { Text(subtitle, maxLines = 1) }) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ListItem(
                        onClick = {
                            actions.playNext(songs)
                            onDismiss()
                        },
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_play_next), contentDescription = null) },
                    ) { Text(stringResource(R.string.play_next)) }
                    ListItem(
                        onClick = {
                            actions.addToQueue(songs)
                            onDismiss()
                        },
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_add_to_queue), contentDescription = null) },
                    ) { Text(stringResource(R.string.add_to_queue)) }
                    if (onShuffle != null) {
                        ListItem(
                            onClick = {
                                onDismiss()
                                onShuffle()
                            },
                            leadingContent = { Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null) },
                        ) { Text(stringResource(R.string.shuffle)) }
                    }
                    ListItem(
                        onClick = { picking = true },
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = null) },
                    ) { Text(stringResource(R.string.add_to_playlist)) }
                    if (onAlbum != null) {
                        ListItem(
                            onClick = {
                                onDismiss()
                                onAlbum()
                            },
                            leadingContent = { Icon(painterResource(DesignR.drawable.ic_album), contentDescription = null) },
                        ) { Text(stringResource(R.string.go_to_album)) }
                    }
                    if (onArtist != null) {
                        ListItem(
                            onClick = {
                                onDismiss()
                                onArtist()
                            },
                            leadingContent = { Icon(painterResource(DesignR.drawable.ic_artist), contentDescription = null) },
                        ) { Text(stringResource(R.string.go_to_artist)) }
                    }
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
            TesseraTextField(
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

/**
 * From the player: tick the playlists the song playing should be in. Ticking adds it at the end;
 * unticking takes it out (everywhere in that playlist). Smart playlists aren't offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveToPlaylistSheet(
    songId: Long,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(R.string.new_playlist, "", onDismiss = { creating = false }) { name ->
            creating = false
            actions.createPlaylist(name, listOf(songId))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.save_to_playlist),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            item(key = "new") {
                ListItem(
                    onClick = { creating = true },
                    leadingContent = { Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null) },
                ) { Text(stringResource(R.string.new_playlist)) }
            }
            items(Playlists.sorted(playlists.filterNot { it.readOnly }), key = { it.id }) { p ->
                val inIt = songId in p.songIds
                ListItem(
                    onClick = { if (inIt) actions.removeSongFromPlaylist(p.id, songId) else actions.addToPlaylist(p.id, listOf(songId)) },
                    leadingContent = { Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = null) },
                    trailingContent = { Checkbox(checked = inIt, onCheckedChange = null) },
                ) { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A playlist's picture: its first song's album art, or a playlist icon when it's empty. */
@Composable
private fun PlaylistArt(
    playlist: Playlist,
    byId: Map<Long, Song>,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val album = playlist.songIds.firstNotNullOfOrNull { byId[it]?.albumId }
    if (album != null) {
        AlbumArt(album, size = size, modifier = modifier)
    } else {
        Box(
            modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_queue_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    byId: Map<Long, Song>,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "new", span = { GridItemSpan(maxLineSpan) }) {
            Row {
                FilledTonalButton(onClick = onNew) {
                    Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.new_playlist), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        gridItems(playlists, key = { it.id }) { p ->
            Surface(onClick = { onOpen(p.id) }, shape = MaterialTheme.shapes.medium) {
                Column {
                    PlaylistArt(p, byId, 160.dp, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        p.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        pluralStringResource(R.plurals.song_count, p.songIds.size, p.songIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
