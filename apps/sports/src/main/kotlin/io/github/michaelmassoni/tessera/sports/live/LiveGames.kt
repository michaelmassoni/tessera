package io.github.michaelmassoni.tessera.sports.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.michaelmassoni.tessera.sports.MainActivity
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.data.EspnSource
import io.github.michaelmassoni.tessera.sports.data.ScoreSource
import io.github.michaelmassoni.tessera.sports.data.followStore
import io.github.michaelmassoni.tessera.sports.data.followedGames
import io.github.michaelmassoni.tessera.sports.data.sportsSettings
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.LiveScore
import io.github.michaelmassoni.tessera.sports.engine.Scores
import io.github.michaelmassoni.tessera.sports.engine.Team
import io.github.michaelmassoni.tessera.sports.ui.icon
import io.github.michaelmassoni.tessera.sports.widget.SportsWidget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Live scores: while a followed team plays, its score sits in an ongoing notification laid out
 * as Apple Sports does (not promoted to an Android Live Update: that needs a standard layout).
 * While a game is live the worker keeps running, checking
 * ESPN's current scoreboard every [LIVE_INTERVAL_MS] and as soon as the screen comes on; between
 * games it sleeps until a minute before the next one. It's not a foreground service (Android won't
 * start one from the background for this), so with the screen off and the phone idle, Android may
 * hold the checks back; turning the screen on brings them back within a few seconds.
 */
object LiveGames {
    private const val WORK = "live-games"
    const val CHANNEL = "live_games"
    private const val PREFS = "live"
    private const val KEY_SHOWN = "shown"

    /**
     * How often a live game is checked. ESPN's scoreboards are cached for up to about 10 s, so
     * checking more often wouldn't bring news sooner.
     */
    const val LIVE_INTERVAL_MS = 15_000L

    /** How long one worker run keeps watching (WorkManager stops a run after 10 minutes). */
    const val RUN_MS = 9 * 60_000L

    /** How faded a losing score is after full time. */
    private const val LOSER_ALPHA = 0.55f

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

    /**
     * A score layout ([layout]: collapsed or expanded) filled in. Spoiler-free ([hide]) leaves out the
     * scores and the last play. After full time the losing score is dimmed, as Apple Sports does.
     */
    private fun scoreViews(
        context: Context,
        layout: Int,
        game: Game,
        status: String,
        awayLogo: Bitmap?,
        homeLogo: Bitmap?,
        hide: Boolean,
    ): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            awayLogo?.let { setImageViewBitmap(R.id.away_logo, it) } ?: setViewVisibility(R.id.away_logo, View.GONE)
            homeLogo?.let { setImageViewBitmap(R.id.home_logo, it) } ?: setViewVisibility(R.id.home_logo, View.GONE)
            setTextViewText(R.id.status, status)
            if (hide) {
                setViewVisibility(R.id.away_score, View.GONE)
                setViewVisibility(R.id.home_score, View.GONE)
            } else {
                setTextViewText(R.id.away_score, game.away.score.orEmpty())
                setTextViewText(R.id.home_score, game.home.score.orEmpty())
                if (game.state == GameState.Final) {
                    if (game.home.winner) setFloat(R.id.away_score, "setAlpha", LOSER_ALPHA)
                    if (game.away.winner) setFloat(R.id.home_score, "setAlpha", LOSER_ALPHA)
                }
            }
            if (layout == R.layout.live_expanded) {
                setTextViewText(R.id.league, game.league.shortName)
                setTextViewText(R.id.away_name, game.away.team.shortName)
                setTextViewText(R.id.home_name, game.home.team.shortName)
                game.situation?.let { setTextViewText(R.id.situation, it) } ?: setViewVisibility(R.id.situation, View.GONE)
                game.lastPlay?.takeUnless { hide || game.state != GameState.Live }?.let { setTextViewText(R.id.last_play, it) }
                    ?: setViewVisibility(R.id.last_play, View.GONE)
            }
        }

    /**
     * The notification for [game]: ongoing while live, a dismissible final score after.
     *
     * The title and text read "Rockets 115 · Nuggets 117" and "3:20 - 4th · Nuggets by 2" (also what
     * screen readers and watches get); the layouts show the score as Apple Sports does. The time is
     * when the score was fetched, so an old score never looks current.
     */
    internal fun notification(
        context: Context,
        game: Game,
        followed: Set<String>,
        updatedAt: Long,
        awayLogo: Bitmap? = null,
        homeLogo: Bitmap? = null,
        /** Spoiler-free: the teams and the clock, no score or margin. */
        hide: Boolean = false,
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
            if (hide) {
                context.getString(R.string.live_title_hidden, game.away.team.shortName, game.home.team.shortName)
            } else {
                context.getString(
                    R.string.live_title,
                    game.away.team.shortName,
                    game.away.score.orEmpty(),
                    game.home.team.shortName,
                    game.home.score.orEmpty(),
                )
            }
        val leader = LiveScore.leader(game)
        val gap = abs((game.home.score?.toIntOrNull() ?: 0) - (game.away.score?.toIntOrNull() ?: 0))
        val text =
            if (hide) {
                if (live) game.status else context.getString(R.string.final_score)
            } else if (live) {
                context.getString(
                    R.string.live_text,
                    game.status,
                    leader?.let { context.getString(R.string.live_leads, it.team.shortName, gap) } ?: context.getString(R.string.live_tied),
                )
            } else {
                context.getString(
                    R.string.live_text,
                    context.getString(R.string.final_score),
                    leader?.let { context.getString(R.string.live_won, it.team.shortName) } ?: context.getString(R.string.live_drawn),
                )
            }
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(game.league.sport.icon())
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(updatedAt)
                .setShowWhen(true)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(live)
                .setAutoCancel(!live)
        // In the spirit of Apple Sports: logos and big scores at the sides, the clock between, names
        // under the logos, then the situation and the last play (expanded). A custom layout can't be
        // promoted to an Android Live Update; docs/sports.md says why it's worth it.
        val status = if (live) game.status else context.getString(R.string.final_score)
        builder
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(scoreViews(context, R.layout.live_collapsed, game, status, awayLogo, homeLogo, hide))
            .setCustomBigContentView(scoreViews(context, R.layout.live_expanded, game, status, awayLogo, homeLogo, hide))
        if (!live) builder.setTimeoutAfter(3 * 60 * 60 * 1000L)
        return builder.build()
    }
}

class LiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val source: ScoreSource = EspnSource()

    /** Team logos for this run, light or dark to suit the notification shade. */
    private val logos = mutableMapOf<String, Bitmap?>()

    private suspend fun logo(team: Team): Bitmap? {
        val context = applicationContext
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val url = (if (dark) team.logoOnDark() else team.logo) ?: return null
        return logos.getOrPut(url) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(128)
                    .allowHardware(false)
                    .build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }
    }

    private suspend fun post(
        manager: NotificationManager,
        game: Game,
        followed: Set<String>,
        at: Long,
    ) {
        val n =
            LiveGames.notification(
                applicationContext,
                game,
                followed,
                at,
                logo(game.away.team),
                logo(game.home.team),
                hide = applicationContext.sportsSettings().hideScores,
            )
        manager.notify(game.id.hashCode(), n)
        // The widget shows the same game.
        SportsWidget.refresh(applicationContext)
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val follows = context.followStore().load()
        if (!context.sportsSettings().liveUpdates || follows.teams.isEmpty()) {
            LiveGames.stop(context)
            return Result.success()
        }
        val started = System.currentTimeMillis()
        val games =
            runCatching { source.followedGames(follows, ZoneId.systemDefault()) }.getOrElse {
                // Offline: try again in a while.
                LiveGames.next(context, if (LiveGames.shown(context).isNotEmpty()) LiveGames.LIVE_INTERVAL_MS else 15 * 60_000L)
                return Result.success()
            }
        val followed = follows.teamKeys
        val mine = games.filter { it.involves(followed) }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Result.success()
        LiveGames.ensureChannel(context)
        val canPost = manager.areNotificationsEnabled()
        val shown = LiveGames.shown(context)
        var live = mine.filter { it.state == GameState.Live }
        if (canPost) {
            live.forEach { post(manager, it, followed, started) }
            // A game that was showing and is over: leave its final score (dismissible).
            mine.filter { it.id in shown && it.state == GameState.Final }.forEach { post(manager, it, followed, started) }
        }
        LiveGames.setShown(context, live.map { it.id }.toSet())
        if (live.isEmpty()) {
            // Wake a minute before the next followed game, or check back in a few hours.
            val delay = Scores.nextStart(mine, followed, started)?.let { it - started - 60_000L } ?: (6 * 60 * 60_000L)
            LiveGames.next(context, delay)
            return Result.success()
        }
        live = watch(manager, live, followed, started + LiveGames.RUN_MS, canPost)
        // Still live: the next run takes over straight away; otherwise sleep until the next game.
        LiveGames.next(context, if (live.isNotEmpty()) 0 else 60_000L)
        return Result.success()
    }

    /**
     * Refreshes [live] games every [LiveGames.LIVE_INTERVAL_MS], and right away when the screen
     * comes on (when someone looks), until they've all ended or [until]. One scoreboard request
     * per league; a game the scoreboard no longer lists is asked for by itself.
     */
    private suspend fun watch(
        manager: NotificationManager,
        start: List<Game>,
        followed: Set<String>,
        until: Long,
        canPost: Boolean,
    ): List<Game> {
        val context = applicationContext
        val wake = Channel<Unit>(Channel.CONFLATED)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    wake.trySend(Unit)
                }
            }
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        var live = start
        try {
            while (live.isNotEmpty() && System.currentTimeMillis() < until) {
                withTimeoutOrNull(LiveGames.LIVE_INTERVAL_MS) { wake.receive() }
                val fresh = live.map { it.league }.distinct().flatMap { runCatching { source.current(it) }.getOrDefault(emptyList()) }
                val missing = live.filter { g -> fresh.none { it.league == g.league && it.id == g.id } }
                val asked = missing.mapNotNull { runCatching { source.detail(it)?.game }.getOrNull() }
                val now = System.currentTimeMillis()
                val updated = LiveScore.update(live, fresh + asked)
                if (canPost) updated.forEach { post(manager, it, followed, now) }
                live = updated.filter { it.state == GameState.Live }
                LiveGames.setShown(context, live.map { it.id }.toSet())
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
        return live
    }
}
