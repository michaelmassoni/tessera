package io.github.michaelmassoni.tessera.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test fun `primary colours convert both ways`() {
        for ((argb, hue) in listOf(0xFFFF0000.toInt() to 0f, 0xFF00FF00.toInt() to 120f, 0xFF0000FF.toInt() to 240f)) {
            val hsv = ColorMath.toHsv(argb)
            assertEquals(hue, hsv.hue, 0.01f)
            assertEquals(1f, hsv.saturation, 0.001f)
            assertEquals(1f, hsv.value, 0.001f)
            assertEquals(argb, ColorMath.fromHsv(hsv))
        }
    }

    @Test fun `every 8-bit grey and a spread of colours round-trip exactly`() {
        for (v in 0..255) {
            val grey = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            assertEquals(grey, ColorMath.fromHsv(ColorMath.toHsv(grey)))
        }
        for (rgb in 0..0xFFFFFF step 9973) {
            val argb = (0xFF shl 24) or rgb
            assertEquals(ColorMath.toHex(argb), argb, ColorMath.fromHsv(ColorMath.toHsv(argb)))
        }
    }

    @Test fun `hue wraps and out-of-range values are clamped`() {
        assertEquals(0xFFFF0000.toInt(), ColorMath.fromHsv(Hsv(360f, 1f, 1f)))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.fromHsv(Hsv(10f, -1f, 2f)))
    }

    @Test fun `hex formats and parses`() {
        assertEquals("#3E5F8A", ColorMath.toHex(0xFF3E5F8A.toInt()))
        assertEquals(0xFF3E5F8A.toInt(), ColorMath.parseHex("#3e5f8a"))
        assertEquals(0xFF3E5F8A.toInt(), ColorMath.parseHex(" 3E5F8A "))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.parseHex("#fff"))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertNull(ColorMath.parseHex(""))
    }

    @Test fun `contrast matches WCAG reference values`() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        assertTrue(ColorMath.contrast(0xFFFFFFFF.toInt(), 0xFF3E5F8A.toInt()) > 4.5)
    }
}
