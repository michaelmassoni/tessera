package io.github.michaelmassoni.tessera.music.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.Offline
import io.github.michaelmassoni.tessera.music.engine.OfflineKind
import io.github.michaelmassoni.tessera.music.engine.OfflinePins
import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.server.ServerLibrary
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.StreamQuality
import io.github.michaelmassoni.tessera.music.server.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A download pass in progress: [done] of [total] songs fetched. */
data class DownloadProgress(
    val done: Int,
    val total: Int,
)

/** What the library shows about downloads. */
data class DownloadsState(
    val pins: OfflinePins = OfflinePins(),
    /** Server song ids on the phone. */
    val onPhone: Set<String> = emptySet(),
    val progress: DownloadProgress? = null,
    /** Downloads wait for Wi-Fi (Settings > Downloads). */
    val wifiOnly: Boolean = true,
)

/** The pins and Downloads settings, in `music_downloads.xml`. */
class DownloadStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var pins: OfflinePins
        get() =
            prefs.getString(KEY_PINS, null)?.let { runCatching { json.decodeFromString(OfflinePins.serializer(), it) }.getOrNull() }
                ?: OfflinePins()
        set(value) = prefs.edit { putString(KEY_PINS, json.encodeToString(OfflinePins.serializer(), value)) }

    /** Download only on Wi-Fi (or another unmetered network). */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI, true)
        set(value) = prefs.edit { putBoolean(KEY_WIFI, value) }

    /** The quality songs are downloaded at; changing it applies to songs downloaded from then on. */
    var quality: StreamQuality
        get() = StreamQuality.entries.firstOrNull { it.name == prefs.getString(KEY_QUALITY, null) } ?: StreamQuality.High
        set(value) = prefs.edit { putString(KEY_QUALITY, value.name) }

    private companion object {
        const val FILE = "music_downloads"
        const val KEY_PINS = "pins"
        const val KEY_WIFI = "wifi_only"
        const val KEY_QUALITY = "quality"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Albums, playlists and Liked songs downloaded for offline listening (docs/music.md, Downloads).
 * Files live in `files/downloads/<account>/`, one per song, named by its server id; playback
 * uses them before the network ([file]). [sync] runs [DownloadWorker] to match the files to the
 * pins.
 */
object Downloads {
    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    const val CHANNEL = "downloads"
    private const val WORK = "music-downloads"
    internal const val NOTIFICATION_ID = 7301

    private fun dir(
        context: Context,
        account: ServerAccount,
    ): File {
        val key =
            MessageDigest
                .getInstance("SHA-256")
                .digest("${account.address}|${account.username}".toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "downloads/$key")
    }

    private fun files(context: Context): List<File> {
        val account = ServerStore(context).account ?: return emptyList()
        return dir(context, account).listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
    }

    /** The downloaded file for a server song, or null. */
    fun file(
        context: Context,
        serverId: String,
    ): File? = files(context).firstOrNull { it.nameWithoutExtension == safe(serverId) }

    /** Re-reads the pins and what's on the phone. */
    fun refresh(context: Context) {
        val onPhone = files(context).map { it.nameWithoutExtension }.toSet()
        val store = DownloadStore(context)
        _state.update { it.copy(pins = store.pins, onPhone = onPhone, wifiOnly = store.wifiOnly) }
    }

    /** Turns downloading of an album or playlist ([Offline.LIKED] for Liked songs) on or off. */
    fun pin(
        context: Context,
        kind: OfflineKind,
        id: Long,
        on: Boolean,
    ) {
        val store = DownloadStore(context)
        store.pins = store.pins.with(kind, id, on)
        refresh(context)
        sync(context)
    }

    /** Every server song in [songs] is on the phone (and there is at least one). */
    fun complete(
        songs: List<Song>,
        state: DownloadsState,
    ): Boolean {
        val server = songs.filter { Offline.serverId(it) != null }
        return server.isNotEmpty() && server.all { Offline.isDownloaded(it, state.onPhone) }
    }

    /** Bytes the downloads take. */
    fun bytes(context: Context): Long = files(context).sumOf { it.length() }

    /** Removes every download and every pin (Settings > Remove all downloads). */
    fun removeAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        DownloadStore(context).pins = OfflinePins()
        File(context.filesDir, "downloads").deleteRecursively()
        _state.value = DownloadsState()
    }

    /** Fetches and deletes files to match the pins, in the background (Wi-Fi only if set). */
    fun sync(context: Context) {
        val store = DownloadStore(context)
        if (store.pins.isEmpty && files(context).isEmpty()) return
        val network = if (store.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .build()
        // A newer pass replaces a running one; files already fetched are kept.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW),
        )
    }

    internal fun setProgress(progress: DownloadProgress?) = _state.update { it.copy(progress = progress) }

    internal fun target(
        context: Context,
        account: ServerAccount,
        serverId: String,
        extension: String,
    ): File = File(dir(context, account), "${safe(serverId)}.$extension")

    internal fun delete(
        context: Context,
        serverIds: Set<String>,
    ) = files(context).filter { it.nameWithoutExtension in serverIds.map(::safe) }.forEach { it.delete() }

    private fun safe(serverId: String) = serverId.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** The file extension for an audio content type. */
    internal fun extension(contentType: String?): String =
        when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/mp4", "audio/x-m4a", "audio/aac", "audio/m4a" -> "m4a"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "mp3"
        }
}

