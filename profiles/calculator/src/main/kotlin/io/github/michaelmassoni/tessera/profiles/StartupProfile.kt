package io.github.michaelmassoni.tessera.profiles

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records what a cold start of the app runs, so it is compiled ahead of time on install. */
@RunWith(AndroidJUnit4::class)
class StartupProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        val app = InstrumentationRegistry.getArguments().getString("app")
        rule.collect(packageName = "io.github.michaelmassoni.tessera.$app", includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
        }
    }
}
