package io.github.michaelmassoni.tessera.gallery.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import io.github.michaelmassoni.tessera.gallery.R
import io.github.michaelmassoni.tessera.gallery.engine.Adjustments
import io.github.michaelmassoni.tessera.gallery.engine.Affine
import io.github.michaelmassoni.tessera.gallery.engine.AutoEnhance
import io.github.michaelmassoni.tessera.gallery.engine.EditColor
import io.github.michaelmassoni.tessera.gallery.engine.EditGeometry
import io.github.michaelmassoni.tessera.gallery.engine.EditHistory
import io.github.michaelmassoni.tessera.gallery.engine.EditState
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.engine.NormRect
import io.github.michaelmassoni.tessera.gallery.engine.Ratio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the editor needs from the app: decoding, saving, and handing off to another editor. */
interface EditorActions {
    /** The photo at preview size, upright. Blocking. */
    fun preview(item: MediaItem): Bitmap

    fun histogram(bitmap: Bitmap): IntArray

    /** Saves a copy next to the original; [onDone] gets whether it worked. */
    fun saveCopy(
        item: MediaItem,
        state: EditState,
        onDone: (Boolean) -> Unit,
    )

    /** Replaces the original once the system has asked the user; [onDone] gets whether it worked. */
    fun overwrite(
        item: MediaItem,
        state: EditState,
        onDone: (Boolean) -> Unit,
    )

    fun canEditElsewhere(item: MediaItem): Boolean

    fun editElsewhere(item: MediaItem)
}

private enum class Mode { Crop, Adjust }

private enum class Slide(
    val label: Int,
) {
    Brightness(R.string.edit_brightness),
    Contrast(R.string.edit_contrast),
    Saturation(R.string.edit_saturation),
    Warmth(R.string.edit_warmth),
    ;

    fun get(a: Adjustments) =
        when (this) {
            Brightness -> a.brightness
            Contrast -> a.contrast
            Saturation -> a.saturation
            Warmth -> a.warmth
        }

    fun set(
        a: Adjustments,
        v: Float,
    ) = when (this) {
        Brightness -> a.copy(brightness = v)
        Contrast -> a.copy(contrast = v)
        Saturation -> a.copy(saturation = v)
        Warmth -> a.copy(warmth = v)
    }
}

