package io.github.michaelmassoni.tessera.podcasts.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.podcasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloaded episodes: in the app's own external folder, one file per episode. */
object Downloads {
    const val TAG = "download"
    const val KEY_EPISODE = "episode"
    const val KEY_PROGRESS = "progress"
    const val CHANNEL = "downloads"

    fun dir(context: Context): File =
        (context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "downloads")).also { it.mkdirs() }

    fun file(
        context: Context,
        name: String,
    ) = File(dir(context), name)

    private fun workName(episodeId: String) = "download-$episodeId"

    /** Downloads [episodeId]: a tap ([manual]) may use mobile data if Settings allow it; automatic ones wait for Wi-Fi. */
    fun enqueue(
        context: Context,
        episodeId: String,
        manual: Boolean,
    ) {
        val network = if (manual && Prefs.mobileDownloads(context)) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY_EPISODE to episodeId))
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(network)
                        .setRequiresStorageNotLow(true)
                        .build(),
                ).addTag(TAG)
                .addTag("episode:$episodeId")
                .apply { if (manual) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(
        context: Context,
        episodeId: String,
    ) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(episodeId))
    }

    /** Deletes the episode's download, if any. */
    fun delete(
        context: Context,
        store: PodcastStore,
        episodeId: String,
    ) {
        val name =
            store.library.value
                .state(episodeId)
                .download ?: return
        file(context, name).delete()
        store.update { lib -> lib.withState(episodeId) { it.copy(download = null) } }
    }

    /** The playable file for [episodeId], or null (not downloaded, or the file has gone). */
    fun playable(
        context: Context,
        store: PodcastStore,
        episodeId: String,
    ): File? {
        val name =
            store.library.value
                .state(episodeId)
                .download ?: return null
        val file = file(context, name)
        if (file.exists()) return file
        store.update { lib -> lib.withState(episodeId) { it.copy(download = null) } }
        return null
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** A file name for the download: the episode id (safe for files) and the audio's extension. */
    fun fileName(
        episodeId: String,
        audioUrl: String,
        type: String?,
    ): String {
        val fromUrl =
            audioUrl
                .substringBefore('?')
                .substringAfterLast('/')
                .substringAfterLast('.', "")
                .lowercase()
        val ext =
            fromUrl.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
                ?: when (type) {
                    "audio/mp4", "audio/x-m4a", "audio/aac" -> "m4a"
                    "audio/ogg" -> "ogg"
                    "audio/opus" -> "opus"
                    "video/mp4" -> "mp4"
                    else -> "mp3"
                }
        return episodeId.replace(':', '-') + "." + ext
    }
}

/** Downloads one episode, showing its progress in a notification. Retries when the connection drops. */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val episodeId = inputData.getString(Downloads.KEY_EPISODE).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0)

    private fun foreground(percent: Int): ForegroundInfo {
        val store = applicationContext.podcasts.store
        val title = store.episode(episodeId)?.title ?: applicationContext.getString(R.string.downloading)
        val notification =
            NotificationCompat
                .Builder(applicationContext, Downloads.CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(applicationContext.getString(R.string.downloading))
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setSilent(true)
                .addAction(
                    0,
                    applicationContext.getString(R.string.cancel),
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                ).build()
        return ForegroundInfo(episodeId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        val podcasts = applicationContext.podcasts
        val store = podcasts.store
        val episode = store.episode(episodeId) ?: return Result.failure()
        if (store.library.value
                .state(episodeId)
                .download != null
        ) {
            return Result.success()
        }
        Downloads.ensureChannel(applicationContext)
        runCatching { setForeground(foreground(0)) }
        val name = Downloads.fileName(episodeId, episode.audioUrl, episode.audioType)
        val target = Downloads.file(applicationContext, name)
        val part = File(target.path + ".part")
        return withContext(Dispatchers.IO) {
            try {
                download(episode.audioUrl, part)
                if (!part.renameTo(target)) throw IOException("rename failed")
                store.update { lib -> lib.withState(episodeId) { it.copy(download = name) } }
                Result.success()
            } catch (e: IOException) {
                part.delete()
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }
    }

    private suspend fun download(
        url: String,
        part: File,
    ) {
        Net.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val total = response.body.contentLength()
            var done = 0L
            var shown = -1
            response.body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != shown) {
                                shown = percent
                                setProgress(workDataOf(Downloads.KEY_EPISODE to episodeId, Downloads.KEY_PROGRESS to percent))
                                runCatching { setForeground(foreground(percent)) }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
