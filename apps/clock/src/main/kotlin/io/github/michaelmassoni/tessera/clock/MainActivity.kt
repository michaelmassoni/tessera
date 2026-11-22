package io.github.michaelmassoni.tessera.clock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.clock.ringing.Ringing
import io.github.michaelmassoni.tessera.clock.ringing.RingingService
import io.github.michaelmassoni.tessera.clock.ui.ClockRoot
import io.github.michaelmassoni.tessera.clock.ui.ClockViewModel
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import java.time.DayOfWeek
import java.util.Calendar
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val viewModel: ClockViewModel by viewModels { viewModelFactory { initializer { ClockViewModel(graph) } } }
    private var tab by mutableIntStateOf(TAB_ALARMS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && handleIntent(intent)) return
        setTesseraContent {
            SetupGate(clockSetup) {
                ClockRoot(viewModel, initialTab = tab, onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Handles the standard AlarmClock intents other apps and assistants use.
     * @return true if the activity finished because the caller asked to skip the UI.
     */
    private fun handleIntent(intent: Intent): Boolean {
        tab = intent.getIntExtra(EXTRA_TAB, tab)
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                tab = TAB_ALARMS
                if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
                    val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0).coerceIn(0, 23)
                    val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)
                    val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
                    val vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true)
                    val days =
                        intent
                            .getIntegerArrayListExtra(
                                AlarmClock.EXTRA_DAYS,
                            ).orEmpty()
                            .mapNotNull(::calendarDay)
                            .toSet()
                    thread { graph.alarms.add(hour, minute, label, days, vibrate) }
                    if (skipUi) return finishNow()
                }
            }

            AlarmClock.ACTION_SET_TIMER -> {
                tab = TAB_TIMER
                val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                if (seconds in 1..(24 * 3600)) {
                    val label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty()
                    thread { graph.timers.create(seconds * 1000L, label) }
                    if (skipUi) return finishNow()
                }
            }

            AlarmClock.ACTION_SHOW_ALARMS -> {
                tab = TAB_ALARMS
            }

            AlarmClock.ACTION_SHOW_TIMERS -> {
                tab = TAB_TIMER
            }

            AlarmClock.ACTION_DISMISS_ALARM, AlarmClock.ACTION_SNOOZE_ALARM -> {
                if (graph.ringing.value is Ringing.Alarm) {
                    val action =
                        if (intent.action ==
                            AlarmClock.ACTION_SNOOZE_ALARM
                        ) {
                            RingingService.ACTION_SNOOZE
                        } else {
                            RingingService.ACTION_DISMISS
                        }
                    startService(RingingService.command(this, action))
                }
                return finishNow()
            }
        }
        return false
    }

    private fun finishNow(): Boolean {
        finish()
        return true
    }

    private fun calendarDay(day: Int): DayOfWeek? =
        when (day) {
            Calendar.MONDAY -> DayOfWeek.MONDAY
            Calendar.TUESDAY -> DayOfWeek.TUESDAY
            Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            Calendar.THURSDAY -> DayOfWeek.THURSDAY
            Calendar.FRIDAY -> DayOfWeek.FRIDAY
            Calendar.SATURDAY -> DayOfWeek.SATURDAY
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            else -> null
        }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ALARMS = 0
        const val TAB_WORLD = 1
        const val TAB_TIMER = 2
        const val TAB_STOPWATCH = 3
    }
}