/**
 * Crop, rotate, straighten and adjust a photo. Changes are a list of [EditState]s (for undo);
 * while a drag or slider moves, the change is shown live and added to the list when it ends.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Editor(
    item: MediaItem,
    actions: EditorActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var history by rememberSaveable(stateSaver = HistorySaver) { mutableStateOf(EditHistory()) }
    var live by remember { mutableStateOf<EditState?>(null) }
    var mode by rememberSaveable { mutableStateOf(Mode.Crop) }
    var slide by rememberSaveable { mutableStateOf(Slide.Brightness) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, item.id) {
        value =
            withContext(Dispatchers.IO) { runCatching { actions.preview(item) }.getOrNull() }
    }
    val current = history.current
    val shown = live ?: current

    fun push(state: EditState) {
        history = history.push(state)
        live = null
    }
    val close = { if (current.isIdentity || saving) onClose() else confirmDiscard = true }
    BackHandler(onBack = close)

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close_editor))
                    }
                },
                actions = {
                    IconButton(onClick = { history = history.undo() }, enabled = history.canUndo && !saving) {
                        Icon(painterResource(DesignR.drawable.ic_undo), contentDescription = stringResource(R.string.edit_undo))
                    }
                    IconButton(onClick = { history = history.redo() }, enabled = history.canRedo && !saving) {
                        Icon(painterResource(DesignR.drawable.ic_redo), contentDescription = stringResource(R.string.edit_redo))
                    }
                    Button(
                        onClick = {
                            saving = true
                            actions.saveCopy(item, current) { ok ->
                                saving = false
                                if (ok) onClose()
                            }
                        },
                        enabled = !current.isIdentity && !saving,
                    ) { Text(stringResource(R.string.edit_save_copy)) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !saving) {
                            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_save_over)) },
                                enabled = !current.isIdentity,
                                onClick = {
                                    menu = false
                                    saving = true
                                    actions.overwrite(item, current) { ok ->
                                        saving = false
                                        if (ok) onClose()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_reset)) },
                                enabled = !current.isIdentity,
                                onClick = {
                                    menu = false
                                    push(EditState())
                                },
                            )
                            if (actions.canEditElsewhere(item)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_elsewhere)) },
                                    onClick = {
                                        menu = false
                                        actions.editElsewhere(item)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image == null) {
                    LoadingIndicator()
                } else {
                    EditPreview(
                        image = image,
                        state =
                            if (comparing) {
                                EditState(
                                    quarterTurns = shown.quarterTurns,
                                    flipped = shown.flipped,
                                    straighten = shown.straighten,
                                    crop = shown.crop,
                                )
                            } else {
                                shown
                            },
                        cropping = mode == Mode.Crop,
                        onCrop = { crop, done -> if (done) push(shown.copy(crop = crop)) else live = shown.copy(crop = crop) },
                        onCompare = { comparing = it },
                    )
                }
                if (saving) {
                    Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                val aspect =
                    bitmap?.let { b ->
                        EditGeometry.frame(b.width, b.height, shown.quarterTurns).let { (w, h) -> w.toFloat() / h }
                    } ?: 1f
                if (mode == Mode.Crop) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ratio.entries.forEach { r ->
                            FilterChip(
                                selected = shown.ratio == r,
                                onClick = { push(shown.copy(ratio = r, crop = EditGeometry.fit(r, aspect))) },
                                label = { Text(stringResource(r.label())) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val turned = shown.copy(quarterTurns = (shown.quarterTurns + 1) % 4)
                            val newAspect =
                                bitmap?.let { b ->
                                    EditGeometry.frame(b.width, b.height, turned.quarterTurns).let { (w, h) ->
                                        w.toFloat() /
                                            h
                                    }
                                }
                                    ?: 1f
                            push(turned.copy(crop = EditGeometry.fit(shown.ratio, newAspect)))
                        }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_rotate_right),
                                contentDescription = stringResource(R.string.edit_rotate),
                            )
                        }
                        IconButton(onClick = {
                            val c = shown.crop
                            push(shown.copy(flipped = !shown.flipped, crop = NormRect(1f - c.right, c.top, 1f - c.left, c.bottom)))
                        }) { Icon(painterResource(DesignR.drawable.ic_flip), contentDescription = stringResource(R.string.edit_flip)) }
                        val label = stringResource(R.string.edit_straighten_value, shown.straighten.roundToInt())
                        EditSlider(
                            value = shown.straighten,
                            onChange = { live = shown.copy(straighten = it) },
                            onFinished = { live?.let(::push) },
                            range = -EditGeometry.MAX_STRAIGHTEN..EditGeometry.MAX_STRAIGHTEN,
                            modifier = Modifier.weight(1f).semantics { contentDescription = label },
                        )
                        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp).padding(start = 8.dp))
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { bitmap?.let { push(shown.copy(adjust = AutoEnhance.from(actions.histogram(it)))) } },
                            label = { Text(stringResource(R.string.edit_auto)) },
                            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_auto_fix_high), contentDescription = null) },
                        )
                        Slide.entries.forEach { s ->
                            FilterChip(selected = slide == s, onClick = { slide = s }, label = { Text(stringResource(s.label)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val value = slide.get(shown.adjust)
                        val label = "${stringResource(slide.label)} ${(value * 100).roundToInt()}"
                        EditSlider(
                            value = value,
                            onChange = { live = shown.copy(adjust = slide.set(shown.adjust, it)) },
                            onFinished = { live?.let(::push) },
                            range = -1f..1f,
                            modifier = Modifier.weight(1f).semantics { contentDescription = label },
                        )
                        Text(
                            "${(value * 100).roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(48.dp).padding(start = 8.dp),
                        )
                    }
                }
                val modes = listOf(Mode.Crop to R.string.edit_mode_crop, Mode.Adjust to R.string.edit_mode_adjust)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { mode = m },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = {
                                Icon(
                                    painterResource(if (m == Mode.Crop) DesignR.drawable.ic_crop else DesignR.drawable.ic_tune),
                                    contentDescription = null,
                                )
                            },
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.edit_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.edit_keep_editing)) } },
        )
    }
}

private fun Ratio.label() =
    when (this) {
        Ratio.Free -> R.string.ratio_free
        Ratio.Original -> R.string.ratio_original
        Ratio.Square -> R.string.ratio_square
        Ratio.FourThree -> R.string.ratio_4_3
        Ratio.ThreeTwo -> R.string.ratio_3_2
        Ratio.SixteenNine -> R.string.ratio_16_9
    }

/**
 * The photo with [state] applied. Cropping shows the whole straightened frame with the crop on
 * top (drag its corners, edges or middle); otherwise the cropped result, where pressing and
 * holding shows it without the colour changes.
 */