/** One download pass: delete what no pin needs, then fetch what's missing, one song at a time. */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(DownloadProgress(0, 0))

    private fun foreground(progress: DownloadProgress): ForegroundInfo {
        val context = applicationContext
        val text =
            if (progress.total > 0) {
                context.resources.getQuantityString(R.plurals.downloading_count, progress.total, progress.done, progress.total)
            } else {
                context.getString(R.string.downloading)
            }
        val notification =
            NotificationCompat
                .Builder(context, Downloads.CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.downloading_title))
                .setContentText(text)
                .setProgress(progress.total, progress.done, progress.total == 0)
                .setOngoing(true)
                .setSilent(true)
                .addAction(0, context.getString(R.string.cancel), WorkManager.getInstance(context).createCancelPendingIntent(id))
                .build()
        return ForegroundInfo(Downloads.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val server = ServerStore(context)
        val account = server.account ?: return Result.success()
        val password = server.password() ?: return Result.success()
        val contents = withContext(Dispatchers.IO) { ServerLibrary.load(context, account) } ?: return Result.success()
        val store = DownloadStore(context)
        val wanted = Offline.wanted(store.pins, contents.songs, contents.playlists, LikeStore.read(context))
        Downloads.refresh(context)
        val plan = Offline.plan(wanted, Downloads.state.value.onPhone)
        withContext(Dispatchers.IO) { Downloads.delete(context, plan.delete) }
        Downloads.refresh(context)
        if (plan.download.isEmpty()) return Result.success()
        Downloads.ensureChannel(context)
        val client = SubsonicClient(account, password)
        val kbps = store.quality.kbps
        var failed = false
        try {
            plan.download.forEachIndexed { i, song ->
                val progress = DownloadProgress(i, plan.download.size)
                Downloads.setProgress(progress)
                runCatching { setForeground(foreground(progress)) }
                val id = Offline.serverId(song) ?: return@forEachIndexed
                try {
                    withContext(Dispatchers.IO) { fetch(client.streamUrl(id, kbps), account, id) }
                    Downloads.refresh(context)
                } catch (_: IOException) {
                    // This song next time; the rest carry on.
                    failed = true
                }
            }
        } finally {
            Downloads.setProgress(null)
        }
        return if (failed && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    private suspend fun fetch(
        url: String,
        account: ServerAccount,
        id: String,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val type = connection.contentType
            // The server answers errors as XML or JSON with a 200: that's not a song.
            if (type != null && (type.contains("xml") || type.contains("json"))) throw IOException("Not audio: $type")
            val target = Downloads.target(applicationContext, account, id, Downloads.extension(type))
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            try {
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                    }
                }
                if (!part.renameTo(target)) throw IOException("Can't save ${target.name}")
            } finally {
                part.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val TIMEOUT_MS = 30_000
        const val BUFFER = 64 * 1024
    }
}
