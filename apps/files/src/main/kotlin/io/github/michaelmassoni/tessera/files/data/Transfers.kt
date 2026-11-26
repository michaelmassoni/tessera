package io.github.michaelmassoni.tessera.files.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.michaelmassoni.tessera.files.MainActivity
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.engine.Archives
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.OperationException
import io.github.michaelmassoni.tessera.files.engine.Progress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What a transfer does. */
enum class OperationKind { Copy, Move, Compress, Extract }

/** A transfer in progress ([kind] null: moving to the trash, which the browser runs itself). */
data class RunningOperation(
    val kind: OperationKind?,
    val progress: Progress?,
)

/** How a transfer ended: [count] items done, or why it failed. */
data class TransferOutcome(
    val kind: OperationKind,
    val count: Int,
    val failure: Failure? = null,
) {
    enum class Failure { IntoItself, NoSpace, Io, Cancelled, BadArchive }
}

/**
 * Copies, moves and zips run here (docs/files.md, Transfers): in [TransferService], a foreground
 * service, so they keep going after the app is left. One at a time; the browser shows [running]
 * and reports [outcomes].
 */
object Transfers {
    private class Request(
        val kind: OperationKind,
        val sources: List<File>,
        val target: File?,
    )

    private val _running = MutableStateFlow<RunningOperation?>(null)
    val running: StateFlow<RunningOperation?> = _running.asStateFlow()

    private val _outcomes = MutableSharedFlow<TransferOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<TransferOutcome> = _outcomes.asSharedFlow()

    @Volatile private var pending: Request? = null
    private val cancel = AtomicBoolean(false)

    /** Starts a transfer, unless one is running. [target] is the folder (null for Extract: beside the zip). */
    fun start(
        context: Context,
        kind: OperationKind,
        sources: List<File>,
        target: File?,
    ): Boolean {
        if (_running.value != null || sources.isEmpty()) return false
        pending = Request(kind, sources, target)
        cancel.set(false)
        _running.value = RunningOperation(kind, null)
        context.startForegroundService(Intent(context, TransferService::class.java))
        return true
    }

    fun cancel() = cancel.set(true)

    internal fun run(
        context: Context,
        onProgress: (OperationKind, Progress) -> Unit,
    ): TransferOutcome? {
        val request = pending ?: return null
        pending = null
        val ops = FileOperations(freeSpace = AndroidFreeSpace(context))
        val report: (Progress) -> Unit = { p ->
            _running.value = RunningOperation(request.kind, p)
            onProgress(request.kind, p)
        }
        val outcome =
            try {
                when (request.kind) {
                    OperationKind.Copy -> ops.copy(request.sources, request.target!!, cancel, report)
                    OperationKind.Move -> ops.move(request.sources, request.target!!, cancel, report)
                    OperationKind.Compress -> Archives.compress(request.sources, request.target!!, cancel, report)
                    OperationKind.Extract -> request.sources.forEach { Archives.extract(it, cancel, report) }
                }
                TransferOutcome(request.kind, request.sources.size)
            } catch (e: IOException) {
                TransferOutcome(request.kind, request.sources.size, failureOf(e))
            }
        _running.value = null
        _outcomes.tryEmit(outcome)
        return outcome
    }

    fun failureOf(e: IOException): TransferOutcome.Failure =
        when (e) {
            is OperationException.IntoItself -> TransferOutcome.Failure.IntoItself
            is OperationException.NotEnoughSpace -> TransferOutcome.Failure.NoSpace
            is OperationException.Cancelled -> TransferOutcome.Failure.Cancelled
            is Archives.Unsafe -> TransferOutcome.Failure.BadArchive
            else -> if (e.message?.startsWith("Not a readable zip") == true) TransferOutcome.Failure.BadArchive else TransferOutcome.Failure.Io
        }

    /** Whether the browser is watching (so it reports the outcome itself, not a notification). */
    internal val watched: Boolean get() = _outcomes.subscriptionCount.value > 0
}

/** Runs [Transfers] in the foreground, with a progress notification and Cancel. */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_CANCEL) {
            Transfers.cancel()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(ID, progressNotification(Transfers.running.value?.kind ?: OperationKind.Copy, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            var lastUpdate = 0L
            val outcome =
                Transfers.run(this@TransferService) { kind, p ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= UPDATE_MS) {
                        lastUpdate = now
                        notify(progressNotification(kind, p))
                    }
                }
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Nobody's looking at the app: say how it went in a notification instead.
            if (outcome != null && !Transfers.watched) notifyDone(outcome)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_transfers), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_transfers_description)
            },
        )
    }

    private fun title(kind: OperationKind) =
        getString(
            when (kind) {
                OperationKind.Copy -> R.string.progress_copying
                OperationKind.Move -> R.string.progress_moving
                OperationKind.Compress -> R.string.progress_compressing
                OperationKind.Extract -> R.string.progress_extracting
            },
        )

    private fun openApp() =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun progressNotification(
        kind: OperationKind,
        p: Progress?,
    ): Notification {
        val cancel =
            PendingIntent.getService(
                this,
                1,
                Intent(this, TransferService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val percent = if (p != null && p.bytesTotal > 0) (p.bytesDone * 100 / p.bytesTotal).toInt() else 0
        return NotificationCompat
            .Builder(this, CHANNEL)
            .setSmallIcon(DesignR.drawable.ic_folder)
            .setContentTitle(title(kind))
            .setContentText(
                p?.let {
                    "${it.current} · ${Formatter.formatShortFileSize(this, it.bytesDone)} / ${Formatter.formatShortFileSize(this, it.bytesTotal)}"
                },
            ).setProgress(100, percent, p == null || p.bytesTotal == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(DesignR.drawable.ic_close, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun notify(notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(ID, notification)
        } catch (_: SecurityException) {
            // Notifications are off: the transfer still runs.
        }
    }

    private fun notifyDone(outcome: TransferOutcome) {
        val text =
            if (outcome.failure == null) {
                getString(R.string.transfer_done, title(outcome.kind))
            } else {
                getString(R.string.transfer_failed, title(outcome.kind))
            }
        try {
            NotificationManagerCompat.from(this).notify(
                DONE_ID,
                NotificationCompat
                    .Builder(this, CHANNEL)
                    .setSmallIcon(DesignR.drawable.ic_folder)
                    .setContentTitle(text)
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build(),
            )
        } catch (_: SecurityException) {
            // Notifications are off.
        }
    }

    private companion object {
        const val CHANNEL = "transfers"
        const val ID = 1
        const val DONE_ID = 2
        const val UPDATE_MS = 500L
        const val ACTION_CANCEL = "io.github.michaelmassoni.tessera.files.CANCEL_TRANSFER"
    }
}
