package io.github.michaelmassoni.tessera.core.settings

import kotlin.math.abs
import kotlin.math.roundToInt

/** Hue 0–360, saturation and value 0–1. */
data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

/** Colour conversions for the icon colour picker, on plain ARGB ints so they can be unit tested. */
object ColorMath {
    fun toHsv(argb: Int): Hsv {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue =
            when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta).mod(6f))
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
        val saturation = if (max == 0f) 0f else delta / max
        return Hsv(hue, saturation, max)
    }

    fun fromHsv(hsv: Hsv): Int {
        val h = hsv.hue.mod(360f)
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs((h / 60f).mod(2f) - 1))
        val m = v - c
        val (r, g, b) =
            when {
                h < 60 -> Triple(c, x, 0f)
                h < 120 -> Triple(x, c, 0f)
                h < 180 -> Triple(0f, c, x)
                h < 240 -> Triple(0f, x, c)
                h < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        fun channel(f: Float) = ((f + m) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** "#3E5F8A". */
    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Accepts "3e5f8a", "#3E5F8A" or the short "#fff" form. Returns an opaque ARGB colour or null. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        val full =
            when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("")
                6 -> hex
                else -> return null
            }
        val rgb = full.toIntOrNull(16) ?: return null
        return (0xFF shl 24) or rgb
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }

    /** WCAG contrast ratio, 1 to 21. Used to warn about icons that would be hard to see. */
    fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
