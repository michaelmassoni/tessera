package io.github.michaelmassoni.tessera.phone.call

import android.telecom.Call
import android.telecom.VideoProfile
import io.github.michaelmassoni.tessera.phone.engine.AudioInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the in-call screen shows about one call. */
data class CallInfo(
    val id: String,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val state: Int,
    /** When the call became active (epoch ms), or 0. */
    val connectedAt: Long,
    val incoming: Boolean,
    /** Where an unknown number is from ("Melbourne"), worked out on the phone. */
    val location: String? = null,
    /** Reported as spam on this phone. */
    val spam: Boolean = false,
    /** Part of a conference call (shown as the conference, not on its own). */
    val child: Boolean = false,
) {
    val ringing: Boolean get() = state == Call.STATE_RINGING
    val active: Boolean get() = state == Call.STATE_ACTIVE
    val holding: Boolean get() = state == Call.STATE_HOLDING
    val ended: Boolean get() = state == Call.STATE_DISCONNECTED

    /** On the line or connecting: everything but ringing and over. */
    val live: Boolean get() = !ringing && !ended
}

/**
 * The calls Telecom has given the in-call service, as state for the UI, plus the commands the
 * UI sends back. A process-wide singleton because the service and the activity both use it.
 */
object CallManager {
    private val calls = mutableMapOf<String, Call>()
    private val _state = MutableStateFlow<List<CallInfo>>(emptyList())
    val state: StateFlow<List<CallInfo>> = _state.asStateFlow()

    private val _audio = MutableStateFlow(AudioInfo())
    val audio: StateFlow<AudioInfo> = _audio.asStateFlow()

    /** Set by the service so the UI can change audio routes and mute. */
    var audioControl: AudioControl? = null

    interface AudioControl {
        fun mute(on: Boolean)

        /** Switches call audio to the route with this [AudioRoute.id]. */
        fun route(id: String)
    }

    internal var nameLookup: (String) -> Pair<String, String?>? = { null }

    /** Location and spam report for a number, set by the service. */
    internal var callerLookup: (String) -> Pair<String?, Boolean> = { null to false }
    private val caller = mutableMapOf<String, Pair<String?, Boolean>>()

    private val connectedAt = mutableMapOf<String, Long>()
    private val info = mutableMapOf<String, Pair<String?, String?>>()

    private val callback =
        object : Call.Callback() {
            override fun onStateChanged(
                call: Call,
                state: Int,
            ) = publish()

            override fun onDetailsChanged(
                call: Call,
                details: Call.Details,
            ) = publish()
        }

    fun add(call: Call) {
        val id = idOf(call)
        calls[id] = call
        call.registerCallback(callback)
        val number = numberOf(call)
        info[id] = nameLookup(number)?.let { it.first to it.second } ?: (null to null)
        caller[id] = callerLookup(number)
        publish()
    }

    fun remove(call: Call) {
        val id = idOf(call)
        call.unregisterCallback(callback)
        calls.remove(id)
        connectedAt.remove(id)
        info.remove(id)
        caller.remove(id)
        publish()
    }

    /** Re-reads every call's state (callers that got a state change before this object did). */
    fun refresh() = publish()

    fun setAudio(audio: AudioInfo) {
        _audio.value = audio
    }

    /** Answers [id]. Telecom puts a call you're already on on hold first ("Hold & answer"). */
    fun answer(id: String) = calls[id]?.answer(VideoProfile.STATE_AUDIO_ONLY)

    /** A ringing call to answer once the calls being ended have gone ("End & answer"). */
    private var answerWhenFree: String? = null

    /** Ends every other call, then answers [id] when the line is free. */
    fun endAndAnswer(id: String) {
        val others =
            calls.filterKeys { it != id }.values.filter {
                @Suppress("DEPRECATION")
                it.state != Call.STATE_DISCONNECTED
            }
        if (others.isEmpty()) {
            answer(id)
            return
        }
        answerWhenFree = id
        others.forEach { it.disconnect() }
    }

    fun reject(id: String) = calls[id]?.reject(false, null)

    fun hangUp(id: String) = calls[id]?.disconnect()

    fun hold(
        id: String,
        on: Boolean,
    ) = calls[id]?.let { if (on) it.hold() else it.unhold() }

    fun dtmf(
        id: String,
        digit: Char,
    ) {
        calls[id]?.let {
            it.playDtmfTone(digit)
            it.stopDtmfTone()
        }
    }

    private fun idOf(call: Call) = System.identityHashCode(call).toString()

    @Suppress("DEPRECATION") // getHandle is the supported way on the minimum SDK.
    private fun numberOf(call: Call): String =
        call.details.handle
            ?.schemeSpecificPart
            .orEmpty()

    @Suppress("DEPRECATION") // Call.getState() is still the state on API 31.
    private fun publish() {
        answerWhenFree?.let { id ->
            val ringing = calls[id]?.takeIf { it.state == Call.STATE_RINGING }
            if (ringing == null) {
                answerWhenFree = null
            } else if (calls.none { (other, call) -> other != id && call.state != Call.STATE_DISCONNECTED }) {
                answerWhenFree = null
                ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
        }
        _state.value =
            calls.map { (id, call) ->
                val state = call.state
                if (state == Call.STATE_ACTIVE && id !in connectedAt) connectedAt[id] = System.currentTimeMillis()
                val (name, photo) = info[id] ?: (null to null)
                val (location, spam) = caller[id] ?: (null to false)
                CallInfo(
                    id = id,
                    number = numberOf(call),
                    name = name,
                    photoUri = photo,
                    state = state,
                    connectedAt = connectedAt[id] ?: 0,
                    incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING,
                    location = location.takeIf { name == null },
                    spam = spam && name == null,
                    child = call.parent != null,
                )
            }
    }
}
