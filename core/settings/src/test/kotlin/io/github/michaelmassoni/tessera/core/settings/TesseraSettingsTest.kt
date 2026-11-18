package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TesseraSettingsTest {
    private class FakePeers : TesseraSettings.Peers {
        val pushed = mutableListOf<SharedState>()
        var states = emptyList<SharedState>()

        override fun readAll() = states

        override fun pushAll(state: SharedState) {
            pushed += state
        }
    }

    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test", Context.MODE_PRIVATE)
    private val peers = FakePeers()
    private val nightModes = mutableListOf<ThemeMode>()
    private var clock = 1000L

    private fun settings() = TesseraSettings(prefs, { nightModes += it }, peers, now = { clock })

    @Before fun clear() = prefs.edit().clear().apply()

    @Test fun `theme changes persist and set the night mode`() {
        settings().setTheme(ThemeSettings(mode = ThemeMode.Dark, pureBlack = true))
        assertEquals(listOf(ThemeMode.Dark), nightModes)
        val reloaded = settings().shared.value
        assertEquals(ThemeSettings(mode = ThemeMode.Dark, pureBlack = true), reloaded.theme)
        assertEquals(1000L, reloaded.updatedAt)
    }

    @Test fun `changes are only pushed while syncing, and turning sync off is pushed too`() {
        val s = settings()
        s.setTheme(ThemeSettings(dynamicColor = false))
        assertTrue(peers.pushed.isEmpty())
        clock = 2000
        s.setSyncAll(true)
        clock = 3000
        s.setTheme(ThemeSettings(mode = ThemeMode.Light))
        clock = 4000
        s.setSyncAll(false)
        assertEquals(listOf(true, true, false), peers.pushed.map { it.syncAll })
        assertEquals(
            ThemeMode.Light,
            peers.pushed
                .last()
                .theme.mode,
        )
    }

    @Test fun `timestamps never go backwards when the clock does`() {
        val s = settings()
        s.setSyncAll(true)
        clock = 10
        s.setTheme(ThemeSettings(mode = ThemeMode.Dark))
        assertEquals(1001L, s.shared.value.updatedAt)
    }

    @Test fun `received and pulled states are applied`() {
        val s = settings()
        assertTrue(s.receive(SharedState(ThemeSettings(mode = ThemeMode.Dark), syncAll = true, updatedAt = 5)))
        assertEquals(listOf(ThemeMode.Dark), nightModes)
        assertFalse(s.receive(SharedState(ThemeSettings(mode = ThemeMode.Light), syncAll = true, updatedAt = 4)))
        peers.states = listOf(SharedState(ThemeSettings(mode = ThemeMode.Light), syncAll = true, updatedAt = 9))
        s.pullFromPeers()
        assertEquals(
            ThemeMode.Light,
            settings()
                .shared.value.theme.mode,
        )
    }

    @Test fun `icon settings persist, and app colours apply until the user picks some`() {
        val s = settings()
        assertEquals(IconSettings(foreground = 1, background = 2), s.icon.value.withAppColors(1, 2))
        s.setIcon(IconSettings(IconStyle.Custom, foreground = 5, background = 6, colorsChosen = true))
        val reloaded = settings().icon.value
        assertEquals(IconSettings(IconStyle.Custom, 5, 6, colorsChosen = true), reloaded)
        assertEquals(reloaded, reloaded.withAppColors(1, 2))
    }

    @Test fun `exactly one launcher alias is enabled for each style`() {
        for (style in IconStyle.entries) {
            val states = LauncherAliases.states("p", style)
            assertEquals(3, states.size)
            assertEquals(1, states.count { it.second })
        }
        assertEquals("p.LauncherDefault", LauncherAliases.className("p", IconStyle.Custom))
        assertEquals("p.LauncherDark", LauncherAliases.className("p", IconStyle.Dark))
    }
}
