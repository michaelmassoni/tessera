package io.github.michaelmassoni.tessera.music.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.github.michaelmassoni.tessera.core.cast.CastButton
import io.github.michaelmassoni.tessera.core.cast.castingTo
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import io.github.michaelmassoni.tessera.core.design.TesseraSearchTopBar
import io.github.michaelmassoni.tessera.core.design.keyboardVisible
import io.github.michaelmassoni.tessera.core.settings.LocalNetwork
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.Progress
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.data.Connectivity
import io.github.michaelmassoni.tessera.music.data.Downloads
import io.github.michaelmassoni.tessera.music.data.MusicSettings
import io.github.michaelmassoni.tessera.music.engine.Album
import io.github.michaelmassoni.tessera.music.engine.Artist
import io.github.michaelmassoni.tessera.music.engine.Groups
import io.github.michaelmassoni.tessera.music.engine.Library
import io.github.michaelmassoni.tessera.music.engine.LikeChange
import io.github.michaelmassoni.tessera.music.engine.Likes
import io.github.michaelmassoni.tessera.music.engine.Lyrics
import io.github.michaelmassoni.tessera.music.engine.Offline
import io.github.michaelmassoni.tessera.music.engine.OfflineKind
import io.github.michaelmassoni.tessera.music.engine.PageKeys
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.Sorts
import io.github.michaelmassoni.tessera.music.engine.formatDuration
import io.github.michaelmassoni.tessera.music.engine.sourceId
import io.github.michaelmassoni.tessera.core.design.R as DesignR
import io.github.michaelmassoni.tessera.core.settings.R as SettingsR

data class LibraryState(
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    /** Artists by album artist (see [Library.albumArtists]). */
    val albumArtists: List<Artist> = emptyList(),
    /** When songs were last played on this phone, for "Recently played". */
    val history: Map<Long, Long> = emptyMap(),
    /** This phone's likes (see [Likes]): with each song's server star, what's liked. */
    val likes: Map<Long, LikeChange> = emptyMap(),
    /** Songs from a server (an empty library says so). */
    val fromServer: Boolean = false,
    /** Something wrong with the server, shown above the library. */
    val notice: ServerNotice? = null,
) {
    val byId: Map<Long, Song> by lazy { songs.associateBy { it.id } }
}

/** Why the server library may be out of date. */
enum class ServerNotice {
    /** The password stopped working (or isn't on this phone): sign in again in Settings. */
    SignIn,

    /** No answer: the saved library and cached songs still play. */
    Unreachable,

    /** No answer because Nearby devices isn't allowed and the server is on the local network. */
    LocalNetwork,
}

/** Library-level actions. */
interface LibraryActions {
    /** Whether [song] is liked (its server star or a like made here), for the heart in song lists. */
    fun isLiked(song: Song): Boolean = false

    /** Turns downloading for offline listening on or off for an album, playlist or Liked songs. */
    fun setDownload(
        kind: OfflineKind,
        id: Long,
        on: Boolean,
    ) {}

    fun play(
        songs: List<Song>,
        index: Int,
        from: PlayingFrom,
    )

    fun shuffle(
        songs: List<Song>,
        from: PlayingFrom,
    )

    /** Puts songs right after the one playing. */
    fun playNext(songs: List<Song>) {}

    /** Puts songs at the end of the queue. */
    fun addToQueue(songs: List<Song>) {}

    fun createPlaylist(
        name: String,
        songIds: List<Long>,
    )

    fun renamePlaylist(
        id: Long,
        name: String,
    )

    fun deletePlaylist(id: Long)

    fun addToPlaylist(
        id: Long,
        songIds: List<Long>,
    )

    fun removeFromPlaylist(
        id: Long,
        index: Int,
    )

    /** Takes a song out of a playlist, wherever it is in it. */
    fun removeSongFromPlaylist(
        id: Long,
        songId: Long,
    ) {}

    /** The heart: likes or unlikes a song (a star on the server). */
    fun setLiked(
        songId: Long,
        liked: Boolean,
    ) {}

    fun openSettings()

    /** Fetch the server library again. */
    fun retry() {}
}

