package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendingTest {
    private val now = 1_000_000L
    private val old = Message("s1", "earlier", now - 600_000, outgoing = false)

    @Test fun `a sending message stays until its stored copy arrives`() {
        val shown = Sending.message("hi", emptyList(), now)
        val merged = Sending.merge(listOf(old, shown), listOf(old), now + 1_000)
        assertEquals(listOf("s1", shown.id), merged.map { it.id })
    }

    @Test fun `the stored copy takes over the shown key`() {
        val shown = Sending.message("hi", emptyList(), now)
        val stored = Message("s2", "hi", now + 400, outgoing = true)
        val merged = Sending.merge(listOf(old, shown), listOf(old, stored), now + 1_000)
        assertEquals(listOf("s1", "s2"), merged.map { it.id })
        assertEquals(shown.key, merged.last().key)
    }

    @Test fun `two identical texts match one stored copy each`() {
        val a = Sending.message("ok", emptyList(), now)
        val b = Sending.message("ok", emptyList(), now + 10)
        val stored = Message("s2", "ok", now + 50, outgoing = true)
        val merged = Sending.merge(listOf(a, b), listOf(stored), now + 100)
        assertEquals(2, merged.size)
        assertEquals(setOf(a.key, b.key), merged.map { it.key }.toSet())
    }

    @Test fun `a shown message that never arrives is dropped after the timeout`() {
        val shown = Sending.message("lost", emptyList(), now)
        assertTrue(Sending.merge(listOf(shown), emptyList(), now + Sending.TIMEOUT_MILLIS + 1).isEmpty())
    }

    @Test fun `received messages with the same text don't match`() {
        val shown = Sending.message("hi", emptyList(), now)
        val incoming = Message("s3", "hi", now + 100, outgoing = false)
        val merged = Sending.merge(listOf(shown), listOf(incoming), now + 200)
        assertEquals(2, merged.size)
    }
}
