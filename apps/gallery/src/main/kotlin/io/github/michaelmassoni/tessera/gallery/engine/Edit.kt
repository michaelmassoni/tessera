package io.github.michaelmassoni.tessera.gallery.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** A rectangle in 0..1 coordinates of the frame being cropped. */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val Full = NormRect(0f, 0f, 1f, 1f)
    }
}

/** Crop ratios. Fixed ones follow the photo's orientation: 4:3 on a portrait photo is 3:4. */
enum class Ratio(
    private val long: Int,
    private val short: Int,
) {
    Free(0, 0),
    Original(0, 0),
    Square(1, 1),
    FourThree(4, 3),
    ThreeTwo(3, 2),
    SixteenNine(16, 9),
    ;

    /** Width over height in pixels for a frame of [frameAspect] (width / height), or null when free. */
    fun value(frameAspect: Float): Float? =
        when (this) {
            Free -> null
            Original -> frameAspect
            else -> if (frameAspect >= 1f) long.toFloat() / short else short.toFloat() / long
        }
}

/** Light and colour, each from -1 to 1 (the sliders show -100 to 100). Zero is unchanged. */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

/**
 * Everything an edit does, applied in this order: [quarterTurns] clockwise, [flipped]
 * horizontally, [straighten] degrees (zoomed so no empty corners show), then [crop] (in the
 * frame those steps produce), then the colour [adjust]ments.
 */
data class EditState(
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    val straighten: Float = 0f,
    val crop: NormRect = NormRect.Full,
    val ratio: Ratio = Ratio.Free,
    val adjust: Adjustments = Adjustments(),
) {
    val isIdentity get() = quarterTurns % 4 == 0 && !flipped && straighten == 0f && crop == NormRect.Full && adjust.isIdentity
}

/** A 2D affine transform: x' = a·x + b·y + tx, y' = c·x + d·y + ty. */
data class Affine(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
) {
    /** This transform, then [next]. */
    fun then(next: Affine) =
        Affine(
            a = next.a * a + next.b * c,
            b = next.a * b + next.b * d,
            c = next.c * a + next.d * c,
            d = next.c * b + next.d * d,
            tx = next.a * tx + next.b * ty + next.tx,
            ty = next.c * tx + next.d * ty + next.ty,
        )

    fun map(
        x: Float,
        y: Float,
    ) = Pair(a * x + b * y + tx, c * x + d * y + ty)

    /** Row-major 3×3 values, as `android.graphics.Matrix.setValues` takes them. */
    fun values() = floatArrayOf(a, b, tx, c, d, ty, 0f, 0f, 1f)

    companion object {
        fun translate(
            x: Float,
            y: Float,
        ) = Affine(tx = x, ty = y)

        fun scale(s: Float) = Affine(a = s, d = s)

        fun rotate(degrees: Float): Affine {
            val r = Math.toRadians(degrees.toDouble())
            val cs = cos(r).toFloat()
            val sn = sin(r).toFloat()
            return Affine(a = cs, b = -sn, c = sn, d = cs)
        }
    }
}

object EditGeometry {
    const val MAX_STRAIGHTEN = 45f

    /** The frame size after quarter turns: width and height swap on odd turns. */
    fun frame(
        width: Int,
        height: Int,
        quarterTurns: Int,
    ): Pair<Int, Int> = if (quarterTurns % 2 == 0) width to height else height to width

    /**
     * How much a frame of [w]×[h] turned by [degrees] must be scaled to still cover the whole
     * frame, so straightening never shows empty corners.
     */
    fun straightenScale(
        w: Float,
        h: Float,
        degrees: Float,
    ): Float {
        val t = Math.toRadians(abs(degrees).toDouble())
        return (cos(t) + max(w / h, h / w) * sin(t)).toFloat()
    }

    /**
     * Source pixels (the photo as decoded, upright from EXIF) to the uncropped, straightened
     * frame. Crop-mode previews draw with this; [toOutput] adds the crop.
     */
    fun toFrame(
        width: Int,
        height: Int,
        state: EditState,
    ): Affine {
        val turns = ((state.quarterTurns % 4) + 4) % 4
        val (fw, fh) = frame(width, height, turns).let { it.first.toFloat() to it.second.toFloat() }
        val turned =
            Affine.rotate(90f * turns).then(
                when (turns) {
                    1 -> Affine.translate(height.toFloat(), 0f)
                    2 -> Affine.translate(width.toFloat(), height.toFloat())
                    3 -> Affine.translate(0f, width.toFloat())
                    else -> Affine()
                },
            )
        val flipped = if (state.flipped) turned.then(Affine(a = -1f, tx = fw)) else turned
        if (state.straighten == 0f) return flipped
        return flipped
            .then(Affine.translate(-fw / 2, -fh / 2))
            .then(Affine.rotate(state.straighten))
            .then(Affine.scale(straightenScale(fw, fh, state.straighten)))
            .then(Affine.translate(fw / 2, fh / 2))
    }