private enum class Tab(
    val label: Int,
    val icon: Int,
) {
    Songs(R.string.tab_songs, DesignR.drawable.ic_music_note),
    Albums(R.string.tab_albums, DesignR.drawable.ic_album),
    Artists(R.string.tab_artists, DesignR.drawable.ic_artist),
    Playlists(R.string.tab_playlists, DesignR.drawable.ic_queue_music),
}

private sealed interface Page {
    data class AlbumPage(
        val id: Long,
    ) : Page

    data class ArtistPage(
        val id: Long,
    ) : Page

    data class PlaylistPage(
        val id: Long,
    ) : Page

    /** All genres. */
    data object GenresPage : Page

    /** One genre, by [io.github.michaelmassoni.tessera.music.engine.SongGroup.id]. */
    data class GenrePage(
        val id: Long,
    ) : Page
}

/** Where the library is: a tab, and the pages opened from it (album, artist, playlist). */
private data class Screen(
    val tab: Int,
    val stack: List<Page> = emptyList(),
) {
    /** The saved-state key of the tab and of each page on it; the last is the one shown. */
    val keys: List<String>
        get() =
            PageKeys.levels(
                "tab:$tab",
                stack.map {
                    when (it) {
                        is Page.AlbumPage -> "album:${it.id}"
                        is Page.ArtistPage -> "artist:${it.id}"
                        is Page.PlaylistPage -> "playlist:${it.id}"
                        is Page.GenresPage -> "genres"
                        is Page.GenrePage -> "genre:${it.id}"
                    }
                },
            )

    companion object {
        // Kept across rotation: the tab, then each page as its kind (0 album, 1 artist, 2 playlist) and id.
        val Saver =
            listSaver<Screen, Long>(
                save = { s ->
                    listOf(s.tab.toLong()) +
                        s.stack.flatMap { p ->
                            when (p) {
                                is Page.AlbumPage -> listOf(0L, p.id)
                                is Page.ArtistPage -> listOf(1L, p.id)
                                is Page.PlaylistPage -> listOf(2L, p.id)
                                is Page.GenresPage -> listOf(3L, 0L)
                                is Page.GenrePage -> listOf(5L, p.id)
                            }
                        }
                },
                restore = { saved ->
                    Screen(
                        saved.first().toInt(),
                        saved.drop(1).chunked(2).map { (kind, id) ->
                            when (kind) {
                                0L -> Page.AlbumPage(id)
                                1L -> Page.ArtistPage(id)
                                3L -> Page.GenresPage
                                5L -> Page.GenrePage(id)
                                else -> Page.PlaylistPage(id)
                            }
                        },
                    )
                },
            )
    }
}

/**
 * How the library pages are sorted and shown, remembered between visits. Changes are saved as
 * they're made.
 */
