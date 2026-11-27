package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.cast.CastDevice
import io.github.michaelmassoni.tessera.core.cast.CastIconButton
import io.github.michaelmassoni.tessera.core.cast.CastState
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.Progress
import io.github.michaelmassoni.tessera.music.QueueItem
import io.github.michaelmassoni.tessera.music.engine.Groups
import io.github.michaelmassoni.tessera.music.engine.Library
import io.github.michaelmassoni.tessera.music.engine.LyricLine
import io.github.michaelmassoni.tessera.music.engine.Lyrics
import io.github.michaelmassoni.tessera.music.engine.LyricsSource
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.Song
import java.net.InetAddress

private val songs =
    listOf(
        Song(1, "u1", "Northern Lights", "Aurora Bay", 1, "Polar", 10, 241_000, 1001, 2024),
        Song(2, "u2", "Slow Tide", "Aurora Bay", 1, "Polar", 10, 198_000, 1002, 2024),
        Song(3, "u3", "City at Dawn", "The Night Shift", 2, "Overtime", 20, 305_000, 1001, 2022, explicit = true),
        Song(4, "u4", "Paper Planes", "Juniper", 3, "Fold", 30, 187_000, 1001, 2021),
        Song(5, "u5", "Lanterns", "Juniper", 3, "Fold", 30, 222_000, 1002, 2021),
    )

private val library = LibraryState(true, Library.songs(songs), Library.albums(songs), Library.artists(songs))

private val playing =
    PlaybackState(
        hasMedia = true,
        songId = 3,
        title = "City at Dawn",
        artist = "The Night Shift",
        album = "Overtime",
        isPlaying = true,
        shuffle = true,
        queue = songs.map { QueueItem(it.id.toString(), it.title, it.artist) },
        from = PlayingFrom(PlayingFrom.Kind.Album, "Overtime"),
        index = 2,
    )

private val progress = Progress(95_000, 305_000)

private object NoLibrary : LibraryActions {
    override fun play(
        songs: List<Song>,
        index: Int,
        from: PlayingFrom,
    ) = Unit

    override fun shuffle(
        songs: List<Song>,
        from: PlayingFrom,
    ) = Unit

    override fun createPlaylist(
        name: String,
        songIds: List<Long>,
    ) = Unit

    override fun renamePlaylist(
        id: Long,
        name: String,
    ) = Unit

    override fun deletePlaylist(id: Long) = Unit

    override fun addToPlaylist(
        id: Long,
        songIds: List<Long>,
    ) = Unit

    override fun removeFromPlaylist(
        id: Long,
        index: Int,
    ) = Unit

    override fun openSettings() = Unit
}

private object NoPlayer : PlayerActions {
    override fun togglePlay() = Unit

    override fun next() = Unit

    override fun previous() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun toggleShuffle() = Unit

    override fun cycleRepeat() = Unit

    override fun skipTo(index: Int) = Unit
}

@TesseraPreviews
@Composable
private fun LibraryPreview() {
    TesseraTheme(dynamicColor = false) { MusicApp(library, playing, { progress }, NoLibrary, NoPlayer) }
}

@TesseraPreviews
@Composable
private fun ServerLibraryPreview() {
    TesseraTheme(dynamicColor = false) {
        MusicApp(library.copy(fromServer = true, notice = ServerNotice.Unreachable), playing, { progress }, NoLibrary, NoPlayer)
    }
}

@TesseraPreviews
@Composable
private fun FullPlayerPreview() {
    TesseraTheme(
        dynamicColor = false,
    ) {
        FullPlayer(
            playing,
            { progress },
            NoPlayer,
            onClose = {},
            lyrics = LyricsState.Found(previewLyrics),
            links = NowPlayingLinks(onAlbum = {}, onArtist = {}, onSource = {}),
            explicit = true,
            // The heart (liked) and save to playlist, as with a library.
            libraryActions = NoLibrary,
            liked = true,
        )
    }
}

// A playlist's page: count, Play and Shuffle, and its songs, in both themes.
@TesseraPreviews
@Composable
private fun PlaylistPreview() {
    val playlist = Playlist(1, "Road trip", listOf(4, 1, 5))
    TesseraTheme(dynamicColor = false) {
        PlaylistScreen(
            playlist,
            playlist.songIds.mapNotNull { id ->
                songs.firstOrNull { it.id == id }
            },
            listOf(playlist),
            NoLibrary,
            onBack = {},
        )
    }
}

private val previewLyrics =
    Lyrics(
        listOf(
            LyricLine(0, "Streetlights hum a quiet tune"),
            LyricLine(30_000, "City wakes beneath the moon"),
            LyricLine(60_000, "Every corner, every light"),
            LyricLine(90_000, "Calling out across the night"),
            LyricLine(120_000, ""),
            LyricLine(150_000, "Hold the dawn a little while"),
        ),
        LyricsSource.Server,
    )

@TesseraPreviews
@Composable
private fun LyricsScreenPreview() {
    TesseraTheme(dynamicColor = false) { LyricsScreen(previewLyrics, playing, { progress }, NoPlayer, onClose = {}) }
}

@TesseraPreviews
@Composable
private fun NoLyricsPlayerPreview() {
    TesseraTheme(dynamicColor = false) {
        FullPlayer(playing, { progress }, NoPlayer, onClose = {}, lyrics = LyricsState.None(canLookOnline = true))
    }
}

private val livingRoom =
    CastDevice("1", "Living Room TV", "Chromecast with Google TV", InetAddress.getLoopbackAddress(), 8009, video = true)

// The player while casting: the Cast button filled in and "Casting to ..." under the controls.
@TesseraPreviews
@Composable
private fun CastingPlayerPreview() {
    TesseraTheme(dynamicColor = false) {
        FullPlayer(
            playing,
            { progress },
            NoPlayer,
            onClose = {},
            castButton = { CastIconButton(CastState.Connected(livingRoom, 0.4f, false)) {} },
            castingTo = livingRoom.name,
        )
    }
}

private val genreSongs =
    songs.mapIndexed { i, s -> s.copy(genre = listOf("Ambient", "Ambient; Electronic", "Jazz", "Indie Folk", "indie folk")[i]) }

// Genres from the Songs tab's chip.
@TesseraPreviews
@Composable
private fun GenresPreview() {
    TesseraTheme(dynamicColor = false) { GenresScreen(Groups.genres(genreSongs), onOpen = {}, onBack = {}) }
}
