package io.github.michaelmassoni.tessera.camera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.camera.CameraState
import io.github.michaelmassoni.tessera.camera.R
import io.github.michaelmassoni.tessera.camera.engine.Flash
import io.github.michaelmassoni.tessera.camera.engine.Mode
import io.github.michaelmassoni.tessera.camera.engine.ZoomSteps
import io.github.michaelmassoni.tessera.camera.engine.formatElapsed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the camera screen can do. */
interface CameraActions {
    fun shutter()

    fun setMode(mode: Mode)

    fun switchCamera()

    fun cycleFlash()

    fun zoom(ratio: Float)

    fun focus(
        x: Float,
        y: Float,
        surfaceWidth: Int,
        surfaceHeight: Int,
    )

    fun openLast(uri: Uri)

    fun openSettings()
}

/**
 * The viewfinder with controls over it. Controls sit along the bottom in portrait and down the
 * end edge in landscape. Callers wrap it in a dark theme: a camera is always shown on black.
 */
@Composable
fun CameraScreen(
    state: CameraState,
    surfaceRequest: SurfaceRequest?,
    actions: CameraActions,
    modifier: Modifier = Modifier,
    captureIntent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val transformer = remember { MutableCoordinateTransformer() }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.capturing) {
        if (state.capturing) {
            flash.snapTo(0.6f)
            flash.animateTo(0f)
        }
    }
    LaunchedEffect(focusAt) {
        if (focusAt != null) {
            delay(1200)
            focusAt = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(scheme.background)) {
        val landscape = maxWidth > maxHeight
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(request) {
                            detectTapGestures { offset ->
                                focusAt = offset
                                val surface = with(transformer) { offset.transform() }
                                actions.focus(surface.x, surface.y, request.resolution.width, request.resolution.height)
                            }
                        }.pointerInput(state.zoom) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                if (gestureZoom != 1f) actions.zoom(state.zoom * gestureZoom)
                            }
                        },
            )
        }
        focusAt?.let { FocusRing(it) }
        // Shutter feedback: a brief dim of the viewfinder.
        Box(Modifier.fillMaxSize().alpha(flash.value).background(scheme.scrim))

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopControls(state, actions, captureIntent, Modifier.align(if (landscape) Alignment.TopStart else Alignment.TopCenter))
            if (landscape) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { BottomControls(state, actions, captureIntent, vertical = true) }
            } else {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { BottomControls(state, actions, captureIntent, vertical = false) }
            }
        }
        if (state.error) {
            Text(
                stringResource(R.string.camera_unavailable),
                color = scheme.onBackground,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
}

@Composable
private fun FocusRing(at: Offset) {
    val size = 72.dp
    val px = with(LocalDensity.current) { size.toPx() }
    Box(
        Modifier
            .offset { IntOffset((at.x - px / 2).roundToInt(), (at.y - px / 2).roundToInt()) }
            .size(size)
            .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape),
    )
}

@Composable
private fun TopControls(
    state: CameraState,
    actions: CameraActions,
    captureIntent: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OverlayButton(
            when (state.flash) {
                Flash.Off -> DesignR.drawable.ic_flash_off
                Flash.Auto -> DesignR.drawable.ic_flash_auto
                Flash.On -> DesignR.drawable.ic_flash_on
            },
            stringResource(
                when (state.flash) {
                    Flash.Off -> R.string.flash_off
                    Flash.Auto -> R.string.flash_auto
                    Flash.On -> if (state.mode == Mode.Video) R.string.torch_on else R.string.flash_on
                },
            ),
            actions::cycleFlash,
        )
        if (state.recording) RecordingPill(state.recordingStart)
        if (!captureIntent && !state.recording) {
            OverlayButton(DesignR.drawable.ic_settings, stringResource(R.string.settings), actions::openSettings)
        }
    }
}

