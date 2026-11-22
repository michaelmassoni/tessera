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
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.ui.LibraryActions
import io.github.michaelmassoni.tessera.music.ui.LibraryState
import io.github.michaelmassoni.tessera.music.ui.MusicApp
import io.github.michaelmassoni.tessera.music.ui.PermissionScreen
import io.github.michaelmassoni.tessera.music.ui.PlayerActions
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
        granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        val libraryActions = LibraryActionsImpl()
        val playerActions = PlayerActionsImpl()
        setTesseraContent {
            SetupGate(musicSetup) {
                if (!granted) {
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
        if (granted) onGranted()
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
        if (!granted && ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            granted = true
            onGranted()
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
}
