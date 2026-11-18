package io.github.michaelmassoni.tessera.music.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import io.github.michaelmassoni.tessera.music.engine.Song

/** Reads music from MediaStore. Blocking: call off the main thread. */
class MusicRepository(
    private val resolver: ContentResolver,
) {
    fun songs(): List<Song> {
        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val songs = mutableListOf<Song>()
        resolver
            .query(
                uri,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ARTIST_ID,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.YEAR,
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    songs +=
                        Song(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                            title = c.getString(1).orEmpty(),
                            artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                            artistId = c.getLong(3),
                            album = c.getString(4).orEmpty(),
                            albumId = c.getLong(5),
                            durationMillis = c.getLong(6),
                            track = c.getInt(7),
                            year = c.getInt(8),
                        )
                }
            }
        return songs
    }

    companion object {
        fun albumUri(albumId: Long) = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
    }
}
