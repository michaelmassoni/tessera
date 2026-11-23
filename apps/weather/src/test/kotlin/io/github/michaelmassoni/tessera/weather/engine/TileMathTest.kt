package io.github.michaelmassoni.tessera.weather.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TileMathTest {
    @Test
    fun `visibility bands`() {
        assertEquals(VisibilityBand.VeryPoor, TileMath.visibility(800.0))
        assertEquals(VisibilityBand.Poor, TileMath.visibility(1_000.0))
        assertEquals(VisibilityBand.Moderate, TileMath.visibility(9_999.0))
        assertEquals(VisibilityBand.Good, TileMath.visibility(16_100.0))
    }

    @Test
    fun `gauges stay on their dials`() {
        assertEquals(0f, TileMath.pressure(940.0))
        assertEquals(0.488f, TileMath.pressure(1008.8), 0.001f)
        assertEquals(1f, TileMath.pressure(1080.0))
        assertEquals(0.5f, TileMath.aqi(150))
        assertEquals(1f, TileMath.aqi(420))
    }

    @Test
    fun `sun moves from sunrise to sunset`() {
        assertEquals(0f, TileMath.sun(now = 50, rise = 100, set = 200))
        assertEquals(0.25f, TileMath.sun(now = 125, rise = 100, set = 200))
        assertEquals(1f, TileMath.sun(now = 300, rise = 100, set = 200))
        assertEquals(0f, TileMath.sun(now = 150, rise = 200, set = 200))
    }

    @Test
    fun `wind blows the opposite way to where it comes from`() {
        assertEquals(180, TileMath.towards(0))
        assertEquals(45, TileMath.towards(225))
        assertEquals(90, TileMath.towards(-90))
    }
}
