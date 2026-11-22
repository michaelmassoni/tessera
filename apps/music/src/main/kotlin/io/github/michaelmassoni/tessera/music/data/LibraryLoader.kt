package io.github.michaelmassoni.tessera.music.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Playlists
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import io.github.michaelmassoni.tessera.music.server.ServerStore
import java.io.File

/**
 * The library for the playback service (Android Auto browses it without the app open): the
 * server's saved copy, or the phone's music and playlists. Blocking: call off the main thread.
 */
object LibraryLoader {
    fun load(context: Context): Pair<List<Song>, List<Playlist>> {
        val store = ServerStore(context)
        if (store.source == MusicSource.Server) {
            val account = store.account ?: return emptyList<Song>() to emptyList()
            return ServerLibrary.load(context, account)?.let { it.songs to it.playlists } ?: (emptyList<Song>() to emptyList())
        }
        val permission =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return emptyList<Song>() to emptyList()
        val songs = MusicRepository(context.contentResolver).songs()
        val playlists = runCatching { Playlists.decode(File(context.filesDir, "playlists.json").readText()) }.getOrDefault(emptyList())
        return songs to playlists
    }
}