@Composable
private fun EditPreview(
    image: Bitmap,
    state: EditState,
    cropping: Boolean,
    onCrop: (NormRect, Boolean) -> Unit,
    onCompare: (Boolean) -> Unit,
) {
    val touch = with(LocalDensity.current) { 28.dp.toPx() }
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val (frameW, frameH) =
            EditGeometry.frame(image.width, image.height, state.quarterTurns).let { (w, h) ->
                w.toFloat() to h.toFloat()
            }
        val (base, contentW, contentH) =
            if (cropping) {
                Triple(EditGeometry.toFrame(image.width, image.height, state), frameW, frameH)
            } else {
                val (t, size) = EditGeometry.toOutput(image.width, image.height, state)
                Triple(t, size.first.toFloat(), size.second.toFloat())
            }
        val scale = min(viewW / contentW, viewH / contentH)
        val left = (viewW - contentW * scale) / 2
        val top = (viewH - contentH * scale) / 2
        val toView = base.then(Affine.scale(scale)).then(Affine.translate(left, top))
        val paint =
            remember(state.adjust) {
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    if (!state.adjust.isIdentity) colorFilter = ColorMatrixColorFilter(ColorMatrix(EditColor.matrix(state.adjust)))
                }
            }
        // The crop frame in view pixels.
        val c = state.crop
        val cropLeft = left + c.left * frameW * scale
        val cropTop = top + c.top * frameH * scale
        val cropRight = left + c.right * frameW * scale
        val cropBottom = top + c.bottom * frameH * scale
        var handle by remember { mutableStateOf<EditGeometry.Handle?>(null) }
        var rect by remember { mutableStateOf(c) }
        // The gesture handler outlives recompositions; it reads the current frame and state from here.
        val bounds by rememberUpdatedState(floatArrayOf(cropLeft, cropTop, cropRight, cropBottom))
        val latest by rememberUpdatedState(state)
        val gestures =
            if (cropping) {
                Modifier.pointerInput(state.quarterTurns, state.ratio, scale) {
                    detectDragGestures(
                        onDragStart = { p ->
                            rect = latest.crop
                            handle = pickHandle(p, bounds[0], bounds[1], bounds[2], bounds[3], touch)
                        },
                        onDrag = { change, amount ->
                            val h = handle ?: return@detectDragGestures
                            change.consume()
                            rect =
                                EditGeometry.drag(
                                    rect,
                                    h,
                                    amount.x / (frameW * scale),
                                    amount.y / (frameH * scale),
                                    latest.ratio,
                                    frameW / frameH,
                                )
                            onCrop(rect, false)
                        },
                        onDragEnd = {
                            if (handle != null) onCrop(rect, true)
                            handle = null
                        },
                    )
                }
            } else {
                Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        onCompare(true)
                        tryAwaitRelease()
                        onCompare(false)
                    })
                }
            }
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                n.withSave {
                    // Straightening turns the photo past the frame's edges; only the frame shows.
                    clipRect(left, top, left + contentW * scale, top + contentH * scale)
                    drawBitmap(image, Matrix().apply { setValues(toView.values()) }, paint)
                }
            }
            if (cropping) {
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset(left, top), Size(contentW * scale, cropTop - top))
                drawRect(dim, Offset(left, cropBottom), Size(contentW * scale, top + contentH * scale - cropBottom))
                drawRect(dim, Offset(left, cropTop), Size(cropLeft - left, cropBottom - cropTop))
                drawRect(dim, Offset(cropRight, cropTop), Size(left + contentW * scale - cropRight, cropBottom - cropTop))
                val w = cropRight - cropLeft
                val h = cropBottom - cropTop
                val thirds = Color.White.copy(alpha = 0.5f)
                for (i in 1..2) {
                    drawLine(thirds, Offset(cropLeft + w * i / 3, cropTop), Offset(cropLeft + w * i / 3, cropBottom), 1.dp.toPx())
                    drawLine(thirds, Offset(cropLeft, cropTop + h * i / 3), Offset(cropRight, cropTop + h * i / 3), 1.dp.toPx())
                }
                drawRect(Color.White, Offset(cropLeft, cropTop), Size(w, h), style = Stroke(1.5.dp.toPx()))
                val len = 20.dp.toPx()
                val stroke = 4.dp.toPx()
                for ((x, y) in listOf(cropLeft to cropTop, cropRight to cropTop, cropLeft to cropBottom, cropRight to cropBottom)) {
                    val sx = if (x == cropLeft) 1f else -1f
                    val sy = if (y == cropTop) 1f else -1f
                    drawLine(scheme.primary, Offset(x, y), Offset(x + sx * len, y), stroke)
                    drawLine(scheme.primary, Offset(x, y), Offset(x, y + sy * len), stroke)
                }
            }
        }
    }
}

