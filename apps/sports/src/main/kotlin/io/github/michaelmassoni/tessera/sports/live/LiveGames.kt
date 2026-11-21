package io.github.michaelmassoni.tessera.sports.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.sports.MainActivity
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.data.EspnSource
import io.github.michaelmassoni.tessera.sports.data.followStore
import io.github.michaelmassoni.tessera.sports.data.followedGames
import io.github.michaelmassoni.tessera.sports.data.sportsSettings
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.Scores
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Live Updates: while a followed team plays, its score sits in an ongoing notification (promoted
 * to a Live Update on Android 16 QPR1+). A WorkManager job checks about every minute while a
 * game is live, and otherwise waits until the next followed game starts. It's not a foreground
 * service (Android won't start one from the background for this), so in deep sleep the updates
 * can come less often.
 */
object LiveGames {
    private const val WORK = "live-games"
    const val CHANNEL = "live_games"
    private const val PREFS = "live"
    private const val KEY_SHOWN = "shown"

    /** How often a live game is checked. */
    const val LIVE_INTERVAL_MS = 60_000L

    /** Starts (or restarts) the watch: right away, then as the games need. */
    fun kick(context: Context) = enqueue(context, delayMs = 0, ExistingWorkPolicy.REPLACE)

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        val manager = context.getSystemService(NotificationManager::class.java)
        shown(context).forEach { manager?.cancel(it.hashCode()) }
        setShown(context, emptySet())
    }

    private fun enqueue(
        context: Context,
        delayMs: Long,
        policy: ExistingWorkPolicy,
    ) {
        val request =
            OneTimeWorkRequestBuilder<LiveWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
    }

    /** From inside the worker: queue the next run behind this one. */
    internal fun next(
        context: Context,
        delayMs: Long,
    ) = enqueue(context, delayMs, ExistingWorkPolicy.APPEND_OR_REPLACE)

    internal fun shown(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SHOWN, emptySet()).orEmpty()

    internal fun setShown(
        context: Context,
        ids: Set<String>,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(KEY_SHOWN, ids) }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** The notification for [game]: ongoing while live, a dismissible final score after. */
    internal fun notification(
        context: Context,
        game: Game,
    ): Notification {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val live = game.state == GameState.Live
        val title =
            context.getString(
                R.string.live_title,
                game.away.team.abbreviation,
                game.away.score.orEmpty(),
                game.home.score.orEmpty(),
                game.home.team.abbreviation,
            )
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_scoreboard)
                .setContentTitle(title)
                .setContentText(if (live) game.status else context.getString(R.string.final_score))
                .setSubText(game.league.shortName)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(live)
                .setAutoCancel(!live)
        if (live && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            // Android 16 QPR1+: ask for a Live Update (a status bar chip, near the top of the lock screen).
            builder.setRequestPromotedOngoing(true)
        }
        if (!live) builder.setTimeoutAfter(3 * 60 * 60 * 1000L)
        return builder.build()
    }
}

class LiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val follows = context.followStore().load()
        if (!context.sportsSettings().liveUpdates || follows.teams.isEmpty()) {
            LiveGames.stop(context)
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val games =
            runCatching { EspnSource().followedGames(follows, ZoneId.systemDefault()) }.getOrElse {
                // Offline: try again in a while.
                LiveGames.next(context, if (LiveGames.shown(context).isNotEmpty()) LiveGames.LIVE_INTERVAL_MS else 15 * 60_000L)
                return Result.success()
            }
        val mine = games.filter { it.involves(follows.teamKeys) }
        val manager = context.getSystemService(NotificationManager::class.java)
        LiveGames.ensureChannel(context)
        val canPost = manager?.areNotificationsEnabled() == true
        val shown = LiveGames.shown(context)
        val live = mine.filter { it.state == GameState.Live }
        if (canPost) {
            live.forEach { manager.notify(it.id.hashCode(), LiveGames.notification(context, it)) }
            // A game that was showing and is over: leave its final score (dismissible).
            mine.filter { it.id in shown && it.state == GameState.Final }.forEach {
                manager.notify(it.id.hashCode(), LiveGames.notification(context, it))
            }
        }
        LiveGames.setShown(context, live.map { it.id }.toSet())
        val delay =
            if (live.isNotEmpty()) {
                LiveGames.LIVE_INTERVAL_MS
            } else {
                // Wake a minute before the next followed game, or check back in a few hours.
                Scores.nextStart(mine, follows.teamKeys, now)?.let { it - now - 60_000L } ?: (6 * 60 * 60_000L)
            }
        LiveGames.next(context, delay)
        return Result.success()
    }
}