@Composable
private fun RecordingPill(start: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    Surface(color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, shape = RoundedCornerShape(50)) {
        Text(
            formatElapsed(now - start),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun BottomControls(
    state: CameraState,
    actions: CameraActions,
    captureIntent: Boolean,
    vertical: Boolean,
) {
    val zoomSteps = ZoomSteps.steps(state.minZoom, state.maxZoom)
    if (zoomSteps.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            zoomSteps.forEach { step ->
                val selected = kotlin.math.abs(state.zoom - step) < 0.05f
                Surface(
                    onClick = { actions.zoom(step) },
                    shape = CircleShape,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.scrim.copy(
                                alpha = 0.5f,
                            )
                        },
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(ZoomSteps.label(if (selected) state.zoom else step), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (!captureIntent && !state.recording) {
        SingleChoiceSegmentedButtonRow {
            Mode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { actions.setMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                ) { Text(stringResource(if (mode == Mode.Photo) R.string.mode_photo else R.string.mode_video)) }
            }
        }
    }
    val row: @Composable () -> Unit = {
        LastCapture(state.lastCapture, actions, visible = !captureIntent)
        Shutter(state, actions)
        if (state.hasFront && !state.recording) {
            OverlayButton(DesignR.drawable.ic_cameraswitch, stringResource(R.string.switch_camera), actions::switchCamera, large = true)
        } else {
            Spacer(Modifier.size(56.dp))
        }
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { row() }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) { row() }
    }
}

@Composable
private fun Shutter(
    state: CameraState,
    actions: CameraActions,
) {
    val scheme = MaterialTheme.colorScheme
    val label =
        stringResource(
            when {
                state.mode == Mode.Photo -> R.string.take_photo
                state.recording -> R.string.stop_recording
                else -> R.string.start_recording
            },
        )
    Surface(
        onClick = actions::shutter,
        enabled = !state.capturing,
        shape = CircleShape,
        color = scheme.onBackground,
        modifier = Modifier.size(80.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            val inner = if (state.recording) 28.dp else 64.dp
            Box(
                Modifier
                    .size(inner)
                    .clip(if (state.recording) RoundedCornerShape(6.dp) else CircleShape)
                    .background(if (state.mode == Mode.Video) scheme.error else scheme.onBackground)
                    .border(
                        if (state.recording) 0.dp else 3.dp,
                        scheme.background,
                        if (state.recording) RoundedCornerShape(6.dp) else CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun LastCapture(
    uri: Uri?,
    actions: CameraActions,
    visible: Boolean,
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, uri) {
        value =
            uri?.let {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.loadThumbnail(it, Size(160, 160), null).asImageBitmap()
                    } catch (_: IOException) {
                        null
                    }
                }
            }
    }
    val image = thumb
    if (!visible || uri == null || image == null) {
        Spacer(Modifier.size(56.dp))
        return
    }
    val label = stringResource(R.string.open_last)
    Surface(onClick = {
        actions.openLast(uri)
    }, shape = CircleShape, modifier = Modifier.size(56.dp).semantics { contentDescription = label }) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun OverlayButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    large: Boolean = false,
) {
    val colors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onBackground,
        )
    FilledTonalIconButton(onClick = onClick, colors = colors, modifier = if (large) Modifier.size(56.dp) else Modifier) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

/** After a capture for another app: show it and ask Retake or OK. */
@Composable
fun ReviewScreen(
    photo: java.io.File?,
    isVideo: Boolean,
    onRetake: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(null, photo) {
        value =
            photo?.takeIf { !isVideo }?.let { f ->
                withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(
                        f.path,
                        BitmapFactory.Options().apply {
                            inSampleSize =
                                4
                        },
                    )
                }
            }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (isVideo) {
            Text(
                stringResource(R.string.video_recorded),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedButton(onClick = onRetake) { Text(stringResource(R.string.retake)) }
            Button(onClick = onAccept) { Text(stringResource(R.string.use_this)) }
        }
    }
}

/** Shown until camera access is granted. */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(DesignR.drawable.ic_photo_camera),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = if (denied) onSettings else onGrant) {
                Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_grant))
            }
        }
    }
}
