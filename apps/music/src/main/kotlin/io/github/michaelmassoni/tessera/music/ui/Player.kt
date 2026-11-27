package io.github.michaelmassoni.tessera.music.ui

import android.graphics.ImageDecoder
import android.os.SystemClock
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.Progress
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.data.MusicRepository
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.Playlist
import io.github.michaelmassoni.tessera.music.engine.formatDuration
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import io.github.michaelmassoni.tessera.core.cast.R as CastR
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Player commands. */
interface PlayerActions {
    fun togglePlay()

    fun next()

    fun previous()

    /** Straight to the previous song, never restarting this one (swiping the art right). */
    fun previousSong() = previous()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeat()

    fun skipTo(index: Int)

    /** Moves a queued song from one place to another. */
    fun moveQueueItem(
        from: Int,
        to: Int,
    ) {}

    fun removeQueueItem(index: Int) {}

    /** Turns on looking up lyrics online (LRCLIB) and looks for this song's. */
    fun lookUpLyricsOnline() {}
}

/**
 * Album art: embedded artwork bytes when the player has them, else the system's album thumbnail,
 * else a music note on a container colour.
 */
@Composable
fun AlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    artwork: ByteArray? = null,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // Lists show the same album many times and scroll rows back in: thumbnails come from memory
    // after the first decode.
    val key = if (artwork == null && albumId > 0) "$albumId@$px" else null
    // Until this size is ready, any size already loaded stands in: art moving from a list to a page
    // keeps its picture instead of flashing the placeholder.
    val image by produceState(key?.let(ArtCache::get) ?: AnySize.get(albumId), albumId, artwork, px) {
        key?.let(ArtCache::get)?.let {
            value = it
            return@produceState
        }
        value =
            withContext(Dispatchers.IO) {
                if (artwork != null) {
                    // Embedded art is often thousands of pixels across: decode it at the size shown.
                    runCatching {
                        ImageDecoder
                            .decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(artwork))) { decoder, info, _ ->
                                val scale = px.toFloat() / minOf(info.size.width, info.size.height)
                                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                            }.asImageBitmap()
                    }.getOrNull()
                } else if (albumId > 0) {
                    // A server album's art comes from the server (cached on disk); a local one's from MediaStore.
                    val server = ServerLibrary.artFor(context, albumId)
                    try {
                        if (server != null) {
                            ServerLibrary.art(context, server, px)?.asImageBitmap()
                        } else {
                            context.contentResolver
                                .loadThumbnail(MusicRepository.albumUri(albumId), Size(px, px), null)
                                .asImageBitmap()
                        }?.also { bitmap ->
                            key?.let { ArtCache.put(it, bitmap) }
                            AnySize.put(albumId, bitmap)
                        }
                    } catch (_: IOException) {
                        null
                    }
                } else {
                    null
                }
            }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 8)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                painterResource(DesignR.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** The "E" before an explicit song's artist. */
@Composable
fun ExplicitBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Icon(
        painterResource(R.drawable.ic_explicit),
        contentDescription = stringResource(R.string.explicit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size),
    )
}

/** A song's artist line with the explicit badge before it (as Spotify does); the text gives way (ellipsis) first. */
@Composable
fun WithExplicitBadge(
    explicit: Boolean,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp,
    text: @Composable (Modifier) -> Unit,
) {
    if (!explicit) {
        text(modifier)
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ExplicitBadge(size = badgeSize)
        text(Modifier.weight(1f, fill = false))
    }
}

/** The bar above the navigation while something is loaded. */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    progress: () -> Progress,
    actions: PlayerActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            LinearProgressIndicator(
                progress = { progress().fraction },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(state.albumId, artwork = state.artwork, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (state.failed) {
                            stringResource(if (state.failedServer) R.string.cant_play else R.string.cant_play_file)
                        } else {
                            state.artist.ifBlank { stringResource(R.string.unknown_artist) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayPause(state.isPlaying, actions::togglePlay, filled = false)
                IconButton(onClick = actions::next) {
                    Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = stringResource(R.string.next))
                }
            }
        }
    }
}

@Composable
private fun PlayPause(
    playing: Boolean,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val label = stringResource(if (playing) R.string.pause else R.string.play)
    val icon = if (playing) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled
    if (filled) {
        FilledIconButton(onClick = onClick, modifier = modifier.size(size)) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(size / 2))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) { Icon(painterResource(icon), contentDescription = label) }
    }
}

/** Where the full player can take you: the playing song's album and artist pages, and where it plays from. */
class NowPlayingLinks(
    val onAlbum: (() -> Unit)? = null,
    val onArtist: (() -> Unit)? = null,
    /** "Playing from": the album, artist or playlist the queue came from, or all songs. */
    val onSource: (() -> Unit)? = null,
)

