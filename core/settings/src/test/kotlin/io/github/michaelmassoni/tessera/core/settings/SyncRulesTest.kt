package io.github.michaelmassoni.tessera.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRulesTest {
    private val dark = ThemeSettings(mode = ThemeMode.Dark)
    private val light = ThemeSettings(mode = ThemeMode.Light, dynamicColor = false)

    @Test fun `older or equal changes are ignored`() {
        val local = SharedState(dark, syncAll = true, updatedAt = 10)
        assertNull(SyncRules.receive(local, SharedState(light, syncAll = true, updatedAt = 10)))
        assertNull(SyncRules.receive(local, SharedState(light, syncAll = true, updatedAt = 9)))
    }

    @Test fun `a newer synced state is adopted, including the sync flag`() {
        val local = SharedState(dark, syncAll = false, updatedAt = 10)
        val remote = SharedState(light, syncAll = true, updatedAt = 11)
        assertEquals(remote, SyncRules.receive(local, remote))
    }

    @Test fun `turning sync off elsewhere turns it off here but keeps this app's theme`() {
        val local = SharedState(dark, syncAll = true, updatedAt = 10)
        val remote = SharedState(light, syncAll = false, updatedAt = 11)
        assertEquals(SharedState(dark, syncAll = false, updatedAt = 11), SyncRules.receive(local, remote))
    }

    @Test fun `pull adopts the newest state that has sync on`() {
        val local = SharedState(updatedAt = 0)
        val remotes =
            listOf(
                SharedState(dark, syncAll = true, updatedAt = 5),
                SharedState(light, syncAll = false, updatedAt = 50),
                SharedState(light, syncAll = true, updatedAt = 20),
            )
        assertEquals(remotes[2], SyncRules.pull(local, remotes))
    }

    @Test fun `pull keeps the local state when no app syncs`() {
        assertNull(SyncRules.pull(SharedState(), listOf(SharedState(dark, syncAll = false, updatedAt = 99))))
        assertNull(SyncRules.pull(SharedState(), emptyList()))
    }

    @Test fun `pull ignores a shared theme older than a local change`() {
        val local = SharedState(dark, syncAll = false, updatedAt = 30)
        assertNull(SyncRules.pull(local, listOf(SharedState(light, syncAll = true, updatedAt = 20))))
    }
}
