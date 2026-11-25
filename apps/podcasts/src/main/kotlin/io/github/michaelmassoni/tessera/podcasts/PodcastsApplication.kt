package io.github.michaelmassoni.tessera.podcasts

import android.app.Application
import android.content.Context
import io.github.michaelmassoni.tessera.podcasts.data.Downloads
import io.github.michaelmassoni.tessera.podcasts.data.PodcastStore
import io.github.michaelmassoni.tessera.podcasts.data.Podcasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the one [Podcasts] (and its store) for the app, the player and background work. */
class PodcastsApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val podcasts: Podcasts by lazy { Podcasts(this, PodcastStore(filesDir, scope)) }

    override fun onCreate() {
        super.onCreate()
        Downloads.ensureChannel(this)
    }
}

val Context.podcasts: Podcasts get() = (applicationContext as PodcastsApplication).podcasts
