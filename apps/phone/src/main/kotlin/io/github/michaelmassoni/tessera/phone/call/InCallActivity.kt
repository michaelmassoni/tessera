package io.github.michaelmassoni.tessera.phone.call

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.phone.engine.Proximity
import io.github.michaelmassoni.tessera.phone.ui.InCallActions
import io.github.michaelmassoni.tessera.phone.ui.InCallScreen

/** The in-call screen, shown over the lock screen. Finishes when there are no calls left. */
class InCallActivity : ComponentActivity() {
    /** Turns the screen off when the phone is held to the ear, so a cheek can't press buttons. */
    private val proximity: PowerManager.WakeLock? by lazy {
        getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tessera:in-call")
    }

    private fun setProximity(on: Boolean) {
        val lock = proximity ?: return
        if (on && !lock.isHeld) {
            lock.acquire(MAX_CALL_MS)
        } else if (!on && lock.isHeld) {
            // Wait for the phone to leave the ear before the screen comes back.
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        answerFrom(intent)
    }

    private fun answerFrom(intent: android.content.Intent) {
        if (intent.action ==
            TesseraInCallService.ACTION_ANSWER
        ) {
            intent.getStringExtra(TesseraInCallService.EXTRA_ID)?.let(CallManager::answer)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        answerFrom(intent)
        val actions =
            object : InCallActions {
                override fun answer(id: String) {
                    CallManager.answer(id)
                }

                override fun endAndAnswer(id: String) {
                    CallManager.endAndAnswer(id)
                }

                override fun decline(id: String) {
                    CallManager.reject(id)
                }

                override fun declineWithMessage(
                    id: String,
                    message: String,
                ) {
                    CallManager.rejectWithMessage(id, message)
                }

                override fun hangUp(id: String) {
                    CallManager.hangUp(id)
                }

                override fun chooseSim(
                    id: String,
                    index: Int,
                ) {
                    CallManager.chooseSim(id, index)
                }

                override fun hold(
                    id: String,
                    on: Boolean,
                ) {
                    CallManager.hold(id, on)
                }

                override fun mute(on: Boolean) {
                    CallManager.audioControl?.mute(on)
                }

                override fun route(id: String) {
                    CallManager.audioControl?.route(id)
                }

                override fun dtmf(
                    id: String,
                    digit: Char,
                ) = CallManager.dtmf(id, digit)
            }
        setTesseraContent {
            val calls by CallManager.state.collectAsStateWithLifecycle()
            val audio by CallManager.audio.collectAsStateWithLifecycle()
            LaunchedEffect(calls, audio.current) { setProximity(Proximity.wanted(calls.any { it.live }, audio.current)) }
            LaunchedEffect(calls) {
                if (calls.none { !it.ended }) {
                    kotlinx.coroutines.delay(800)
                    if (CallManager.state.value.none { !it.ended }) finish()
                }
            }
            InCallScreen(calls, audio, actions)
        }
    }
}

/** The proximity lock's safety timeout; it's released when the call ends long before this. */
private const val MAX_CALL_MS = 6 * 60 * 60 * 1000L
