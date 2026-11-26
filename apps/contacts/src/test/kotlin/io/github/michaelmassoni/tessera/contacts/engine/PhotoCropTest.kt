package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCropTest {
    @Test
    fun `a landscape picture keeps its centre, scaled to the display photo size`() {
        assertEquals(PhotoCrop.Crop(left = 500, top = 0, side = 3000, target = 720), PhotoCrop.square(4000, 3000))
    }

    @Test
    fun `a portrait picture is cropped top and bottom`() {
        assertEquals(PhotoCrop.Crop(left = 0, top = 150, side = 300, target = 300), PhotoCrop.square(300, 600))
    }

    @Test
    fun `a small square is kept as it is`() {
        assertEquals(PhotoCrop.Crop(0, 0, 200, 200), PhotoCrop.square(200, 200))
    }
}
