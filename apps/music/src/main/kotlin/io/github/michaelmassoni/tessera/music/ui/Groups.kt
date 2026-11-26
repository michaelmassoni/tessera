package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.SongGroup
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Every genre, or every folder (docs/music.md, Genres and folders). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupsScreen(
    groups: List<SongGroup>,
    folders: Boolean,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { BackBar(stringResource(if (folders) R.string.folders else R.string.genres), onBack) }) { padding ->
        if (groups.isEmpty()) {
            Empty(stringResource(if (folders) R.string.no_folders else R.string.no_genres))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(groups, key = { it.id }) { g ->
                ListItem(
                    supportingContent = {
                        val count = pluralStringResource(R.plurals.group_songs, g.songs.size, g.songs.size)
                        // A folder's full path tells apart two folders with the same name.
                        Text(if (folders) "${g.key} · $count" else count, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Icon(
                            painterResource(if (folders) DesignR.drawable.ic_folder else DesignR.drawable.ic_label),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { onOpen(g.id) },
                ) { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** One genre's or folder's songs, with Shuffle; long press for song options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupScreen(
    group: SongGroup,
    folders: Boolean,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onBack: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    playingId: Long = 0,
) {
    var options by remember { mutableStateOf<Song?>(null) }
    options?.let { song ->
        SongOptions(song, playlists, actions, onDismiss = { options = null }, onAlbum = onAlbum, onArtist = onArtist)
    }
    Scaffold(topBar = { BackBar(group.name, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            SongList(
                group.songs,
                actions,
                PlayingFrom(if (folders) PlayingFrom.Kind.Folder else PlayingFrom.Kind.Genre, group.name, group.id),
                showShuffle = true,
                onLongClick = { _, song -> options = song },
                playingId = playingId,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
    )
}
