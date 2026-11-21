package io.github.michaelmassoni.tessera.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.Album
import io.github.michaelmassoni.tessera.music.engine.Artist
import io.github.michaelmassoni.tessera.music.engine.Library
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.formatDuration
import io.github.michaelmassoni.tessera.core.design.R as DesignR

data class LibraryState(
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)

/** Library-level actions. */
interface LibraryActions {
    fun play(
        songs: List<Song>,
        index: Int,
    )

    fun shuffle(songs: List<Song>)

    fun openSettings()
}

private enum class Tab(
    val label: Int,
    val icon: Int,
) {
    Songs(R.string.tab_songs, DesignR.drawable.ic_music_note),
    Albums(R.string.tab_albums, DesignR.drawable.ic_album),
    Artists(R.string.tab_artists, DesignR.drawable.ic_artist),
}

private sealed interface Page {
    data class AlbumPage(
        val id: Long,
    ) : Page

    data class ArtistPage(
        val id: Long,
    ) : Page
}

@Composable
fun MusicApp(
    library: LibraryState,
    playback: PlaybackState,
    libraryActions: LibraryActions,
    playerActions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<Page?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(playerOpen) { playerOpen = false }
    BackHandler(!playerOpen && page != null) { page = null }

    if (playerOpen && playback.hasMedia) {
        FullPlayer(playback, playerActions, onClose = { playerOpen = false }, modifier = modifier)
        return
    }
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = tab == t.ordinal && page == null,
                    onClick = {
                        tab = t.ordinal
                        page = null
                    },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(stringResource(t.label)) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val p = page) {
                    is Page.AlbumPage -> {
                        library.albums.firstOrNull { it.id == p.id }?.let { AlbumScreen(it, libraryActions) { page = null } }
                    }

                    is Page.ArtistPage -> {
                        library.artists.firstOrNull { it.id == p.id }?.let {
                            ArtistScreen(it, libraryActions, { page = Page.AlbumPage(it) }) {
                                page =
                                    null
                            }
                        }
                    }

                    null -> {
                        LibraryScreen(
                            Tab.entries[tab],
                            library,
                            libraryActions,
                            { page = Page.AlbumPage(it) },
                            { page = Page.ArtistPage(it) },
                        )
                    }
                }
            }
            if (playback.hasMedia) MiniPlayer(playback, playerActions, onOpen = { playerOpen = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    tab: Tab,
    library: LibraryState,
    actions: LibraryActions,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            val text = rememberTextFieldState()
            val searchState = rememberSearchBarState()
            LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect { query = it } }
            SearchBar(
                state = searchState,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                inputField = {
                    SearchBarDefaults.InputField(
                        textFieldState = text,
                        searchBarState = searchState,
                        onSearch = {},
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = actions::openSettings) {
                                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                            }
                        },
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !library.loaded -> {}

                library.songs.isEmpty() -> {
                    Empty(stringResource(R.string.empty_library))
                }

                query.isNotBlank() -> {
                    SongList(
                        remember(library.songs, query) { Library.search(library.songs, query) },
                        actions,
                        showShuffle = false,
                    )
                }

                tab == Tab.Songs -> {
                    SongList(library.songs, actions, showShuffle = true)
                }

                tab == Tab.Albums -> {
                    AlbumGrid(library.albums, onAlbum)
                }

                else -> {
                    ArtistList(library.artists, onArtist)
                }
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    actions: LibraryActions,
    showShuffle: Boolean,
    header: (LazyListScope.() -> Unit)? = null,
    showArt: Boolean = true,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        header?.invoke(this)
        if (showShuffle && songs.isNotEmpty()) {
            item(key = "shuffle") {
                FilledTonalButton(onClick = { actions.shuffle(songs) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null)
                    Text(stringResource(R.string.shuffle_all), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
            val description = "${song.title}, $artist, ${formatDuration(song.durationMillis)}"
            ListItem(
                onClick = { actions.play(songs, i) },
                leadingContent = if (showArt) ({ AlbumArt(song.albumId) }) else null,
                supportingContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = { Text(formatDuration(song.durationMillis), style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.padding(horizontal = 8.dp).semantics(mergeDescendants = true) { contentDescription = description },
            ) { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    onAlbum: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Surface(onClick = { onAlbum(album.id) }, shape = MaterialTheme.shapes.medium) {
                Column {
                    AlbumArt(album.id, size = 160.dp, modifier = Modifier.fillMaxWidth())
                    Text(
                        album.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<Artist>,
    onArtist: (Long) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(artists, key = { _, a -> a.id }) { _, artist ->
            ListItem(
                onClick = { onArtist(artist.id) },
                leadingContent = { AlbumArt(artist.albums.firstOrNull()?.id ?: 0) },
                supportingContent = {
                    Text(
                        pluralStringResource(R.plurals.album_count, artist.albums.size, artist.albums.size) + " · " +
                            pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(artist.name.ifBlank { stringResource(R.string.unknown_artist) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumScreen(
    album: Album,
    actions: LibraryActions,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { BackBar(album.title, onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(album.songs, actions, showShuffle = false, showArt = false, header = {
                item(key = "header") {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AlbumArt(album.id, size = 200.dp)
                        Text(album.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(
                            listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { actions.play(album.songs, 0) }) {
                            Icon(painterResource(DesignR.drawable.ic_play_arrow_filled), contentDescription = null)
                            Text(stringResource(R.string.play), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistScreen(
    artist: Artist,
    actions: LibraryActions,
    onAlbum: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { BackBar(artist.name, onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(artist.songs, actions, showShuffle = true, header = {
                item(key = "albums") {
                    Text(
                        stringResource(R.string.tab_albums),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                artist.albums.forEach { album ->
                    item(key = "a${album.id}") {
                        ListItem(
                            onClick = { onAlbum(album.id) },
                            leadingContent = { AlbumArt(album.id, size = 56.dp) },
                            supportingContent = {
                                Text(
                                    album.year
                                        .takeIf { it > 0 }
                                        ?.toString()
                                        .orEmpty(),
                                )
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text(album.title) }
                    }
                }
                item(key = "songs") {
                    Text(
                        stringResource(R.string.tab_songs),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            })
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
            IconButton(
                onClick = onBack,
            ) { Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
        },
    )
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(DesignR.drawable.ic_library_music),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shown until audio access is granted. */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_library_music),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = if (denied) onSettings else onGrant) {
                    Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_grant))
                }
            }
        }
    }
}