/** The full-screen player: one column, or the art beside the rest when the screen is wider than tall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    state: PlaybackState,
    progress: () -> Progress,
    actions: PlayerActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    lyrics: LyricsState = LyricsState.None(false),
    playlists: List<Playlist> = emptyList(),
    libraryActions: LibraryActions? = null,
    links: NowPlayingLinks = NowPlayingLinks(),
    explicit: Boolean = false,
    // The heart: null hides it (no song, or previews without a library).
    liked: Boolean? = null,
    // The Cast button (a slot, so previews show the bar without discovery) and the device being cast to.
    castButton: @Composable () -> Unit = {},
    castingTo: String? = null,
) {
    var queue by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    BackHandler(showLyrics) { showLyrics = false }
    Box(modifier) {
        Scaffold(
            // Clear of the camera cutout too: on its side, a phone's cutout is beside the art.
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { state.from?.let { PlayingFromTitle(it, links.onSource) } },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                painterResource(DesignR.drawable.ic_expand_more),
                                contentDescription = stringResource(R.string.close_player),
                            )
                        }
                    },
                    actions = {
                        if (links.onAlbum != null || links.onArtist != null) {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    links.onAlbum?.let { go ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.go_to_album)) },
                                            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_album), contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                go()
                                            },
                                        )
                                    }
                                    links.onArtist?.let { go ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.go_to_artist)) },
                                            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_artist), contentDescription = null) },
                                            onClick = {
                                                menu = false
                                                go()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                val nextLabel = stringResource(R.string.next)
                val previousLabel = stringResource(R.string.previous)
                val swipe = rememberSkipSwipe()
                val art: @Composable (Modifier) -> Unit = { m ->
                    // Swipe left for the next song, right for the previous.
                    AlbumArt(
                        state.albumId,
                        artwork = state.artwork,
                        size = 320.dp,
                        modifier = m.swipeToSkip(swipe, nextLabel, previousLabel, actions::next, actions::previousSong),
                    )
                }
                val details: @Composable () -> Unit = {
                    TitleRow(state, explicit, liked, playlists, libraryActions, links, onSave = { saving = true })
                    SeekBar(progress, actions)
                    Controls(state, actions)
                    OutputRow(castButton, castingTo, onQueue = { queue = true })
                    LyricsCard(
                        lyrics,
                        progress,
                        state.isPlaying,
                        onOpen = { showLyrics = true },
                        onLookOnline = actions::lookUpLyricsOnline,
                    )
                }
                if (maxWidth > maxHeight && maxWidth >= 560.dp) {
                    // Landscape: the art fills the height on the left, everything else scrolls beside it.
                    val side = minOf(maxHeight - 24.dp, maxWidth * 0.42f)
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                    ) {
                        Box(Modifier.fillMaxHeight().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                            art(Modifier.size(side))
                        }
                        // Centred beside the art when it fits; scrolls from the top when it doesn't.
                        Box(Modifier.weight(1f, fill = false).widthIn(max = 520.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) { details() }
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        art(Modifier.fillMaxWidth().aspectRatio(1f))
                        details()
                    }
                }
            }
        }
        if (queue) QueueSheet(state.queue, state.index, actions, onDismiss = { queue = false })
        if (saving && libraryActions != null) {
            SaveToPlaylistSheet(state.songId, playlists, libraryActions, onDismiss = { saving = false })
        }
        if (showLyrics && lyrics is LyricsState.Found) {
            LyricsScreen(lyrics.lyrics, state, progress, actions, onClose = { showLyrics = false })
        }
    }
}

/** Title, then "Artist · Album" (each a link to its page when it has one), the heart, and save to playlist. */
@Composable
private fun TitleRow(
    state: PlaybackState,
    explicit: Boolean,
    liked: Boolean?,
    playlists: List<Playlist>,
    libraryActions: LibraryActions?,
    links: NowPlayingLinks,
    onSave: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                // One line; a long title scrolls sideways to show the rest (as Spotify does).
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_DELAY_MS),
            )
            val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
            val line =
                buildAnnotatedString {
                    fun part(
                        text: String,
                        tag: String,
                        go: (() -> Unit)?,
                    ) {
                        if (text.isBlank()) return
                        if (length > 0) append(" · ")
                        if (go != null) {
                            withLink(LinkAnnotation.Clickable(tag, linkStyle) { go() }) { append(text) }
                        } else {
                            append(text)
                        }
                    }
                    part(state.artist, "artist", links.onArtist)
                    part(state.album, "album", links.onAlbum)
                }
            WithExplicitBadge(explicit) { m ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = m,
                )
            }
        }
        if (libraryActions != null && state.songId != 0L && liked != null) {
            IconToggleButton(checked = liked, onCheckedChange = { libraryActions.setLiked(state.songId, it) }) {
                Icon(
                    painterResource(if (liked) DesignR.drawable.ic_favorite_filled else DesignR.drawable.ic_favorite),
                    contentDescription = stringResource(R.string.like),
                    tint = if (liked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
        if (libraryActions != null && state.songId != 0L) {
            val saved = playlists.any { !it.readOnly && state.songId in it.songIds }
            IconButton(onClick = onSave) {
                Icon(
                    painterResource(
                        if (saved) DesignR.drawable.ic_playlist_add_check else DesignR.drawable.ic_playlist_add,
                    ),
                    contentDescription = stringResource(R.string.save_to_playlist),
                    tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        }
    }
}

/** "Playing from album" over the album's name; tapping it opens that album (or artist, playlist, songs). */
@Composable
private fun PlayingFromTitle(
    from: PlayingFrom,
    onClick: (() -> Unit)?,
) {
    // The whole title is the button, a bigger target than the name alone.
    val tap =
        if (onClick == null) {
            Modifier
        } else {
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.open_source), onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        }
    Column(tap, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(
                when (from.kind) {
                    PlayingFrom.Kind.Album -> R.string.playing_from_album
                    PlayingFrom.Kind.Artist -> R.string.playing_from_artist
                    PlayingFrom.Kind.Playlist -> R.string.playing_from_playlist
                    PlayingFrom.Kind.Search -> R.string.playing_from_search
                    PlayingFrom.Kind.Songs -> R.string.playing_from_songs
                    PlayingFrom.Kind.Genre -> R.string.playing_from_genre
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        val name = if (from.kind == PlayingFrom.Kind.Songs) stringResource(R.string.all_songs) else from.name
        if (name.isNotBlank()) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Controls(
    state: PlaybackState,
    actions: PlayerActions,
) {
    // On is a filled circle, not just a tint, so it reads at a glance in dark theme too.
    val toggle =
        IconButtonDefaults.iconToggleButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(checked = state.shuffle, onCheckedChange = { actions.toggleShuffle() }, colors = toggle) {
            Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = stringResource(R.string.shuffle))
        }
        IconButton(onClick = actions::previous) {
            Icon(painterResource(DesignR.drawable.ic_skip_previous), contentDescription = stringResource(R.string.previous))
        }
        PlayPause(state.isPlaying, actions::togglePlay, filled = true, size = 72.dp)
        IconButton(onClick = actions::next) {
            Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = stringResource(R.string.next))
        }
        IconToggleButton(
            checked = state.repeatMode != Player.REPEAT_MODE_OFF,
            onCheckedChange = { actions.cycleRepeat() },
            colors = toggle,
        ) {
            Icon(
                painterResource(
                    if (state.repeatMode ==
                        Player.REPEAT_MODE_ONE
                    ) {
                        DesignR.drawable.ic_repeat_one
                    } else {
                        DesignR.drawable.ic_repeat
                    },
                ),
                contentDescription =
                    stringResource(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.string.repeat_one
                            Player.REPEAT_MODE_ALL -> R.string.repeat_all
                            else -> R.string.repeat_off
                        },
                    ),
            )
        }
    }
}

/** Reads [progress] itself, so the tick recomposes only this. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    progress: () -> Progress,
    actions: PlayerActions,
) {
    val state = progress()
    val label = stringResource(R.string.position)
    val slider = remember { SliderState(0f) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    slider.value = dragging ?: state.fraction
    Column(Modifier.fillMaxWidth()) {
        Slider(
            state = slider,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { actions.seekTo((it * state.durationMs).toLong()) }
                dragging = null
            },
            modifier = Modifier.semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth()) {
            val shown = dragging?.let { (it * state.durationMs).toLong() } ?: state.positionMs
            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("-" + formatDuration(state.durationMs - shown), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The last art loaded for each album, at whatever size, to show while another size loads. */
private object AnySize : LruCache<Long, ImageBitmap>(64)

/** Album thumbnails by album and size, up to about 16 MB. */
private object ArtCache : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(
        key: String,
        value: ImageBitmap,
    ) = value.width * value.height * 4
}

/**
 * [progress], moved on every frame while playing. The player is sampled twice a second; between
 * samples the position is carried on from the last one, so the seek bar glides instead of jumping.
 */
@Composable
fun rememberSmoothProgress(progress: () -> Progress): () -> Progress {
    val frame = remember { mutableLongStateOf(0L) }
    val playing = progress().playing
    LaunchedEffect(playing) {
        while (playing) withFrameMillis { frame.longValue = SystemClock.elapsedRealtime() }
    }
    return remember(progress) {
        {
            frame.longValue // read, so whoever shows the position follows every frame
            progress().at(SystemClock.elapsedRealtime())
        }
    }
}

/**
 * Under the controls, as in other players: where it's playing on the left (the Cast button, and
 * the device while casting) and the queue on the right.
 */
@Composable
private fun OutputRow(
    castButton: @Composable () -> Unit,
    castingTo: String?,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The buttons' icons line up with the title and seek bar, their touch targets reaching past them.
    Row(
        modifier.fillMaxWidth().layout { measurable, constraints ->
            val extra = 24.dp.roundToPx()
            val wider = constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
            val placeable = measurable.measure(wider)
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        castButton()
        Text(
            castingTo?.let { stringResource(CastR.string.cast_casting_to, it) }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onQueue) {
            Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = stringResource(R.string.queue))
        }
    }
}

/** How long a long title rests before (and between) scrolling. */
private const val MARQUEE_DELAY_MS = 2_000
