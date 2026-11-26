package io.github.michaelmassoni.tessera.feeds.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeActionTest {
    @Test
    fun `stored names and fallbacks`() {
        assertEquals(SwipeAction.ToggleStar, SwipeAction.of("ToggleStar", SwipeAction.ToggleRead))
        assertEquals(SwipeAction.None, SwipeAction.of("None", SwipeAction.ToggleRead))
        assertEquals(SwipeAction.ToggleRead, SwipeAction.of(null, SwipeAction.ToggleRead))
        assertEquals(SwipeAction.ToggleStar, SwipeAction.of("Archive", SwipeAction.ToggleStar))
    }
}