    /** Source pixels to output pixels, and the output size: everything but the colours. */
    fun toOutput(
        width: Int,
        height: Int,
        state: EditState,
    ): Pair<Affine, Pair<Int, Int>> {
        val (fw, fh) = frame(width, height, state.quarterTurns)
        val crop = state.crop
        val out = (crop.width * fw).roundToInt().coerceAtLeast(1) to (crop.height * fh).roundToInt().coerceAtLeast(1)
        return toFrame(width, height, state).then(Affine.translate(-crop.left * fw, -crop.top * fh)) to out
    }

    /** The largest centred crop with [ratio] in a frame of [frameAspect] (width / height). */
    fun fit(
        ratio: Ratio,
        frameAspect: Float,
    ): NormRect {
        val r = ratio.value(frameAspect) ?: return NormRect.Full
        // Normalised width / height for that pixel ratio.
        val n = r / frameAspect
        return if (n >= 1f) {
            val h = 1f / n
            NormRect(0f, (1f - h) / 2, 1f, (1f + h) / 2)
        } else {
            NormRect((1f - n) / 2, 0f, (1f + n) / 2, 1f)
        }
    }

    /** Which part of the crop frame a drag moves. */
    enum class Handle(
        val left: Boolean = false,
        val top: Boolean = false,
        val right: Boolean = false,
        val bottom: Boolean = false,
    ) {
        TopLeft(left = true, top = true),
        Top(top = true),
        TopRight(right = true, top = true),
        Right(right = true),
        BottomRight(right = true, bottom = true),
        Bottom(bottom = true),
        BottomLeft(left = true, bottom = true),
        Left(left = true),
        Move,
    }

    /**
     * [rect] after dragging [handle] by ([dx], [dy]) (normalised), kept inside the frame, at least
     * [minSize] wide and tall, and at [ratio] when it's fixed.
     */
    fun drag(
        rect: NormRect,
        handle: Handle,
        dx: Float,
        dy: Float,
        ratio: Ratio,
        frameAspect: Float,
        minSize: Float = 0.08f,
    ): NormRect {
        if (handle == Handle.Move) {
            val x = dx.coerceIn(-rect.left, 1f - rect.right)
            val y = dy.coerceIn(-rect.top, 1f - rect.bottom)
            return NormRect(rect.left + x, rect.top + y, rect.right + x, rect.bottom + y)
        }
        var l = if (handle.left) (rect.left + dx).coerceIn(0f, rect.right - minSize) else rect.left
        var t = if (handle.top) (rect.top + dy).coerceIn(0f, rect.bottom - minSize) else rect.top
        val r = if (handle.right) (rect.right + dx).coerceIn(rect.left + minSize, 1f) else rect.right
        val b = if (handle.bottom) (rect.bottom + dy).coerceIn(rect.top + minSize, 1f) else rect.bottom
        val target = ratio.value(frameAspect)?.let { it / frameAspect } ?: return NormRect(l, t, r, b)
        // A fixed ratio: the dragged dimension leads and the other follows. The side opposite the
        // handle stays put; for an edge, the crop stays centred on the other axis.
        val horizontal = handle.left || handle.right
        val vertical = handle.top || handle.bottom
        var w = r - l
        var h = b - t
        if (horizontal && vertical) {
            if (abs(dx) >= abs(dy)) h = w / target else w = h * target
        } else if (horizontal) {
            h = w / target
        } else {
            w = h * target
        }
        val maxW =
            if (handle.left) {
                rect.right
            } else if (handle.right) {
                1f - rect.left
            } else {
                1f
            }
        val maxH =
            if (handle.top) {
                rect.bottom
            } else if (handle.bottom) {
                1f - rect.top
            } else {
                1f
            }
        val shrink = min(1f, min(maxW / w, maxH / h))
        w *= shrink
        h *= shrink
        l =
            when {
                handle.left -> rect.right - w
                handle.right -> rect.left
                else -> ((rect.left + rect.right) / 2 - w / 2).coerceIn(0f, 1f - w)
            }
        t =
            when {
                handle.top -> rect.bottom - h
                handle.bottom -> rect.top
                else -> ((rect.top + rect.bottom) / 2 - h / 2).coerceIn(0f, 1f - h)
            }
        return NormRect(l, t, l + w, t + h)
    }
}

