package io.github.michaelmassoni.tessera.music

import android.app.SearchManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * "Play Juniper on Music" from the Assistant or another app (MEDIA_PLAY_FROM_SEARCH): hands the
 * words to the playback service, which finds the album, artist or songs (as for Android Auto's
 * voice requests), then closes. It shows nothing.
 */
class PlayFromSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { controller ->
                controller.setMediaItem(request(query))
                controller.prepare()
                controller.play()
                controller.release()
            }
        }, ContextCompat.getMainExecutor(this))
        finish()
    }
}

/** An item that's only a search request, resolved by the playback service. */
private fun request(query: String): MediaItem {
    val request =
        MediaItem.RequestMetadata
            .Builder()
            .setSearchQuery(query)
            .build()
    return MediaItem
        .Builder()
        .setRequestMetadata(request)
        .build()
}
