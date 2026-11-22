package io.github.michaelmassoni.tessera.gallery.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import coil3.request.ImageRequest
import io.github.michaelmassoni.tessera.gallery.R
import io.github.michaelmassoni.tessera.gallery.data.ExifDetails
import io.github.michaelmassoni.tessera.gallery.engine.AlbumTarget
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.engine.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import androidx.media3.common.MediaItem as Media3Item
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the viewer can do with the current item. */
interface ViewerActions {
    fun close()

    fun share(item: MediaItem)

    fun toggleFavorite(item: MediaItem)

    /** Whether an editor app is installed for this item. */
    fun canEdit(item: MediaItem): Boolean

    fun edit(item: MediaItem)

    fun setWallpaper(
        item: MediaItem,
        target: WallpaperTarget,
    )

    fun trash(item: MediaItem)

    /** Turns a photo 90° clockwise (its EXIF orientation; asks the system for permission first). */
    fun rotate(item: MediaItem)

    fun exif(item: MediaItem): ExifDetails?
}

/**
 * Full-screen viewer: swipe between [items], tap to toggle the controls (and the system bars).
 * Callers wrap it in a dark, pure-black theme (see GalleryApp): photos are viewed on black,
 * like the system gallery, whatever the app theme.
 */
@Composable
fun Viewer(
    items: List<MediaItem>,
    startIndex: Int,
    actions: ViewerActions,
    modifier: Modifier = Modifier,
    trashMode: Boolean = false,
    onEditPhoto: (MediaItem) -> Unit = {},
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { actions.close() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var controls by rememberSaveable { mutableStateOf(true) }
    var info by rememberSaveable { mutableStateOf(false) }
    var wallpaper by rememberSaveable { mutableStateOf(false) }
    SystemBars(visible = controls)
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Items run newest first, so older ones sit to the right: swipe left for older, as the grid
        // reads (left to right, newest first) and as Google Photos does.
        HorizontalPager(pager, key = { items[it].id }, beyondViewportPageCount = 1) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item, isCurrent = page == pager.currentPage, showControls = controls, onTap = { controls = !controls })
            } else {
                // Keyed by the modified time as well, so a photo saved over by the editor reloads.
                val context = LocalContext.current
                val request =
                    remember(
                        item.uri,
                        item.modified,
                    ) {
                        ImageRequest
                            .Builder(context)
                            .data(item.uri)
                            .memoryCacheKey("${item.uri}:${item.modified}")
                            .build()
                    }
                ZoomableAsyncImage(
                    model = request,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { controls = !controls },
                )
            }
        }
        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerButton(DesignR.drawable.ic_arrow_back, stringResource(R.string.action_back), actions::close)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    val context = LocalContext.current
                    Text(
                        DateUtils.formatDateTime(context, current.date, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        DateUtils.formatDateTime(context, current.date, DateUtils.FORMAT_SHOW_TIME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
                if (!trashMode) {
                    ViewerButton(DesignR.drawable.ic_info, stringResource(R.string.action_info)) { info = true }
                }
            }
        }
        AnimatedVisibility(controls && !trashMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerButton(DesignR.drawable.ic_share, stringResource(R.string.action_share)) { actions.share(current) }
                ViewerButton(
                    if (current.favorite) DesignR.drawable.ic_favorite_filled else DesignR.drawable.ic_favorite,
                    stringResource(if (current.favorite) R.string.action_unfavorite else R.string.action_favorite),
                ) { actions.toggleFavorite(current) }
                // Photos open the built-in editor; videos go to another app, when one is installed.
                val editable = remember(current.mimeType) { !current.isVideo || actions.canEdit(current) }
                if (editable) {
                    ViewerButton(DesignR.drawable.ic_edit, stringResource(R.string.action_edit)) {
                        if (current.isVideo) actions.edit(current) else onEditPhoto(current)
                    }
                }
                if (AlbumTarget.canRotate(current)) {
                    ViewerButton(DesignR.drawable.ic_rotate_right, stringResource(R.string.action_rotate)) { actions.rotate(current) }
                }
                if (!current.isVideo) {
                    ViewerButton(DesignR.drawable.ic_wallpaper, stringResource(R.string.action_wallpaper)) { wallpaper = true }
                }
                ViewerButton(DesignR.drawable.ic_delete, stringResource(R.string.action_trash)) { actions.trash(current) }
            }
        }
    }
    if (info) InfoSheet(current, actions, onDismiss = { info = false })
    if (wallpaper) {
        WallpaperSheet(onDismiss = { wallpaper = false }) { target -> actions.setWallpaper(current, target) }
    }
}

