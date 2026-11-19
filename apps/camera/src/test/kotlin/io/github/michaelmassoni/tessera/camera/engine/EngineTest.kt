package io.github.michaelmassoni.tessera.camera.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class EngineTest {
    private val t = LocalDateTime.of(2026, 11, 19, 10, 30, 15)

    @Test fun `file names are timestamped and never clash`() {
        assertEquals("IMG_20261119_103015", FileNames.name(Mode.Photo, t))
        assertEquals("VID_20261119_103015", FileNames.name(Mode.Video, t))
        assertEquals("IMG_20261119_103015_1", FileNames.name(Mode.Photo, t, setOf("IMG_20261119_103015")))
        assertEquals("IMG_20261119_103015_2", FileNames.name(Mode.Photo, t, setOf("IMG_20261119_103015", "IMG_20261119_103015_1")))
    }

    @Test fun `flash cycles off-auto-on for photos and toggles the torch for video`() {
        assertEquals(listOf(Flash.Auto, Flash.On, Flash.Off), listOf(Flash.Off.next(false), Flash.Auto.next(false), Flash.On.next(false)))
        assertEquals(Flash.On, Flash.Off.next(true))
        assertEquals(Flash.Off, Flash.Auto.next(true))
    }

    @Test fun `zoom steps fit the lens and labels are short`() {
        assertEquals(listOf(1f, 2f, 5f), ZoomSteps.steps(1f, 8f))
        assertEquals(listOf(0.5f, 1f, 2f, 5f, 10f), ZoomSteps.steps(0.5f, 30f))
        assertEquals(listOf(1f), ZoomSteps.steps(1f, 1f))
        assertEquals("1×", ZoomSteps.label(1f))
        assertEquals("0.5×", ZoomSteps.label(0.5f))
        assertEquals("2.5×", ZoomSteps.label(2.49f))
        assertEquals("0:07", formatElapsed(7_500))
        assertEquals("1:02:03", formatElapsed(3_723_000))
    }
}
