package io.github.michaelmassoni.tessera.music.widget

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import io.github.michaelmassoni.tessera.music.MainActivity
import io.github.michaelmassoni.tessera.music.PlaybackService
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What's playing, as the widget shows it. Written by [PlaybackService], read by the widget. */
object NowPlaying {
    private const val PREFS = "now_playing"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class Snapshot(
        val title: String,
        val artist: String,
        val albumId: Long,
        val isPlaying: Boolean,
    )

    fun publish(
        context: Context,
        player: Player,
    ) {
        val meta = player.mediaMetadata
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasMedia = player.mediaItemCount > 0
        prefs.edit {
            if (!hasMedia) {
                clear()
            } else {
                putString("title", meta.title?.toString().orEmpty())
                putString("artist", meta.artist?.toString().orEmpty())
                putLong("album", meta.extras?.getLong(PlaybackService.EXTRA_ALBUM_ID) ?: 0)
                putBoolean("playing", player.isPlaying)
            }
        }
        val app = context.applicationContext
        scope.launch {
            if (GlanceAppWidgetManager(app).getGlanceIds(NowPlayingWidget::class.java).isNotEmpty()) NowPlayingWidget().updateAll(app)
        }
    }

    fun read(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString("title", null) ?: return null
        return Snapshot(title, prefs.getString("artist", "").orEmpty(), prefs.getLong("album", 0), prefs.getBoolean("playing", false))
    }
}

/** Album art, title and artist, with previous, play/pause and next. */
class NowPlayingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Glance calls this on the main thread: read the file and the art off it.
        val (now, art) =
            withContext(Dispatchers.IO) {
                val now = NowPlaying.read(context)
                now to now?.albumId?.takeIf { it > 0 }?.let { albumArt(context, it) }
            }
        provideContent { GlanceTheme { Content(context, now, art) } }
    }

    private fun albumArt(
        context: Context,
        albumId: Long,
    ): Bitmap? =
        runCatching {
            ServerLibrary.artFor(context, albumId)?.let { return ServerLibrary.art(context, it, ART_PX) }
            context.contentResolver.loadThumbnail(
                ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId),
                Size(ART_PX, ART_PX),
                null,
            )
        }.getOrNull()

    @Composable
    private fun Content(
        context: Context,
        now: NowPlaying.Snapshot?,
        art: Bitmap?,
    ) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Row(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(open),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(72.dp).cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(ImageProvider(art), contentDescription = null, modifier = GlanceModifier.size(72.dp).cornerRadius(16.dp))
                } else {
                    Image(
                        ImageProvider(DesignR.drawable.ic_music_note),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                        modifier = GlanceModifier.size(32.dp),
                    )
                }
            }
            Column(GlanceModifier.defaultWeight().padding(horizontal = 12.dp)) {
                Text(
                    now?.title ?: context.getString(R.string.widget_nothing_playing),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    now?.artist?.ifBlank { context.getString(R.string.unknown_artist) } ?: context.getString(R.string.widget_tap_to_open),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Control(context, DesignR.drawable.ic_skip_previous, R.string.previous, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    Control(
                        context,
                        if (now?.isPlaying == true) DesignR.drawable.ic_pause else DesignR.drawable.ic_play_arrow_filled,
                        if (now?.isPlaying == true) R.string.pause else R.string.play,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    )
                    Control(context, DesignR.drawable.ic_skip_next, R.string.next, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }

    @Composable
    private fun Control(
        context: Context,
        icon: Int,
        label: Int,
        keyCode: Int,
    ) {
        Box(
            GlanceModifier
                .size(48.dp)
                .cornerRadius(24.dp)
                .clickable(mediaButton(context, keyCode))
                .semantics { contentDescription = context.getString(label) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(28.dp),
            )
        }
    }

    /** A media button press, sent to Media3's receiver: it starts the service and resumes the saved queue if needed. */
    @OptIn(UnstableApi::class)
    private fun mediaButton(
        context: Context,
        keyCode: Int,
    ): Action =
        actionSendBroadcast(
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
        )

    private companion object {
        const val ART_PX = 256
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
