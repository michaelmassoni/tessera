package io.github.michaelmassoni.tessera.gallery.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTargetTest {
    private fun item(
        video: Boolean = false,
        path: String = "DCIM/Camera/",
        mime: String = if (video) "video/mp4" else "image/jpeg",
    ) = MediaItem(1, "content://1", video, mime, "a", 0, 1, "Camera", relativePath = path)

    @Test fun `new albums go under Pictures, bad names are refused`() {
        assertEquals("Pictures/Trip/", AlbumTarget.newAlbum("  Trip "))
        assertNull(AlbumTarget.newAlbum(" "))
        assertNull(AlbumTarget.newAlbum("a/b"))
        assertNull(AlbumTarget.newAlbum(".."))
    }

    @Test fun `media types only go where MediaStore allows`() {
        assertTrue(AlbumTarget.allowed(item(), "Pictures/Trip/"))
        assertFalse(AlbumTarget.allowed(item(), "Movies/Clips/"))
        assertTrue(AlbumTarget.allowed(item(video = true), "Movies/Clips/"))
        assertTrue(AlbumTarget.allowed(item(), "Download/"))
        assertFalse(AlbumTarget.allowed(item(), "Music/"))
    }

    @Test fun `moving skips items already there, and rotation cycles`() {
        assertEquals(1, AlbumTarget.toMove(listOf(item(path = "Pictures/Trip/"), item()), "pictures/trip/").size)
        assertEquals(listOf(6, 3, 8, 1), generateSequence(1) { AlbumTarget.rotateClockwise(it) }.drop(1).take(4).toList())
        assertTrue(AlbumTarget.canRotate(item()))
        assertFalse(AlbumTarget.canRotate(item(mime = "image/heic")))
    }
}
