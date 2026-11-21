package io.github.michaelmassoni.tessera.music.ui

import android.graphics.ImageDecoder
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.michaelmassoni.tessera.music.PlaybackState
import io.github.michaelmassoni.tessera.music.Progress
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.data.MusicRepository
import io.github.michaelmassoni.tessera.music.engine.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Player commands. */
interface PlayerActions {
    fun togglePlay()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeat()

    fun skipTo(index: Int)
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
    val image by produceState(key?.let(ArtCache::get), albumId, artwork, px) {
        value = key?.let(ArtCache::get)
        if (value != null) return@produceState
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
                    try {
                        context.contentResolver
                            .loadThumbnail(MusicRepository.albumUri(albumId), Size(px, px), null)
                            .asImageBitmap()
                            .also { bitmap -> key?.let { ArtCache.put(it, bitmap) } }
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
                        state.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** The full-screen player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    state: PlaybackState,
    progress: () -> Progress,
    actions: PlayerActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var queue by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = stringResource(R.string.close_player))
                    }
                },
                actions = {
                    IconButton(onClick = { queue = true }) {
                        Icon(painterResource(DesignR.drawable.ic_queue_music), contentDescription = stringResource(R.string.queue))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AlbumArt(state.albumId, artwork = state.artwork, size = 320.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SeekBar(progress, actions)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconToggleButton(checked = state.shuffle, onCheckedChange = { actions.toggleShuffle() }) {
                        Icon(painterResource(DesignR.drawable.ic_shuffle), contentDescription = stringResource(R.string.shuffle))
                    }
                    IconButton(onClick = actions::previous) {
                        Icon(painterResource(DesignR.drawable.ic_skip_previous), contentDescription = stringResource(R.string.previous))
                    }
                    PlayPause(state.isPlaying, actions::togglePlay, filled = true, size = 72.dp)
                    IconButton(onClick = actions::next) {
                        Icon(painterResource(DesignR.drawable.ic_skip_next), contentDescription = stringResource(R.string.next))
                    }
                    IconToggleButton(checked = state.repeatMode != Player.REPEAT_MODE_OFF, onCheckedChange = { actions.cycleRepeat() }) {
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
        }
    }
    if (queue) {
        ModalBottomSheet(onDismissRequest = { queue = false }) {
            LazyColumn {
                itemsIndexed(state.queue) { i, item ->
                    ListItem(
                        selected = i == state.index,
                        onClick = {
                            actions.skipTo(i)
                            queue = false
                        },
                        supportingContent = { Text(item.artist, maxLines = 1) },
                    ) { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
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

/** Album thumbnails by album and size, up to about 16 MB. */
private object ArtCache : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(
        key: String,
        value: ImageBitmap,
    ) = value.width * value.height * 4
}
