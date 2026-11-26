package io.github.michaelmassoni.tessera.podcasts.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.michaelmassoni.tessera.podcasts.MainActivity
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.Library
import io.github.michaelmassoni.tessera.podcasts.engine.NewEpisodeNotices

/** "New episode" notifications after a background refresh (docs/podcasts.md, Notifications). */
object NewEpisodeNotifier {
    private const val CHANNEL = "new_episodes"
    private const val GROUP = "new_episodes"
    private const val SUMMARY_ID = 0x5EA50

    fun notify(
        context: Context,
        added: List<Episode>,
        library: Library,
    ) {
        if (!Prefs.newEpisodeNotifications(context)) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notices = NewEpisodeNotices.from(added, library)
        if (notices.isEmpty()) return
        ensureChannel(context)
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        try {
            notices.forEach { n ->
                val more = n.episodes.size - 1
                manager.notify(
                    n.show.id.hashCode(),
                    NotificationCompat
                        .Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_podcasts)
                        .setContentTitle(n.show.title)
                        .setContentText(
                            if (more > 0) {
                                context.resources.getQuantityString(R.plurals.new_episode_more, more, n.newest.title, more)
                            } else {
                                n.newest.title
                            },
                        ).setContentIntent(open)
                        .setAutoCancel(true)
                        .setGroup(GROUP)
                        .build(),
                )
            }
            if (notices.size > 1) {
                manager.notify(
                    SUMMARY_ID,
                    NotificationCompat
                        .Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_podcasts)
                        .setContentTitle(context.getString(R.string.new_episodes))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .setGroup(GROUP)
                        .setGroupSummary(true)
                        .build(),
                )
            }
        } catch (_: SecurityException) {
            // Notifications turned off meanwhile.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.new_episodes), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_new_episodes_description)
            },
        )
    }
}
