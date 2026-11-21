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
import android.graphics.drawable.Icon
import android.os.Build
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live Updates: while a followed team plays, its score sits in an ongoing notification (promoted
 * to a Live Update on Android 16 QPR1+). While a game is live the worker keeps running, checking
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

    /** Progress units per period in the Live Update's bar. */
    private const val UNITS = 100

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
     * The notification for [game]: ongoing while live, a dismissible final score after.
     *
     * Each team sits next to its own score ("Rockets 115 · Nuggets 117", away first as in the
     * app), the text says who leads ("4th 3:20 · Nuggets by 2"), and the status bar chip shows
     * your team's margin ("DEN +2"). On Android 16 the bar shows how far through the game it is,
     * a segment per period, between the away and home logos. The time is when the score was
     * fetched, so an old score never looks current.
     */
    internal fun notification(
        context: Context,
        game: Game,
        followed: Set<String>,
        updatedAt: Long,
        awayLogo: Bitmap? = null,
        homeLogo: Bitmap? = null,
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
                game.away.team.shortName,
                game.away.score.orEmpty(),
                game.home.team.shortName,
                game.home.score.orEmpty(),
            )
        val leader = LiveScore.leader(game)
        val gap = abs((game.home.score?.toIntOrNull() ?: 0) - (game.away.score?.toIntOrNull() ?: 0))
        val text =
            if (live) {
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
        val yours = LiveScore.yours(game, followed)
        val builder =
            Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(game.league.sport.icon())
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(game.league.shortName)
                .setWhen(updatedAt)
                .setShowWhen(true)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(live)
                .setAutoCancel(!live)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val periods = LiveScore.periods(game.league)
            val style =
                Notification
                    .ProgressStyle()
                    .setProgressSegments(List(periods) { Notification.ProgressStyle.Segment(UNITS) })
                    .setProgress((LiveScore.elapsed(game) * UNITS).roundToInt())
                    .setStyledByProgress(true)
            awayLogo?.let { style.setProgressStartIcon(Icon.createWithBitmap(it)) }
            homeLogo?.let { style.setProgressEndIcon(Icon.createWithBitmap(it)) }
            builder.setStyle(style)
            LiveScore.margin(game, yours.team.key)?.let { margin ->
                builder.setShortCriticalText(
                    when {
                        margin > 0 -> context.getString(R.string.live_chip_ahead, yours.team.abbreviation, margin)
                        margin < 0 -> context.getString(R.string.live_chip_behind, yours.team.abbreviation, -margin)
                        else -> context.getString(R.string.live_chip_level, yours.team.abbreviation)
                    },
                )
            }
        } else {
            (if (yours === game.away) awayLogo else homeLogo)?.let { builder.setLargeIcon(it) }
        }
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
        val n = LiveGames.notification(applicationContext, game, followed, at, logo(game.away.team), logo(game.home.team))
        manager.notify(game.id.hashCode(), n)
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
