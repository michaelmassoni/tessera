package io.github.michaelmassoni.tessera.phone.call

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallFocusTest {
    private fun call(
        id: String,
        state: Int,
        child: Boolean = false,
    ) = CallInfo(id, "040000000$id", null, null, state, 0, incoming = true, child = child)

    @Test
    fun `one call is the call`() {
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_ACTIVE)))!!
        assertEquals("a", focus.main.id)
        assertNull(focus.other)
    }

    @Test
    fun `a second call ringing takes over, with the call you're on as the other`() {
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_ACTIVE), call("b", Call.STATE_RINGING)))!!
        assertEquals("b", focus.main.id)
        assertEquals("a", focus.other?.id)
    }

    @Test
    fun `after answering, the new active call is shown and the first is on hold`() {
        // The bug: the first call (now on hold, first in the list) was shown instead.
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_HOLDING), call("b", Call.STATE_ACTIVE)))!!
        assertEquals("b", focus.main.id)
        assertEquals("a", focus.other?.id)
    }

    @Test
    fun `while swapping, a call being dialled or connecting beats the held one`() {
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_HOLDING), call("b", Call.STATE_DIALING)))!!
        assertEquals("b", focus.main.id)
    }

    @Test
    fun `when the call you're on ends, the held one is shown on its own`() {
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_HOLDING), call("b", Call.STATE_DISCONNECTED)))!!
        assertEquals("a", focus.main.id)
        assertNull(focus.other)
    }

    @Test
    fun `with every call over, the last one shows as ended`() {
        val focus = CallFocus.pick(listOf(call("a", Call.STATE_DISCONNECTED), call("b", Call.STATE_DISCONNECTED)))!!
        assertEquals("b", focus.main.id)
    }

    @Test
    fun `calls inside a conference are left out`() {
        val focus =
            CallFocus.pick(
                listOf(
                    call("conf", Call.STATE_ACTIVE),
                    call("x", Call.STATE_ACTIVE, child = true),
                    call("y", Call.STATE_ACTIVE, child = true),
                ),
            )!!
        assertEquals("conf", focus.main.id)
        assertNull(focus.other)
    }

    @Test
    fun `no calls, nothing`() {
        assertNull(CallFocus.pick(emptyList()))
    }
}
