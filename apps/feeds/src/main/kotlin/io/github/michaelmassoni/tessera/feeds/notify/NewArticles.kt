package io.github.michaelmassoni.tessera.feeds.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.michaelmassoni.tessera.feeds.MainActivity
import io.github.michaelmassoni.tessera.feeds.R
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.FeedData

/** Notifications for new articles of the feeds the person asked to hear about. */
object NewArticles {
    private const val CHANNEL = "new_articles"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_new), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_new_description)
            },
        )
    }

    /** One notification per feed (with notify on) that has new unread [articles]. */
    fun post(
        context: Context,
        data: FeedData,
        articles: List<Article>,
    ) {
        if (articles.isEmpty()) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context)
        articles.groupBy { it.feedId }.forEach { (feedId, list) ->
            val feed = data.feed(feedId)?.takeIf { it.notify } ?: return@forEach
            val newest = list.sortedByDescending { it.date }
            val open =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (newest.size == 1) {
                        putExtra(MainActivity.EXTRA_ARTICLE, newest[0].id)
                    } else {
                        putExtra(MainActivity.EXTRA_FEED, feedId)
                    }
                }
            val builder =
                Notification
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_feed)
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            feedId.hashCode(),
                            open,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    ).setSubText(feed.title)
                    .setWhen(newest[0].date.takeIf { it > 0 } ?: System.currentTimeMillis())
                    .setShowWhen(true)
            if (newest.size == 1) {
                builder
                    .setContentTitle(newest[0].title)
                    .setContentText(newest[0].snippet)
                    .setStyle(Notification.BigTextStyle().bigText(newest[0].snippet))
            } else {
                val title = context.resources.getQuantityString(R.plurals.new_articles, newest.size, newest.size)
                val style = Notification.InboxStyle().setBigContentTitle(title)
                newest.take(MAX_LINES).forEach { style.addLine(it.title) }
                builder
                    .setContentTitle(title)
                    .setContentText(newest.joinToString(" · ") { it.title })
                    .setNumber(newest.size)
                    .setStyle(style)
            }
            manager.notify(feedId.hashCode(), builder.build())
        }
    }

    /** Clears a feed's notification once its articles are being read. */
    fun cancel(
        context: Context,
        feedId: String,
    ) = context.getSystemService(NotificationManager::class.java).cancel(feedId.hashCode())

    private const val MAX_LINES = 6
}
