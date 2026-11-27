package io.github.michaelmassoni.tessera.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.AskForLocalNetwork
import io.github.michaelmassoni.tessera.core.settings.LocalNetwork
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.music.data.Connectivity
import io.github.michaelmassoni.tessera.music.data.DownloadStore
import io.github.michaelmassoni.tessera.music.data.Downloads
import io.github.michaelmassoni.tessera.music.data.LikeStore
import io.github.michaelmassoni.tessera.music.data.LyricsQuery
import io.github.michaelmassoni.tessera.music.data.LyricsRepository
import io.github.michaelmassoni.tessera.music.data.MusicRepository
import io.github.michaelmassoni.tessera.music.data.MusicSettings
import io.github.michaelmassoni.tessera.music.data.PlayHistory
import io.github.michaelmassoni.tessera.music.engine.Library
import io.github.michaelmassoni.tessera.music.engine.Offline
import io.github.michaelmassoni.tessera.music.engine.OfflineKind
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.SubsonicClient
import io.github.michaelmassoni.tessera.music.ui.LibraryActions
import io.github.michaelmassoni.tessera.music.ui.LibraryState
import io.github.michaelmassoni.tessera.music.ui.LyricsState
import io.github.michaelmassoni.tessera.music.ui.MusicApp
import io.github.michaelmassoni.tessera.music.ui.PermissionScreen
import io.github.michaelmassoni.tessera.music.ui.PlayerActions
import io.github.michaelmassoni.tessera.music.ui.ServerNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    // Goes up each time the media notification (or Quick Settings player) opens the app: the player opens.
    private var openPlayerRequests by mutableIntStateOf(0)

    private val player by lazy { PlayerConnection(lifecycleScope) }
    private var granted by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var library by mutableStateOf(LibraryState())
    private var lyrics by mutableStateOf<LyricsState>(LyricsState.None(false))
    private val lyricsRepository by lazy { LyricsRepository(applicationContext) }
    private var lyricsJob: Job? = null
    private var lyricsFor = ""
    private var observing = false
    private val store by lazy { ServerStore(this) }
    private var source by mutableStateOf(MusicSource.Local)
    private var lastFetch = 0L

    private val permission =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = load()
        }

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            denied = !ok
            if (ok) onGranted()
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_OPEN_PLAYER) openPlayerRequests++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_PLAYER) openPlayerRequests++
        source = store.source
        granted = hasAudio()
        Connectivity.start(this)
        lifecycleScope.launch(Dispatchers.IO) { Downloads.refresh(this@MainActivity) }
        val libraryActions = LibraryActionsImpl()
        val playerActions = PlayerActionsImpl()
        setTesseraContent {
            SetupGate(musicSetup) {
                // A server account made before Android 17 asked for the local network.
                AskForLocalNetwork(remember(source) { if (source == MusicSource.Server) store.account?.address else null }) { load() }
                // Setup may just have chosen the source.
                LaunchedEffect(Unit) { checkSource() }
                if (source == MusicSource.Local && !granted) {
                    PermissionScreen(
                        denied = denied,
                        onGrant = { request.launch(permission) },
                        onSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
                            )
                        },
                    )
                } else {
                    val playback by player.state.collectAsStateWithLifecycle()
                    val progress = player.progress.collectAsStateWithLifecycle()
                    LaunchedEffect(playback.uri) { loadLyrics(playback) }
                    // A new song started: "Recently played" moves it up.
                    LaunchedEffect(playback.songId) { refreshHistory() }
                    // Likes from the car (the service) show here too.
                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) { LikeStore.read(this@MainActivity) }
                        LikeStore.flow.collect { likes ->
                            if (likes != null &&
                                likes != library.likes
                            ) {
                                library = library.copy(likes = likes)
                            }
                        }
                    }
                    MusicApp(
                        library,
                        playback,
                        { progress.value },
                        libraryActions,
                        playerActions,
                        lyrics = lyrics,
                        openPlayerRequests = openPlayerRequests,
                    )
                }
            }
        }
        if (source == MusicSource.Server) {
            load()
        } else if (granted) {
            onGranted()
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect(this)
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        checkSource()
        if (source == MusicSource.Server) {
            // Back from Settings with a new sign-in, or a while since the last fetch.
            if (library.notice == ServerNotice.SignIn || System.currentTimeMillis() - lastFetch > REFETCH_MS) load()
        } else if (!granted && hasAudio()) {
            granted = true
            onGranted()
        }
    }

    private fun hasAudio() = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Picks up a source changed in setup or Settings: the library starts again from that source. */
    private fun checkSource() {
        val now = store.source
        if (now == source) return
        source = now
        library = LibraryState()
        lastFetch = 0
        if (now == MusicSource.Server) {
            if (observing) contentResolver.unregisterContentObserver(observer)
            observing = false
            load()
        } else {
            granted = hasAudio()
            if (granted) onGranted()
        }
    }

    override fun onDestroy() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    private fun onGranted() {
        if (!observing) {
            contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observing = true
        }
        load()
    }

    private val playlistFile by lazy { File(filesDir, "playlists.json") }

    private fun load() {
        if (source == MusicSource.Server) {
            loadServer()
            return
        }
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) { MusicRepository(contentResolver).songs() }
            // Once loaded, the playlists on screen are the latest (writes are in flight); only read them once.
            val playlists = if (library.loaded) library.playlists else withContext(Dispatchers.IO) { readPlaylists() }
            library =
                withContext(Dispatchers.Default) {
                    state(songs, playlists, fromServer = false)
                }
        }
    }

    /**
     * The server's library: the saved copy at once, then a fresh fetch. When the fetch fails the saved
     * copy stays, with a notice saying why.
     */
    private fun loadServer() {
        lastFetch = System.currentTimeMillis()
        lifecycleScope.launch {
            val account = store.account
            val password = withContext(Dispatchers.IO) { store.password() }
            if (account == null || password == null) {
                library = LibraryState(loaded = true, fromServer = true, notice = ServerNotice.SignIn)
                return@launch
            }
            if (!library.loaded) {
                val saved = withContext(Dispatchers.IO) { ServerLibrary.load(this@MainActivity, account) }
                library =
                    if (saved != null) {
                        withContext(Dispatchers.Default) { state(saved.songs, saved.playlists, fromServer = true) }
                    } else {
                        LibraryState(fromServer = true)
                    }
            }
            try {
                val (songs, playlists) =
                    withContext(Dispatchers.IO) {
                        val client = SubsonicClient(account, password)
                        val songs = client.allSongs()
                        val playlists = client.playlists()
                        ServerLibrary.save(this@MainActivity, account, songs, playlists)
                        // Likes made here (or in the car) that the server hasn't got yet.
                        LikeStore.sync(this@MainActivity, songs, client)
                        // Downloaded playlists follow the server: new songs download, removed ones go.
                        Downloads.sync(this@MainActivity)
                        songs to playlists
                    }
                if (store.source != MusicSource.Server) return@launch
                library = withContext(Dispatchers.Default) { state(songs, playlists, fromServer = true) }
            } catch (e: ServerException) {
                val notice =
                    when {
                        e.problem == ServerProblem.WrongLogin -> ServerNotice.SignIn
                        LocalNetwork.blocks(this@MainActivity, account.address) -> ServerNotice.LocalNetwork
                        else -> ServerNotice.Unreachable
                    }
                library = library.copy(loaded = true, notice = notice)
            }
        }
    }

    private fun refreshHistory() {
        lifecycleScope.launch {
            // The service records the play a moment after the song starts.
            delay(1_000)
            val history = withContext(Dispatchers.IO) { PlayHistory.read(this@MainActivity) }
            if (history != library.history) library = library.copy(history = history)
        }
    }

    /** Fetches the playing song's lyrics (once per song) off the main thread. */
    private fun loadLyrics(playback: PlaybackState) {
        if (playback.uri.isEmpty() || playback.uri == lyricsFor) return
        lyricsFor = playback.uri
        lyricsJob?.cancel()
        lyrics = LyricsState.Loading
        val song = library.byId[playback.songId]
        val query =
            LyricsQuery(
                uri = playback.uri,
                artist = song?.artist ?: playback.artist,
                title = song?.title ?: playback.title,
                album = song?.album ?: playback.album,
                durationMs = song?.durationMillis ?: playback.durationMs,
            )
        lyricsJob =
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { lyricsRepository.lyrics(query) }
                lyrics =
                    found?.let { LyricsState.Found(it) } ?: LyricsState.None(canLookOnline = !MusicSettings(this@MainActivity).onlineLyrics)
            }
    }

    /** The server's ids of songs in the library. */
    private fun serverIds(songIds: List<Long>) = songIds.mapNotNull { library.byId[it]?.uri?.let(Subsonic::songId) }

    /**
     * A playlist change on the server: shown at once, sent, then the server's playlists are read
     * back so what's shown is exactly what's there. A failure says so and shows the server's version.
     */
    private fun editServerPlaylists(
        change: (List<Playlist>) -> List<Playlist>,
        send: SubsonicClient.() -> Unit,
    ) {
        library = library.copy(playlists = change(library.playlists))
        lifecycleScope.launch {
            val account = store.account ?: return@launch
            val password = withContext(Dispatchers.IO) { store.password() } ?: return@launch
            val playlists =
                withContext(Dispatchers.IO) {
                    val client = SubsonicClient(account, password)
                    val sent = runCatching { client.send() }.isSuccess
                    val fresh = runCatching { client.playlists() }.getOrNull()
                    if (fresh != null) ServerLibrary.save(this@MainActivity, account, library.songs, fresh)
                    if (sent) fresh else null
                }
            library =
                if (playlists != null) {
                    library.copy(playlists = playlists)
                } else {
                    library.copy(notice = ServerNotice.Unreachable)
                }
        }
    }

    private fun serverPlaylist(id: Long) =
        library.playlists
            .firstOrNull { it.id == id }
            ?.takeUnless { it.readOnly }
            ?.serverId

    private fun state(
        songs: List<Song>,
        playlists: List<Playlist>,
        fromServer: Boolean,
    ) = LibraryState(
        loaded = true,
        songs = Library.songs(songs),
        albums = Library.albums(songs),
        artists = Library.artists(songs),
        playlists = playlists,
        albumArtists = Library.albumArtists(songs),
        history = PlayHistory.read(this),
        likes = LikeStore.read(this),
        fromServer = fromServer,
    )

    private fun readPlaylists(): List<Playlist> =
        try {
            if (playlistFile.exists()) Playlists.decode(playlistFile.readText()) else emptyList()
        } catch (_: IOException) {
            emptyList()
        }

    /** Applies a change to the playlists on screen at once, then writes them. */
    private fun editPlaylists(change: (List<Playlist>) -> List<Playlist>) {
        val updated = change(library.playlists)
        library = library.copy(playlists = updated)
        val text = Playlists.encode(updated)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val temp = File(playlistFile.path + ".tmp")
                temp.writeText(text)
                temp.renameTo(playlistFile)
            }
        }
    }

    private inner class LibraryActionsImpl : LibraryActions {
        override fun play(
            songs: List<Song>,
            index: Int,
            from: PlayingFrom,
        ) = player.play(songs, index, from = from)

        override fun shuffle(
            songs: List<Song>,
            from: PlayingFrom,
        ) = player.play(songs, 0, shuffle = true, from = from)

        override fun playNext(songs: List<Song>) = player.playNext(songs)

        override fun addToQueue(songs: List<Song>) = player.addToQueue(songs)

        override fun createPlaylist(
            name: String,
            songIds: List<Long>,
        ) = if (source == MusicSource.Server) {
            val ids = serverIds(songIds)
            editServerPlaylists({ it }) { createPlaylist(name.trim(), ids) }
        } else {
            editPlaylists { Playlists.create(it, name, songIds).first }
        }

        override fun renamePlaylist(
            id: Long,
            name: String,
        ) = if (source == MusicSource.Server) {
            serverPlaylist(id)?.let { sid ->
                editServerPlaylists({ Playlists.rename(it, id, name) }) { updatePlaylist(sid, name = name.trim()) }
            }
                ?: Unit
        } else {
            editPlaylists { Playlists.rename(it, id, name) }
        }

        override fun deletePlaylist(id: Long) =
            if (source == MusicSource.Server) {
                serverPlaylist(id)?.let { sid -> editServerPlaylists({ Playlists.delete(it, id) }) { deletePlaylist(sid) } } ?: Unit
            } else {
                editPlaylists { Playlists.delete(it, id) }
            }

        override fun setDownload(
            kind: OfflineKind,
            id: Long,
            on: Boolean,
        ) {
            // The progress notification needs permission on Android 13+; asked the first time.
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
            lifecycleScope.launch(Dispatchers.IO) { Downloads.pin(this@MainActivity, kind, id, on) }
        }

        override fun setLiked(
            songId: Long,
            liked: Boolean,
        ) {
            val song = library.byId[songId] ?: return
            lifecycleScope.launch(Dispatchers.IO) {
                LikeStore.set(this@MainActivity, song, liked)
                // Liked songs downloaded: a newly liked song downloads, an unliked one goes.
                if (DownloadStore(this@MainActivity).pins.has(OfflineKind.Playlist, Offline.LIKED)) Downloads.sync(this@MainActivity)
            }
        }

        override fun addToPlaylist(
            id: Long,
            songIds: List<Long>,
        ) = if (source == MusicSource.Server) {
            val sid = serverPlaylist(id)
            // Only songs not already there, as on the phone.
            val existing =
                library.playlists
                    .firstOrNull { it.id == id }
                    ?.songIds
                    .orEmpty()
            val added = serverIds(songIds.filterNot { it in existing }.distinct())
            if (sid != null &&
                added.isNotEmpty()
            ) {
                editServerPlaylists({ Playlists.add(it, id, songIds) }) { updatePlaylist(sid, add = added) }
            } else {
                Unit
            }
        } else {
            editPlaylists { Playlists.add(it, id, songIds) }
        }

        override fun removeFromPlaylist(
            id: Long,
            index: Int,
        ) = if (source == MusicSource.Server) {
            serverPlaylist(id)?.let { sid ->
                editServerPlaylists({ Playlists.remove(it, id, index) }) { updatePlaylist(sid, removeAt = listOf(index)) }
            } ?: Unit
        } else {
            editPlaylists { Playlists.remove(it, id, index) }
        }

        override fun removeSongFromPlaylist(
            id: Long,
            songId: Long,
        ) {
            val positions =
                library.playlists
                    .firstOrNull {
                        it.id == id
                    }?.songIds
                    ?.withIndex()
                    ?.filter { it.value == songId }
                    ?.map { it.index }
                    .orEmpty()
            if (positions.isEmpty()) return
            val change: (List<Playlist>) -> List<Playlist> = { Playlists.removeSong(it, id, songId) }
            if (source == MusicSource.Server) {
                serverPlaylist(id)?.let { sid -> editServerPlaylists(change) { updatePlaylist(sid, removeAt = positions) } }
            } else {
                editPlaylists(change)
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun retry() = load()
    }

    private inner class PlayerActionsImpl : PlayerActions {
        override fun togglePlay() = player.togglePlay()

        override fun next() {
            player.next()
        }

        override fun previous() {
            player.previous()
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        override fun toggleShuffle() = player.toggleShuffle()

        override fun cycleRepeat() = player.cycleRepeat()

        override fun lookUpLyricsOnline() {
            MusicSettings(this@MainActivity).onlineLyrics = true
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { lyricsRepository.forgetMisses() }
                val now = player.state.value
                lyricsFor = ""
                loadLyrics(now)
            }
        }

        override fun moveQueueItem(
            from: Int,
            to: Int,
        ) {
            player.moveQueueItem(from, to)
        }

        override fun removeQueueItem(index: Int) {
            player.removeQueueItem(index)
        }

        override fun skipTo(index: Int) {
            player.skipTo(index)
        }
    }

    internal companion object {
        const val ACTION_OPEN_PLAYER = "io.github.michaelmassoni.tessera.music.OPEN_PLAYER"

        // Opening the app fetches the server library again at most this often.
        const val REFETCH_MS = 10 * 60 * 1000L
    }
}
