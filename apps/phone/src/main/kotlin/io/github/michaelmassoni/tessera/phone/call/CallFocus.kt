package io.github.michaelmassoni.tessera.phone.call

import android.telecom.Call

/** Which call the in-call screen is about, and the other call there may be (call waiting). */
data class Focus(
    val main: CallInfo,
    /**
     * Another call still on the line: the one you're on while [main] rings, or the one on hold
     * while you talk on [main].
     */
    val other: CallInfo? = null,
)

object CallFocus {
    /**
     * A ringing call takes over (newest first), with the call you're already on as [Focus.other].
     * Otherwise the call you're talking on, then one being dialled, then the newest on hold;
     * another live call is [Focus.other]. With every call over, the last one (its "Call ended").
     * Calls inside a conference are left out: the conference stands for them.
     */
    fun pick(calls: List<CallInfo>): Focus? {
        val shown = calls.filterNot { it.child }
        val live = shown.filter { it.live }
        shown.lastOrNull { it.ringing }?.let { ringing ->
            return Focus(ringing, live.firstOrNull { it.active } ?: live.lastOrNull())
        }
        val main =
            live.lastOrNull { it.active }
                ?: live.lastOrNull { it.state in DIALLING }
                ?: live.lastOrNull()
                ?: return shown.lastOrNull()?.let { Focus(it) }
        return Focus(main, live.lastOrNull { it.id != main.id })
    }

    private val DIALLING =
        setOf(Call.STATE_NEW, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_PULLING_CALL)
}
