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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.music.data.MusicRepository
import io.github.michaelmassoni.tessera.music.engine.Library
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.SubsonicClient
import io.github.michaelmassoni.tessera.music.ui.LibraryActions
import io.github.michaelmassoni.tessera.music.ui.LibraryState
import io.github.michaelmassoni.tessera.music.ui.MusicApp
import io.github.michaelmassoni.tessera.music.ui.PermissionScreen
import io.github.michaelmassoni.tessera.music.ui.PlayerActions
import io.github.michaelmassoni.tessera.music.ui.ServerNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val player by lazy { PlayerConnection(lifecycleScope) }
    private var granted by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var library by mutableStateOf(LibraryState())
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = store.source
        granted = hasAudio()
        val libraryActions = LibraryActionsImpl()
        val playerActions = PlayerActionsImpl()
        setTesseraContent {
            SetupGate(musicSetup) {
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
                    MusicApp(library, playback, { progress.value }, libraryActions, playerActions)
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
                    LibraryState(true, Library.songs(songs), Library.albums(songs), Library.artists(songs), playlists)
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
            val playlists = if (library.loaded) library.playlists else withContext(Dispatchers.IO) { readPlaylists() }
            if (account == null || password == null) {
                library = LibraryState(loaded = true, fromServer = true, playlists = playlists, notice = ServerNotice.SignIn)
                return@launch
            }
            if (!library.loaded) {
                withContext(Dispatchers.IO) { ServerLibrary.load(this@MainActivity, account) }?.let { saved ->
                    library = withContext(Dispatchers.Default) { state(saved, playlists, fromServer = true) }
                } ?: run { library = LibraryState(fromServer = true, playlists = playlists) }
            }
            try {
                val songs =
                    withContext(Dispatchers.IO) {
                        SubsonicClient(account, password).allSongs().also { ServerLibrary.save(this@MainActivity, account, it) }
                    }
                if (store.source != MusicSource.Server) return@launch
                library = withContext(Dispatchers.Default) { state(songs, library.playlists, fromServer = true) }
            } catch (e: ServerException) {
                val notice = if (e.problem == ServerProblem.WrongLogin) ServerNotice.SignIn else ServerNotice.Unreachable
                library = library.copy(loaded = true, notice = notice)
            }
        }
    }

    private fun state(
        songs: List<Song>,
        playlists: List<Playlist>,
        fromServer: Boolean,
    ) = LibraryState(true, Library.songs(songs), Library.albums(songs), Library.artists(songs), playlists, fromServer = fromServer)

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
        ) = player.play(songs, index)

        override fun shuffle(songs: List<Song>) = player.play(songs, 0, shuffle = true)

        override fun createPlaylist(
            name: String,
            songIds: List<Long>,
        ) = editPlaylists { Playlists.create(it, name, songIds).first }

        override fun renamePlaylist(
            id: Long,
            name: String,
        ) = editPlaylists { Playlists.rename(it, id, name) }

        override fun deletePlaylist(id: Long) = editPlaylists { Playlists.delete(it, id) }

        override fun addToPlaylist(
            id: Long,
            songIds: List<Long>,
        ) = editPlaylists { Playlists.add(it, id, songIds) }

        override fun removeFromPlaylist(
            id: Long,
            index: Int,
        ) = editPlaylists { Playlists.remove(it, id, index) }

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

        override fun skipTo(index: Int) {
            player.skipTo(index)
        }
    }

    private companion object {
        // Opening the app fetches the server library again at most this often.
        const val REFETCH_MS = 10 * 60 * 1000L
    }
}
