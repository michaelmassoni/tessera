package io.github.michaelmassoni.tessera.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTest {
    private fun assertPoint(
        expected: Pair<Float, Float>,
        actual: Pair<Float, Float>,
    ) {
        assertEquals(expected.first, actual.first, 0.01f)
        assertEquals(expected.second, actual.second, 0.01f)
    }

    @Test fun `quarter turns move corners where a clockwise turn puts them`() {
        // A 400×300 photo turned once is 300×400; its top-left corner ends top-right.
        val once = EditGeometry.toFrame(400, 300, EditState(quarterTurns = 1))
        assertPoint(300f to 0f, once.map(0f, 0f))
        assertPoint(0f to 400f, once.map(400f, 300f))
        val twice = EditGeometry.toFrame(400, 300, EditState(quarterTurns = 2))
        assertPoint(400f to 300f, twice.map(0f, 0f))
        val thrice = EditGeometry.toFrame(400, 300, EditState(quarterTurns = 3))
        assertPoint(0f to 400f, thrice.map(0f, 0f))
    }

    @Test fun `flip mirrors after turning`() {
        val t = EditGeometry.toFrame(400, 300, EditState(flipped = true))
        assertPoint(400f to 0f, t.map(0f, 0f))
    }

    @Test fun `straightening zooms just enough to hide the corners`() {
        assertEquals(1f, EditGeometry.straightenScale(400f, 300f, 0f), 0.0001f)
        val s = EditGeometry.straightenScale(400f, 300f, 10f)
        // The rotated, scaled photo must still cover each frame corner.
        val t = EditGeometry.toFrame(400, 300, EditState(straighten = 10f))
        val inverse = { x: Float, y: Float ->
            // Map the frame corner back: rotate by -10° about the centre and unscale.
            val r = Math.toRadians(-10.0)
            val cx = x - 200f
            val cy = y - 150f
            Pair(((cx * Math.cos(r) - cy * Math.sin(r)) / s + 200).toFloat(), ((cx * Math.sin(r) + cy * Math.cos(r)) / s + 150).toFloat())
        }
        for ((x, y) in listOf(0f to 0f, 400f to 0f, 0f to 300f, 400f to 300f)) {
            val (sx, sy) = inverse(x, y)
            assertTrue("corner ($x,$y) maps to ($sx,$sy)", sx in -0.5f..400.5f && sy in -0.5f..300.5f)
        }
        assertPoint(200f to 150f, t.map(200f, 150f))
    }

    @Test fun `cropping offsets and sizes the output`() {
        val state = EditState(crop = NormRect(0.25f, 0.5f, 0.75f, 1f))
        val (t, size) = EditGeometry.toOutput(400, 300, state)
        assertEquals(200 to 150, size)
        assertPoint(0f to 0f, t.map(100f, 150f))
    }

    @Test fun `ratios follow the photo's orientation`() {
        assertEquals(4f / 3, Ratio.FourThree.value(1.5f)!!, 0.001f)
        assertEquals(3f / 4, Ratio.FourThree.value(0.75f)!!, 0.001f)
        assertNull(Ratio.Free.value(1.5f))
        // Square in a 4:3 landscape frame: full height, centred.
        val r = EditGeometry.fit(Ratio.Square, 4f / 3)
        assertEquals(0.125f, r.left, 0.001f)
        assertEquals(0.875f, r.right, 0.001f)
        assertEquals(0f, r.top, 0.001f)
        assertEquals(1f, r.bottom, 0.001f)
    }

    @Test fun `dragging a corner keeps a fixed ratio and stays inside`() {
        val start = EditGeometry.fit(Ratio.Square, 1f)
        val r = EditGeometry.drag(start, EditGeometry.Handle.BottomRight, -0.3f, -0.1f, Ratio.Square, 1f)
        assertEquals(r.width, r.height, 0.001f)
        assertEquals(0f, r.left, 0.001f)
        assertEquals(0.7f, r.right, 0.001f)
        // Dragging outward past the frame is clamped.
        val big = EditGeometry.drag(r, EditGeometry.Handle.BottomRight, 2f, 2f, Ratio.Square, 1f)
        assertTrue(big.right <= 1f && big.bottom <= 1f)
        assertEquals(big.width, big.height, 0.001f)
    }

    @Test fun `free crops move edges and respect a minimum size`() {
        val r = EditGeometry.drag(NormRect.Full, EditGeometry.Handle.Left, 0.99f, 0f, Ratio.Free, 1f)
        assertEquals(0.92f, r.left, 0.001f)
        val moved = EditGeometry.drag(NormRect(0.2f, 0.2f, 0.6f, 0.6f), EditGeometry.Handle.Move, 0.9f, -0.9f, Ratio.Free, 1f)
        // Moving stops at the frame's edges without changing size.
        assertEquals(0.6f, moved.left, 0.001f)
        assertEquals(0f, moved.top, 0.001f)
        assertEquals(0.4f, moved.width, 0.001f)
        assertEquals(0.4f, moved.height, 0.001f)
    }

    @Test fun `no adjustments is the identity colour matrix`() {
        val m = EditColor.matrix(Adjustments())
        val (r, g, b) = EditColor.apply(m, 10f, 128f, 240f)
        assertEquals(10f, r, 0.01f)
        assertEquals(128f, g, 0.01f)
        assertEquals(240f, b, 0.01f)
    }

    @Test fun `brightness, contrast, saturation and warmth go the right way`() {
        val grey = 100f
        assertTrue(EditColor.apply(EditColor.matrix(Adjustments(brightness = 0.5f)), grey, grey, grey).first > grey)
        // Contrast pushes away from mid-grey.
        assertTrue(EditColor.apply(EditColor.matrix(Adjustments(contrast = 0.5f)), grey, grey, grey).first < grey)
        // Desaturated, a colour moves towards grey.
        val (r, _, b) = EditColor.apply(EditColor.matrix(Adjustments(saturation = -1f)), 200f, 100f, 50f)
        assertTrue(r < 200f && b > 50f)
        val (wr, _, wb) = EditColor.apply(EditColor.matrix(Adjustments(warmth = 1f)), grey, grey, grey)
        assertTrue(wr > grey && wb < grey)
    }

    @Test fun `auto enhance stretches a dull photo and leaves a full range alone`() {
        val dull = IntArray(256).also { h -> for (v in 80..160) h[v] = 100 }
        val a = AutoEnhance.from(dull)
        assertTrue(a.contrast > 0f)
        val m = EditColor.matrix(a.copy(saturation = 0f))
        // Its range widens evenly around its middle, which lands on mid-grey.
        assertTrue(EditColor.apply(m, 80f, 80f, 80f).first < 70f)
        assertTrue(EditColor.apply(m, 160f, 160f, 160f).first > 170f)
        assertEquals(128f, EditColor.apply(m, 120f, 120f, 120f).first, 1f)
        // A bright, flat photo isn't pushed dark.
        val bright = IntArray(256).also { h -> for (v in 170..230) h[v] = 100 }
        val b = AutoEnhance.from(bright)
        assertTrue(b.brightness >= -0.4f)
        assertTrue(EditColor.apply(EditColor.matrix(b.copy(saturation = 0f)), 200f, 200f, 200f).first > 150f)
        val full = IntArray(256) { 100 }
        assertEquals(0f, AutoEnhance.from(full).contrast, 0.01f)
        assertEquals(Adjustments(), AutoEnhance.from(IntArray(256)))
    }

    @Test fun `history undoes and redoes, and a new edit drops the redo branch`() {
        var h = EditHistory()
        h = h.push(EditState(quarterTurns = 1)).push(EditState(quarterTurns = 2))
        h = h.undo()
        assertEquals(1, h.current.quarterTurns)
        assertTrue(h.canRedo)
        h = h.push(EditState(flipped = true))
        assertFalse(h.canRedo)
        assertEquals(3, h.states.size)
        assertTrue(EditState().isIdentity)
        assertFalse(h.current.isIdentity)
    }
}
