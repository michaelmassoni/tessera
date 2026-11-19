package io.github.michaelmassoni.tessera.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppsTest {
    private fun app(
        label: String,
        pkg: String = label.lowercase(),
        work: Boolean = false,
    ) = App(AppKey(pkg, "$pkg.Main", if (work) 10 else 0), label, work)

    private val apps =
        listOf(
            app("Clock"),
            app("Calendar"),
            app("Éclair Notes", "eclair"),
            app("Google Chrome", "chrome"),
            app("Calendar", "cal2", work = true),
            app("camera"),
        )

    @Test fun `keys round-trip and reject garbage`() {
        val key = AppKey("a.b", "a.b.Main", 10)
        assertEquals(key, AppKey.parse(key.toString()))
        assertNull(AppKey.parse("a.b/a.b.Main"))
        assertNull(AppKey.parse("a.b/a.b.Main/x"))
        assertNull(AppKey.parse("//1"))
    }

    @Test fun `sorting uses collation and puts personal before work`() {
        val sorted = AppIndex.sorted(apps, Locale.ENGLISH)
        assertEquals(listOf("Calendar", "Calendar", "camera", "Clock", "Éclair Notes", "Google Chrome"), sorted.map { it.label })
        assertEquals(listOf(false, true), sorted.take(2).map { it.work })
    }

    @Test fun `search ranks label start, then word start, then substring`() {
        assertEquals(
            listOf("Clock", "Calendar", "Calendar", "camera", "Google Chrome", "Éclair Notes"),
            AppIndex.search(apps, "c").map {
                it.label
            },
        )
        assertEquals(listOf("Google Chrome"), AppIndex.search(apps, "chro").map { it.label })
        assertEquals(listOf("Éclair Notes"), AppIndex.search(apps, "eclair").map { it.label })
        assertEquals(listOf("Éclair Notes"), AppIndex.search(apps, "notes").map { it.label })
        assertEquals(listOf("Google Chrome"), AppIndex.search(apps, "hrom").map { it.label })
        assertEquals(apps, AppIndex.search(apps, "  "))
    }

    @Test fun `pins add, cap, move, remove, retain and serialise`() {
        val a = AppKey("a", "a.M", 0)
        val b = AppKey("b", "b.M", 0)
        val c = AppKey("c", "c.M", 10)
        var pins =
            Pins()
                .add(a)
                .add(b)
                .add(c)
                .add(a)
        assertEquals(listOf(a, b, c), pins.keys)
        pins = pins.move(c, -5)
        assertEquals(listOf(c, a, b), pins.keys)
        assertEquals(listOf(c, b), pins.remove(a).keys)
        assertEquals(listOf(a, b), pins.retain(setOf(a, b)).keys)
        assertEquals(pins, Pins.parse(pins.serialize()))
        var full = Pins()
        (1..30).forEach { full = full.add(AppKey("p$it", "M", 0)) }
        assertEquals(Pins.MAX, full.keys.size)
    }
}