/** The crop handle nearest [p]: a corner, then an edge, then the middle (move). */
private fun pickHandle(
    p: Offset,
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    touch: Float,
): EditGeometry.Handle? {
    val nearL = abs(p.x - l) < touch
    val nearR = abs(p.x - r) < touch
    val nearT = abs(p.y - t) < touch
    val nearB = abs(p.y - b) < touch
    val inX = p.x in (l - touch)..(r + touch)
    val inY = p.y in (t - touch)..(b + touch)
    return when {
        nearL && nearT -> EditGeometry.Handle.TopLeft
        nearR && nearT -> EditGeometry.Handle.TopRight
        nearL && nearB -> EditGeometry.Handle.BottomLeft
        nearR && nearB -> EditGeometry.Handle.BottomRight
        nearL && inY -> EditGeometry.Handle.Left
        nearR && inY -> EditGeometry.Handle.Right
        nearT && inX -> EditGeometry.Handle.Top
        nearB && inX -> EditGeometry.Handle.Bottom
        p.x in l..r && p.y in t..b -> EditGeometry.Handle.Move
        else -> null
    }
}

/** The edit history survives rotation: each state as numbers. */
private val HistorySaver =
    listSaver<EditHistory, Float>(
        save = { h ->
            listOf(h.index.toFloat()) +
                h.states.flatMap { s ->
                    listOf(
                        s.quarterTurns.toFloat(),
                        if (s.flipped) 1f else 0f,
                        s.straighten,
                        s.crop.left,
                        s.crop.top,
                        s.crop.right,
                        s.crop.bottom,
                        s.ratio.ordinal.toFloat(),
                        s.adjust.brightness,
                        s.adjust.contrast,
                        s.adjust.saturation,
                        s.adjust.warmth,
                    )
                }
        },
        restore = { v ->
            val states =
                v.drop(1).chunked(12).map { f ->
                    EditState(
                        quarterTurns = f[0].toInt(),
                        flipped = f[1] != 0f,
                        straighten = f[2],
                        crop = NormRect(f[3], f[4], f[5], f[6]),
                        ratio = Ratio.entries[f[7].toInt()],
                        adjust = Adjustments(f[8], f[9], f[10], f[11]),
                    )
                }
            EditHistory(states.ifEmpty { listOf(EditState()) }, v[0].toInt().coerceIn(0, (states.size - 1).coerceAtLeast(0)))
        },
    )

/** A slider that follows [value] from outside (undo, Auto, Reset) while still being draggable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(range) { SliderState(value, trackRange = range) }
    state.value = value
    Slider(state = state, onValueChange = onChange, onValueChangeFinished = onFinished, modifier = modifier)
}
