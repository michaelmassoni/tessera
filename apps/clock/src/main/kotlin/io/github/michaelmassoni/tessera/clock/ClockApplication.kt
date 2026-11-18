package io.github.michaelmassoni.tessera.clock

import android.app.Application
import android.content.Context
import io.github.michaelmassoni.tessera.clock.data.AppGraph

class ClockApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.notifications.createChannels()
    }
}

val Context.graph: AppGraph get() = (applicationContext as ClockApplication).graph