/** Light icons on the dark viewer backdrop. */
@Composable
private fun ViewerButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

/** Hides the status and navigation bars while the viewer's controls are hidden. */
@Composable
private fun SystemBars(visible: Boolean) {
    val activity = LocalActivity.current ?: return
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, activity.window.decorView) }
    DisposableEffect(visible) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // Light status bar icons on the black backdrop, restored on leaving.
    DisposableEffect(Unit) {
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = wasLight }
    }
}

/** A video that plays while its page is current and pauses as soon as it isn't. */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    showControls: Boolean,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val player =
        remember(item.uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(Media3Item.fromUri(item.uri.toUri()))
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
            }
        }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(isCurrent) { if (!isCurrent) player.pause() }
    androidx.lifecycle.compose.LifecycleResumeEffect(player) {
        onPauseOrDispose { player.pause() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        ContentFrame(player, modifier = Modifier.fillMaxSize())
        if (showControls) {
            val playPause = rememberPlayPauseButtonState(player)
            FilledTonalIconButton(
                onClick = playPause::onClick,
                enabled = playPause.isEnabled,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            ) {
                Icon(
                    painterResource(if (playPause.showPlay) DesignR.drawable.ic_play_arrow else DesignR.drawable.ic_pause),
                    contentDescription = stringResource(if (playPause.showPlay) R.string.action_play else R.string.action_pause),
                )
            }
            val scope = rememberCoroutineScope()
            val progress = rememberProgressStateWithTickCount(player, 1000, scope)
            val seekLabel = stringResource(R.string.seek)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 72.dp),
            ) {
                val state = remember { SliderState(0f) }
                state.value = progress.currentPositionProgress
                Slider(
                    state = state,
                    onValueChange = { player.seekTo(progress.progressToPosition(it)) },
                    modifier = Modifier.semantics { contentDescription = seekLabel },
                )
                Text(
                    "${formatDuration(
                        (progress.currentPositionProgress * progress.durationMs).toLong(),
                    )} / ${formatDuration(progress.durationMs.coerceAtLeast(0))}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(
    item: MediaItem,
    actions: ViewerActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val exif by produceState<ExifDetails?>(null, item.id) {
        value = if (item.isVideo) null else withContext(Dispatchers.IO) { actions.exif(item) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            val row: @Composable (Int, String) -> Unit = { label, value ->
                ListItem(supportingContent = { Text(stringResource(label)) }) { Text(value) }
            }
            row(
                R.string.info_date,
                DateUtils.formatDateTime(
                    context,
                    item.date,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
                ),
            )
            row(R.string.info_file, item.name)
            row(R.string.info_size, Formatter.formatShortFileSize(context, item.size))
            if (item.width > 0) row(R.string.info_resolution, stringResource(R.string.resolution, item.width, item.height))
            if (item.isVideo) row(R.string.info_duration, formatDuration(item.durationMillis))
            row(R.string.info_folder, item.relativePath.trimEnd('/'))
            exif?.let { e ->
                val details = listOfNotNull(e.aperture, e.exposure, e.focalLength, e.iso).joinToString("  ·  ")
                row(R.string.info_camera, listOfNotNull(e.camera, details.ifBlank { null }).joinToString("\n"))
            }
        }
    }
}

/** Where a photo set as wallpaper goes. */
enum class WallpaperTarget { Home, Lock, Both }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperSheet(
    onDismiss: () -> Unit,
    onPick: (WallpaperTarget) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.action_wallpaper),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            listOf(
                WallpaperTarget.Home to R.string.wallpaper_home,
                WallpaperTarget.Lock to R.string.wallpaper_lock,
                WallpaperTarget.Both to R.string.wallpaper_both,
            ).forEach { (target, label) ->
                ListItem(
                    modifier =
                        Modifier.clickable {
                            onDismiss()
                            onPick(target)
                        },
                ) { Text(stringResource(label)) }
            }
        }
    }
}
