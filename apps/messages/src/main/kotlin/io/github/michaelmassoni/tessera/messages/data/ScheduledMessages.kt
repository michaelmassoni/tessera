package io.github.michaelmassoni.tessera.messages.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.messages.engine.ScheduledMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Messages scheduled to send later. Kept on this device in the app's own storage (Android's
 * message database has no scheduled state), and sent by an alarm. Pictures are copied in when
 * scheduled, because the photo picker's access doesn't outlive the app.
 */
class ScheduledMessages(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences("scheduled", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val dir get() = File(context.filesDir, "scheduled").apply { mkdirs() }

    fun all(): List<ScheduledMessage> = decode(prefs.getString(KEY, null)).sortedBy { it.at }

    /** Whether alarms fire on time; without the exact-alarm permission they may be minutes late. */
    fun exact(): Boolean = alarms.canScheduleExactAlarms()

    /** Stores the message, copying [images] into app storage, and sets its alarm. */
    fun schedule(
        addresses: List<String>,
        text: String,
        images: List<Uri>,
        at: Long,
    ): ScheduledMessage {
        val id = System.currentTimeMillis()
        val copies =
            images.mapIndexedNotNull { i, uri ->
                runCatching {
                    val file = File(dir, "${id}_$i")
                    context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    Uri.fromFile(file).toString()
                }.getOrNull()
            }
        val message = ScheduledMessage(id, addresses, text, at, copies)
        save(all() + message)
        arm(message)
        return message
    }

    fun cancel(id: Long) {
        val message = all().firstOrNull { it.id == id } ?: return
        alarms.cancel(intent(id))
        forget(message)
    }

    /** Sends [id] now (its alarm went off, or "Send now"), then forgets it. */
    fun send(id: Long) {
        // The alarm and a re-arm at app start can race: only one of them may take the message.
        val message =
            synchronized(LOCK) {
                all().firstOrNull { it.id == id }?.also {
                    alarms.cancel(intent(id))
                    // Forget first: if sending throws, it must not be retried in a loop on the next boot.
                    forget(it, deleteFiles = false)
                }
            } ?: return
        try {
            MessagesRepository(context).send(message.addresses, message.text, message.attachments.map(String::toUri))
        } finally {
            message.attachments.forEach { runCatching { File(it.toUri().path!!).delete() } }
        }
    }

    /** After a reboot or update, alarms are gone: set them again, and send anything already due. */
    fun rearm() {
        val now = System.currentTimeMillis()
        all().forEach { if (it.at <= now) send(it.id) else arm(it) }
    }

    private fun arm(message: ScheduledMessage) {
        val pending = intent(message.id)
        if (exact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.at, pending)
        }
    }

    private fun intent(id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.toInt(),
            // The data URI makes each message's alarm distinct, whatever the request code.
            Intent(context, ScheduledSendReceiver::class.java)
                .setAction(ACTION_SEND)
                .setData("tessera-scheduled://$id".toUri())
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun forget(
        message: ScheduledMessage,
        deleteFiles: Boolean = true,
    ) {
        save(all().filterNot { it.id == message.id })
        if (deleteFiles) message.attachments.forEach { runCatching { File(it.toUri().path!!).delete() } }
    }

    private fun save(list: List<ScheduledMessage>) = prefs.edit { putString(KEY, encode(list)) }

    private fun encode(list: List<ScheduledMessage>): String =
        JSONArray(
            list.map { m ->
                JSONObject()
                    .put("id", m.id)
                    .put("addresses", JSONArray(m.addresses))
                    .put("text", m.text)
                    .put("at", m.at)
                    .put("attachments", JSONArray(m.attachments))
            },
        ).toString()

    private fun decode(json: String?): List<ScheduledMessage> {
        if (json == null) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ScheduledMessage(
                    id = o.getLong("id"),
                    addresses = o.getJSONArray("addresses").strings(),
                    text = o.getString("text"),
                    at = o.getLong("at"),
                    attachments = o.optJSONArray("attachments")?.strings().orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONArray.strings() = (0 until length()).map(::getString)

    companion object {
        private const val KEY = "messages"
        const val ACTION_SEND = "io.github.michaelmassoni.tessera.messages.SEND_SCHEDULED"
        const val EXTRA_ID = "id"
        private val LOCK = Any()
    }
}

/** Sends a scheduled message when its alarm fires, and re-arms everything after a reboot or update. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduled = ScheduledMessages(context)
                when (intent.action) {
                    ScheduledMessages.ACTION_SEND -> scheduled.send(intent.getLongExtra(ScheduledMessages.EXTRA_ID, -1))

                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                    -> scheduled.rearm()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