/** The colour adjustments as a 4×5 colour matrix (row-major, offsets in 0..255), like `ColorMatrix`. */
object EditColor {
    fun matrix(adjust: Adjustments): FloatArray {
        var m = identity()
        if (adjust.saturation != 0f) m = concat(saturation(1f + adjust.saturation), m)
        if (adjust.contrast != 0f || adjust.brightness != 0f) {
            val k = 1f + adjust.contrast
            val offset = 128f * (1f - k) + adjust.brightness * 128f
            m = concat(scaleOffset(k, k, k, offset, offset, offset), m)
        }
        if (adjust.warmth != 0f) {
            val w = adjust.warmth * 0.12f
            m = concat(scaleOffset(1f + w, 1f, 1f - w, 0f, 0f, 0f), m)
        }
        return m
    }

    /** Applies [m] to one colour (0..255 each), for tests and the preview of a single swatch. */
    fun apply(
        m: FloatArray,
        r: Float,
        g: Float,
        b: Float,
    ): Triple<Float, Float, Float> {
        fun row(i: Int) = (m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * b + m[i * 5 + 3] * 255f + m[i * 5 + 4]).coerceIn(0f, 255f)
        return Triple(row(0), row(1), row(2))
    }

    private fun identity() =
        floatArrayOf(
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )

    private fun scaleOffset(
        r: Float,
        g: Float,
        b: Float,
        or: Float,
        og: Float,
        ob: Float,
    ) = floatArrayOf(
        r,
        0f,
        0f,
        0f,
        or,
        0f,
        g,
        0f,
        0f,
        og,
        0f,
        0f,
        b,
        0f,
        ob,
        0f,
        0f,
        0f,
        1f,
        0f,
    )

    /** Rec. 709 luminance weights, as `ColorMatrix.setSaturation` uses. */
    private fun saturation(s: Float): FloatArray {
        val lr = 0.2126f * (1 - s)
        val lg = 0.7152f * (1 - s)
        val lb = 0.0722f * (1 - s)
        return floatArrayOf(
            lr + s,
            lg,
            lb,
            0f,
            0f,
            lr,
            lg + s,
            lb,
            0f,
            0f,
            lr,
            lg,
            lb + s,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
    }

    /** [outer] applied after [inner]. */
    fun concat(
        outer: FloatArray,
        inner: FloatArray,
    ): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = if (col == 4) outer[row * 5 + 4] else 0f
                for (k in 0 until 4) v += outer[row * 5 + k] * inner[k * 5 + col]
                out[row * 5 + col] = v
            }
        }
        return out
    }
}

object AutoEnhance {
    private const val CLIP = 0.005f
    private const val MAX_CONTRAST = 0.6f
    private const val MAX_BRIGHTNESS = 0.4f
    private const val SATURATION = 0.1f

    /**
     * Adjustments that stretch the photo's tones: the darkest and brightest 0.5% of [luminance]
     * (a 256-bin histogram) move towards black and white, with a little saturation. Never harsher
     * than [MAX_CONTRAST] or [MAX_BRIGHTNESS].
     */
    fun from(luminance: IntArray): Adjustments {
        val total = luminance.sum().takeIf { it > 0 } ?: return Adjustments()
        val clip = (total * CLIP).toInt()
        var low = 0
        var seen = 0
        while (low < 255 && seen + luminance[low] <= clip) seen += luminance[low++]
        var high = 255
        seen = 0
        while (high > low && seen + luminance[high] <= clip) seen += luminance[high--]
        if (high - low < 8) return Adjustments(saturation = SATURATION)
        val k = (255f / (high - low)).coerceAtMost(1f + MAX_CONTRAST)
        // With out = k·(in − 128) + 128 + brightness·128, stretch around the middle of the photo's
        // range: uncapped, low and high land on black and white; capped, both ends stretch
        // evenly instead of the whole photo darkening or brightening.
        val mid = (low + high) / 2f
        val brightness = (-k * (mid - 128f) / 128f).coerceIn(-MAX_BRIGHTNESS, MAX_BRIGHTNESS)
        return Adjustments(brightness = round2(brightness), contrast = round2(k - 1f), saturation = SATURATION)
    }

    private fun round2(v: Float) = (v * 100).roundToInt() / 100f
}

/** Undo and redo over [EditState]s. */
data class EditHistory(
    val states: List<EditState> = listOf(EditState()),
    val index: Int = 0,
) {
    val current get() = states[index]
    val canUndo get() = index > 0
    val canRedo get() = index < states.lastIndex

    /** Adds [state] after the current one, dropping anything that was undone. */
    fun push(state: EditState): EditHistory = if (state == current) this else EditHistory(states.take(index + 1) + state, index + 1)

    fun undo() = if (canUndo) copy(index = index - 1) else this

    fun redo() = if (canRedo) copy(index = index + 1) else this
}