@Stable
private class LibraryView(
    private val settings: MusicSettings,
) {
    var songSort by mutableStateOf(settings.sort("songs", Sorts.songs.first()))
    var albumSort by mutableStateOf(settings.sort("albums", Sorts.albums.first()))
    var artistSort by mutableStateOf(settings.sort("artists", Sorts.artists.first()))
    var playlistSort by mutableStateOf(settings.sort("playlists", Sorts.playlists.first()))
    var albumGrid by mutableStateOf(settings.grid("albums", true))
    var artistGrid by mutableStateOf(settings.grid("artists", false))
    var playlistGrid by mutableStateOf(settings.grid("playlists", false))
    var albumArtists by mutableStateOf(settings.albumArtists)

    fun save() {
        settings.setSort("songs", songSort)
        settings.setSort("albums", albumSort)
        settings.setSort("artists", artistSort)
        settings.setSort("playlists", playlistSort)
        settings.setGrid("albums", albumGrid)
        settings.setGrid("artists", artistGrid)
        settings.setGrid("playlists", playlistGrid)
        settings.albumArtists = albumArtists
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MusicApp(
    library: LibraryState,
    playback: PlaybackState,
    sampledProgress: () -> Progress,
    libraryActions: LibraryActions,
    playerActions: PlayerActions,
    modifier: Modifier = Modifier,
    // Goes up when the app is opened from the media notification: the player opens.
    openPlayerRequests: Int = 0,
    lyrics: LyricsState = LyricsState.None(false),
) {
    val context = LocalContext.current
    val progress = rememberSmoothProgress(sampledProgress)
    val view = remember { LibraryView(MusicSettings(context)) }
    var screen by rememberSaveable(stateSaver = Screen.Saver) { mutableStateOf(Screen(0)) }
    // Each tab and page keeps its scroll position while another is shown; a page closed for good forgets it.
    val saveable = rememberSaveableStateHolder()

    fun navigate(to: Screen) {
        PageKeys.closed(screen.keys, to.keys).forEach(saveable::removeState)
        screen = to
    }
    // How far a predictive back gesture has gone on a page, 0 to 1.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = screen.stack.isNotEmpty()) { events ->
        try {
            events.collect { backProgress = it.progress }
            navigate(screen.copy(stack = screen.stack.dropLast(1)))
        } finally {
            backProgress = 0f
        }
    }

    // Genres, worked out once per library load.
    val genres = remember(library.songs) { Groups.genres(library.songs) }

    fun open(page: Page) {
        // Already there (Go to album from that album's page): stay.
        if (screen.stack.lastOrNull() != page) navigate(screen.copy(stack = screen.stack + page))
    }
    val openAlbum: (Long) -> Unit = { open(Page.AlbumPage(it)) }
    val openArtist: (Long) -> Unit = { open(Page.ArtistPage(it)) }
    // The pages the full player links to.
    val playingAlbum =
        remember(playback.songId, playback.albumId, library.albums) {
            Library.albumOf(library.byId[playback.songId], playback.albumId, library.albums)?.id
        }
    val playingArtist =
        remember(playback.songId, playback.artist, library.artists) {
            Library.artistOf(library.byId[playback.songId], playback.artist, library.artists)?.id
        }
    val playingSource =
        remember(playback.from, library.albums, library.artists, library.albumArtists, library.playlists) {
            playback.from?.sourceId(library.albums, library.albumArtists + library.artists, library.playlists)
        }
    val back: () -> Unit = { navigate(screen.copy(stack = screen.stack.dropLast(1))) }

    PlayerSheet(
        hasMedia = playback.hasMedia,
        modifier = modifier,
        openRequests = openPlayerRequests,
        player = { onClose, sheetModifier ->
            FullPlayer(
                playback,
                progress,
                playerActions,
                onClose = onClose,
                modifier = sheetModifier,
                lyrics = lyrics,
                playlists = library.playlists,
                libraryActions = libraryActions,
                explicit = library.byId[playback.songId]?.explicit == true,
                liked = library.byId[playback.songId]?.let { Likes.isLiked(it, library.likes) },
                castButton = { CastButton() },
                castingTo = castingTo(),
                links =
                    NowPlayingLinks(
                        onAlbum =
                            playingAlbum?.let { id ->
                                {
                                    onClose()
                                    openAlbum(id)
                                }
                            },
                        onArtist =
                            playingArtist?.let { id ->
                                {
                                    onClose()
                                    openArtist(id)
                                }
                            },
                        onSource =
                            playback.from?.let { from ->
                                val go: ((Long) -> Unit)? =
                                    when (from.kind) {
                                        PlayingFrom.Kind.Album -> openAlbum
                                        PlayingFrom.Kind.Artist -> openArtist
                                        PlayingFrom.Kind.Playlist -> { id -> open(Page.PlaylistPage(id)) }
                                        PlayingFrom.Kind.Songs -> { _ -> navigate(Screen(Tab.Songs.ordinal)) }
                                        PlayingFrom.Kind.Search -> null
                                        PlayingFrom.Kind.Genre -> { id -> open(Page.GenrePage(id)) }
                                    }
                                val id = if (from.kind == PlayingFrom.Kind.Songs) 0L else playingSource
                                if (go != null && id != null) {
                                    {
                                        onClose()
                                        go(id)
                                    }
                                } else {
                                    null
                                }
                            },
                    ),
            )
        },
    ) { openPlayer, dragUp ->
        // A phone on its side is short: a rail beside the library leaves it the height a bottom bar would take.
        val adaptive = currentWindowAdaptiveInfoV2()
        val layout =
            if (adaptive.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptive)
            } else {
                NavigationSuiteType.NavigationRail
            }
        NavigationSuiteScaffold(
            layoutType = layout,
            navigationSuiteItems = {
                Tab.entries.forEach { t ->
                    item(
                        selected = screen.tab == t.ordinal && screen.stack.isEmpty(),
                        onClick = { navigate(Screen(t.ordinal)) },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                SharedTransitionLayout(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = screen,
                        // The same key as the saved state, so a page is never composed twice during a transition.
                        contentKey = { it.keys.last() },
                        transitionSpec = {
                            val deeper = targetState.stack.size > initialState.stack.size
                            val shallower = targetState.stack.size < initialState.stack.size
                            when {
                                // Opening a page: it slides in a little from the end and fades in.
                                deeper -> {
                                    (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150))
                                }

                                // Back: the page slides away and the one under it fades back.
                                shallower -> {
                                    fadeIn(tween(250)) togetherWith (slideOutHorizontally(tween(250)) { it / 5 } + fadeOut(tween(200)))
                                }

                                // Another tab: a quick cross-fade.
                                else -> {
                                    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90))
                                }
                            }
                        },
                        label = "library",
                        modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                // Predictive back: the page shrinks and moves with the gesture.
                                val p = backProgress
                                scaleX = 1f - 0.08f * p
                                scaleY = 1f - 0.08f * p
                                translationX = p * 32.dp.toPx()
                            },
                    ) { s ->
                        CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalPageScope provides this) {
                            saveable.SaveableStateProvider(s.keys.last()) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                    when (val p = s.stack.lastOrNull()) {
                                        is Page.AlbumPage -> {
                                            library.albums.firstOrNull { it.id == p.id }?.let {
                                                AlbumScreen(
                                                    it,
                                                    library.playlists,
                                                    libraryActions,
                                                    openArtist,
                                                    back,
                                                    playingId = playback.songId,
                                                    playing = playback.isPlaying,
                                                )
                                            }
                                        }

                                        is Page.ArtistPage -> {
                                            (library.albumArtists + library.artists).firstOrNull { it.id == p.id }?.let {
                                                ArtistScreen(
                                                    it,
                                                    library.playlists,
                                                    libraryActions,
                                                    openAlbum,
                                                    back,
                                                    playingId = playback.songId,
                                                    playing = playback.isPlaying,
                                                )
                                            }
                                        }

                                        is Page.PlaylistPage -> {
                                            val likedName = stringResource(R.string.liked_songs)
                                            val liked =
                                                remember(
                                                    library.songs,
                                                    library.likes,
                                                    library.fromServer,
                                                    likedName,
                                                ) { library.likedPlaylist(likedName) }
                                            (listOfNotNull(liked) + library.playlists).firstOrNull { it.id == p.id }?.let { playlist ->
                                                PlaylistScreen(
                                                    playlist,
                                                    Playlists.songs(playlist, library.byId),
                                                    library.playlists,
                                                    libraryActions,
                                                    back,
                                                    onAlbum = openAlbum,
                                                    onArtist = openArtist,
                                                    playingId = playback.songId,
                                                    playing = playback.isPlaying,
                                                )
                                            }
                                        }

                                        is Page.GenresPage -> {
                                            GenresScreen(
                                                genres,
                                                { open(Page.GenrePage(it)) },
                                                back,
                                            )
                                        }

                                        is Page.GenrePage -> {
                                            genres.firstOrNull { it.id == p.id }?.let {
                                                GenreScreen(
                                                    it,
                                                    library.playlists,
                                                    libraryActions,
                                                    back,
                                                    onAlbum = openAlbum,
                                                    onArtist = openArtist,
                                                    playingId = playback.songId,
                                                    playing = playback.isPlaying,
                                                )
                                            }
                                        }

                                        null -> {
                                            LibraryScreen(
                                                Tab.entries[s.tab],
                                                library,
                                                view,
                                                libraryActions,
                                                openAlbum,
                                                openArtist,
                                                { open(Page.PlaylistPage(it)) },
                                                { open(Page.GenresPage) },
                                                playingId = playback.songId,
                                                playing = playback.isPlaying,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (playback.hasMedia) MiniPlayer(playback, progress, playerActions, onOpen = openPlayer, modifier = dragUp)
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    tab: Tab,
    library: LibraryState,
    view: LibraryView,
    actions: LibraryActions,
    onAlbum: (Long) -> Unit,
    onArtist: (Long) -> Unit,
    onPlaylist: (Long) -> Unit,
    onGenres: () -> Unit,
    playingId: Long = 0,
    // The current song is playing (not paused): its indicator moves.
    playing: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf<Song?>(null) }
    options?.let { song ->
        SongOptions(song, library.playlists, actions, onDismiss = { options = null }, onAlbum = onAlbum, onArtist = onArtist)
    }
    val onLongClick: (Int, Song) -> Unit = { _, song -> options = song }
    // Back clears the search (not while the keyboard is up: Back closes it first).
    BackHandler(query.isNotEmpty() && !keyboardVisible()) { query = "" }
    Scaffold(
        topBar = {
            // Each tab searches its own kind: songs, albums, artists or playlists.
            TesseraSearchTopBar(
                query = query,
                onQuery = { query = it },
                placeholder =
                    stringResource(
                        when (tab) {
                            Tab.Songs -> R.string.search_songs
                            Tab.Albums -> R.string.search_albums
                            Tab.Artists -> R.string.search_artists
                            Tab.Playlists -> R.string.search_playlists
                        },
                    ),
                action = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        val downloads by Downloads.state.collectAsState()
        val online by Connectivity.online.collectAsState()
        // The Downloaded chip shows once something is downloaded (or downloading).
        val anyDownloads = library.fromServer && (downloads.onPhone.isNotEmpty() || !downloads.pins.isEmpty)
        var downloadedOnlyChoice by rememberSaveable { mutableStateOf(false) }
        val downloadedOnly = anyDownloads && downloadedOnlyChoice
        val downloadedChip: @Composable () -> Unit = {
            if (anyDownloads) DownloadedChip(downloadedOnly) { downloadedOnlyChoice = !downloadedOnlyChoice }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            library.notice?.let { Notice(it, actions) }
            if (library.fromServer && !online) OfflineNotice()
            val browsing = library.loaded && library.songs.isNotEmpty() && query.isBlank()
            if (browsing) {
                fun change(block: () -> Unit) {
                    block()
                    view.save()
                }
                when (tab) {
                    Tab.Songs -> {
                        SortBar(Sorts.songs, view.songSort, { change { view.songSort = it } }) {
                            AssistChip(onClick = onGenres, label = { Text(stringResource(R.string.genres)) })
                            downloadedChip()
                        }
                    }

                    Tab.Albums -> {
                        SortBar(Sorts.albums, view.albumSort, { change { view.albumSort = it } }, grid = view.albumGrid, onGrid = {
                            change { view.albumGrid = it }
                        }) { downloadedChip() }
                    }

                    Tab.Artists -> {
                        SortBar(
                            Sorts.artists,
                            view.artistSort,
                            { change { view.artistSort = it } },
                            grid = view.artistGrid,
                            onGrid = { change { view.artistGrid = it } },
                        ) { AlbumArtistsChip(view.albumArtists) { change { view.albumArtists = !view.albumArtists } } }
                    }

                    Tab.Playlists -> {
                        SortBar(Sorts.playlists, view.playlistSort, {
                            change { view.playlistSort = it }
                        }, grid = view.playlistGrid, onGrid = {
                            change { view.playlistGrid = it }
                        }) { downloadedChip() }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !library.loaded -> {
                        // Only a first server fetch takes long enough to see.
                        if (library.fromServer) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    library.songs.isEmpty() -> {
                        Empty(stringResource(if (library.fromServer) R.string.empty_server else R.string.empty_library))
                    }

                    query.isNotBlank() -> {
                        // Results of the tab's own kind, shown the way the tab shows them (list or grid).
                        when (tab) {
                            Tab.Songs -> {
                                val songs = remember(library.songs, query) { Library.search(library.songs, query) }
                                if (songs.isEmpty()) {
                                    Empty(stringResource(R.string.no_results))
                                } else {
                                    SongList(
                                        songs,
                                        actions,
                                        PlayingFrom(PlayingFrom.Kind.Search, query),
                                        showShuffle = false,
                                        onLongClick = onLongClick,
                                        playingId = playingId,
                                        playing = playing,
                                    )
                                }
                            }

                            Tab.Albums -> {
                                val albums = remember(library.albums, query) { Library.searchAlbums(library.albums, query) }
                                when {
                                    albums.isEmpty() -> Empty(stringResource(R.string.no_results))
                                    view.albumGrid -> AlbumGrid(albums, onAlbum)
                                    else -> AlbumList(albums, onAlbum)
                                }
                            }

                            Tab.Artists -> {
                                val source = if (view.albumArtists) library.albumArtists else library.artists
                                val artists = remember(source, query) { Library.searchArtists(source, query) }
                                when {
                                    artists.isEmpty() -> Empty(stringResource(R.string.no_results))
                                    view.artistGrid -> ArtistGrid(artists, onArtist)
                                    else -> ArtistList(artists, onArtist)
                                }
                            }

                            Tab.Playlists -> {
                                val playlists = remember(library.playlists, query) { Library.searchPlaylists(library.playlists, query) }
                                if (playlists.isEmpty()) {
                                    Empty(stringResource(R.string.no_results))
                                } else {
                                    PlaylistList(playlists, library.byId, view.playlistGrid, actions, onPlaylist)
                                }
                            }
                        }
                    }

                    tab == Tab.Songs -> {
                        val songs =
                            remember(
                                library.songs,
                                view.songSort,
                                library.history,
                            ) { Sorts.songs(library.songs, view.songSort, library.history) }.let { all ->
                                if (downloadedOnly) all.filter { Offline.isDownloaded(it, downloads.onPhone) } else all
                            }
                        SongList(
                            songs,
                            actions,
                            PlayingFrom(PlayingFrom.Kind.Songs),
                            showShuffle = true,
                            onLongClick = onLongClick,
                            playingId = playingId,
                            playing = playing,
                        )
                    }

                    tab == Tab.Playlists -> {
                        // Liked songs first (a server library), then the playlists in the chosen order.
                        val likedName = stringResource(R.string.liked_songs)
                        val liked =
                            remember(library.songs, library.likes, library.fromServer, likedName) { library.likedPlaylist(likedName) }
                        val playlists =
                            remember(library.playlists, view.playlistSort, library.history, liked) {
                                listOfNotNull(liked) + Sorts.playlists(library.playlists, view.playlistSort, library.byId, library.history)
                            }.let { all ->
                                if (downloadedOnly) {
                                    all.filter { p ->
                                        downloads.pins.has(OfflineKind.Playlist, p.id) ||
                                            Downloads.complete(Playlists.songs(p, library.byId), downloads)
                                    }
                                } else {
                                    all
                                }
                            }
                        PlaylistList(playlists, library.byId, view.playlistGrid, actions, onPlaylist)
                    }

                    tab == Tab.Albums -> {
                        val albums =
                            remember(
                                library.albums,
                                view.albumSort,
                                library.history,
                            ) { Sorts.albums(library.albums, view.albumSort, library.history) }.let { all ->
                                if (downloadedOnly) {
                                    all.filter { a ->
                                        downloads.pins.has(OfflineKind.Album, a.id) || Downloads.complete(a.songs, downloads)
                                    }
                                } else {
                                    all
                                }
                            }
                        if (view.albumGrid) AlbumGrid(albums, onAlbum) else AlbumList(albums, onAlbum)
                    }

                    else -> {
                        val source = if (view.albumArtists) library.albumArtists else library.artists
                        val artists =
                            remember(source, view.artistSort, library.history) { Sorts.artists(source, view.artistSort, library.history) }
                        if (view.artistGrid) ArtistGrid(artists, onArtist) else ArtistList(artists, onArtist)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SongList(
    songs: List<Song>,
    actions: LibraryActions,
    from: PlayingFrom,
    showShuffle: Boolean,
    header: (LazyListScope.() -> Unit)? = null,
    showArt: Boolean = true,
    onLongClick: ((Int, Song) -> Unit)? = null,
    // The song playing now (its id), shown highlighted wherever it appears.
    playingId: Long = 0,
    // The current song is playing (not paused): its indicator moves.
    playing: Boolean = false,
) {
    val downloads by Downloads.state.collectAsState()
    val online by Connectivity.online.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        header?.invoke(this)
        if (showShuffle && songs.isNotEmpty()) {
            item(key = "shuffle") {
                FilledTonalButton(
                    onClick = { actions.shuffle(songs, from) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null)
                    Text(stringResource(R.string.shuffle_all), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
            val artist = song.artist.ifBlank { stringResource(R.string.unknown_artist) }
            val explicit = if (song.explicit) ", " + stringResource(R.string.explicit) else ""
            val current = playingId != 0L && song.id == playingId
            val nowPlaying = if (current) ", " + stringResource(R.string.now_playing) else ""
            val liked = actions.isLiked(song)
            val likedText = if (liked) ", " + stringResource(R.string.liked) else ""
            val downloaded = Offline.isDownloaded(song, downloads.onPhone)
            // Offline, a server song that isn't downloaded can't play (unless cached): shown faded.
            val unavailable = !online && Offline.serverId(song) != null && !downloaded
            val saved = if (downloaded) ", " + stringResource(R.string.downloaded) else ""
            val description = "${song.title}$explicit, $artist, ${formatDuration(song.durationMillis)}$saved$likedText$nowPlaying"
            ListItem(
                selected = current,
                onClick = { actions.play(songs, i, from) },
                onLongClick = onLongClick?.let { { it(i, song) } },
                onLongClickLabel = onLongClick?.let { stringResource(R.string.song_options) },
                leadingContent = if (showArt) ({ AlbumArt(song.albumId) }) else null,
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (downloaded) {
                            Icon(
                                painterResource(DesignR.drawable.ic_download_done),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            )
                        }
                        WithExplicitBadge(
                            song.explicit,
                            badgeSize = 14.dp,
                        ) { m -> Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m) }
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Liked songs show a heart, as Spotify shows its check.
                        if (liked) {
                            Icon(
                                painterResource(DesignR.drawable.ic_favorite_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(formatDuration(song.durationMillis), style = MaterialTheme.typography.labelMedium)
                    }
                },
                modifier =
                    Modifier
                        .padding(horizontal = 8.dp)
                        .alpha(if (unavailable) UNAVAILABLE_ALPHA else 1f)
                        .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                // The playing song: its moving bars before the title.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (current) NowPlayingBars(playing, Modifier.size(16.dp))
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (current) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumScreen(
    album: Album,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onArtist: (Long) -> Unit,
    onBack: () -> Unit,
    playingId: Long = 0,
    // The current song is playing (not paused): its indicator moves.
    playing: Boolean = false,
) {
    val from = PlayingFrom(PlayingFrom.Kind.Album, album.title, album.id)
    var options by remember { mutableStateOf<Song?>(null) }
    options?.let { song -> SongOptions(song, playlists, actions, onDismiss = { options = null }, onArtist = onArtist) }
    var albumOptions by remember { mutableStateOf(false) }
    if (albumOptions) {
        SongsOptions(
            title = album.title,
            subtitle = album.artist.ifBlank { stringResource(R.string.unknown_artist) },
            songs = album.songs,
            playlists = playlists,
            actions = actions,
            onDismiss = { albumOptions = false },
            onArtist = album.songs.firstOrNull()?.let { s -> { onArtist(s.artistId) } },
            onShuffle = { actions.shuffle(album.songs, from) },
        )
    }
    Scaffold(topBar = { BackBar(album.title, onBack, onMore = { albumOptions = true }) }) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(
                album.songs,
                actions,
                from,
                showShuffle = false,
                showArt = false,
                onLongClick = { _, song -> options = song },
                playingId = playingId,
                playing = playing,
                header = {
                    item(key = "header") {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AlbumArt(album.id, size = 200.dp, modifier = Modifier.sharedArt("album-${album.id}"))
                            Text(album.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Text(
                                listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { actions.play(album.songs, 0, from) }) {
                                    Icon(painterResource(DesignR.drawable.ic_play_arrow_filled), contentDescription = null)
                                    Text(stringResource(R.string.play), modifier = Modifier.padding(start = 8.dp))
                                }
                                FilledTonalButton(onClick = { actions.shuffle(album.songs, from) }) {
                                    Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = null)
                                    Text(stringResource(R.string.shuffle), modifier = Modifier.padding(start = 8.dp))
                                }
                                DownloadToggle(OfflineKind.Album, album.id, album.songs, actions)
                            }
                            DownloadStatus(OfflineKind.Album, album.id, album.songs)
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistScreen(
    artist: Artist,
    playlists: List<Playlist>,
    actions: LibraryActions,
    onAlbum: (Long) -> Unit,
    onBack: () -> Unit,
    playingId: Long = 0,
    // The current song is playing (not paused): its indicator moves.
    playing: Boolean = false,
) {
    var options by remember { mutableStateOf<Song?>(null) }
    options?.let { song -> SongOptions(song, playlists, actions, onDismiss = { options = null }, onAlbum = onAlbum) }
    var artistOptions by remember { mutableStateOf(false) }
    if (artistOptions) {
        SongsOptions(
            title = artist.name.ifBlank { stringResource(R.string.unknown_artist) },
            subtitle = pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
            songs = artist.songs,
            playlists = playlists,
            actions = actions,
            onDismiss = { artistOptions = false },
        )
    }
    Scaffold(topBar = { BackBar(artist.name, onBack, onMore = { artistOptions = true }) }) { padding ->
        Box(Modifier.padding(padding)) {
            SongList(
                artist.songs,
                actions,
                PlayingFrom(PlayingFrom.Kind.Artist, artist.name, artist.id),
                showShuffle = true,
                onLongClick = { _, song -> options = song },
                playingId = playingId,
                playing = playing,
                header = {
                    item(key = "header") {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AlbumArt(
                                artist.albums.firstOrNull()?.id ?: 0,
                                size = 160.dp,
                                modifier = Modifier.clip(CircleShape).sharedArt("artist-${artist.id}"),
                            )
                            Text(
                                artist.name.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
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
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackBar(
    title: String,
    onBack: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(
                onClick = onBack,
            ) { Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
        },
        actions = {
            onMore?.let { more ->
                IconButton(onClick = more) {
                    Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                }
            }
        },
    )
}

@Composable
internal fun Empty(text: String) {
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

/** A server problem above the library, with what to do about it. */
@Composable
private fun Notice(
    notice: ServerNotice,
    actions: LibraryActions,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val context = LocalContext.current
            Text(
                when (notice) {
                    ServerNotice.SignIn -> stringResource(R.string.notice_sign_in)
                    ServerNotice.Unreachable -> stringResource(R.string.notice_unreachable)
                    ServerNotice.LocalNetwork -> stringResource(SettingsR.string.local_network_sync_failed)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            when (notice) {
                ServerNotice.SignIn -> {
                    TextButton(onClick = actions::openSettings) { Text(stringResource(R.string.sign_in)) }
                }

                ServerNotice.Unreachable -> {
                    TextButton(onClick = actions::retry) { Text(stringResource(R.string.retry)) }
                }

                ServerNotice.LocalNetwork -> {
                    TextButton(onClick = { context.startActivity(LocalNetwork.appSettings(context)) }) {
                        Text(stringResource(SettingsR.string.local_network_settings))
                    }
                }
            }
        }
    }
}

/** The lyrics of the song playing, as far as they're known. */
sealed interface LyricsState {
    data object Loading : LyricsState

    data class Found(
        val lyrics: Lyrics,
    ) : LyricsState

    /** None found; [canLookOnline] when looking them up online is off and could find some. */
    data class None(
        val canLookOnline: Boolean,
    ) : LyricsState
}

/**
 * Liked songs as a read-only playlist at the top of Playlists (a server library only), so it can
 * be played and downloaded like one. Its id is [Offline.LIKED].
 */
internal fun LibraryState.likedPlaylist(name: String): Playlist? =
    if (fromServer) Playlist(Offline.LIKED, name, songIds = Likes.songs(songs, likes).map { it.id }, readOnly = true) else null

/** How faded a song that can't play offline is. */
private const val UNAVAILABLE_ALPHA = 0.38f
