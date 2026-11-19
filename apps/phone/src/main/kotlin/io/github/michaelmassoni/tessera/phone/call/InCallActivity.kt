package io.github.michaelmassoni.tessera.phone.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.phone.ui.InCallActions
import io.github.michaelmassoni.tessera.phone.ui.InCallScreen

/** The in-call screen, shown over the lock screen. Finishes when there are no calls left. */
class InCallActivity : ComponentActivity() {
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

                override fun decline(id: String) {
                    CallManager.reject(id)
                }

                override fun hangUp(id: String) {
                    CallManager.hangUp(id)
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

                override fun speaker(on: Boolean) {
                    CallManager.audioControl?.speaker(on)
                }

                override fun dtmf(
                    id: String,
                    digit: Char,
                ) = CallManager.dtmf(id, digit)
            }
        setTesseraContent {
            val calls by CallManager.state.collectAsStateWithLifecycle()
            val audio by CallManager.audio.collectAsStateWithLifecycle()
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
