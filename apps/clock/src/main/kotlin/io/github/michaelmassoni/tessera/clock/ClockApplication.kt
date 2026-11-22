package io.github.michaelmassoni.tessera.clock

import android.app.Application
import android.content.Context
import io.github.michaelmassoni.tessera.clock.data.AppGraph
import io.github.michaelmassoni.tessera.core.settings.AppBackup
import kotlin.concurrent.thread

class ClockApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.notifications.createChannels()
        // After a restore from a backup, the restored alarms aren't in AlarmManager yet.
        if (AppBackup.consumeRestored(this)) thread(name = "clock-restored") { graph.alarms.rescheduleAll() }
    }
}

val Context.graph: AppGraph get() = (applicationContext as ClockApplication).graph
