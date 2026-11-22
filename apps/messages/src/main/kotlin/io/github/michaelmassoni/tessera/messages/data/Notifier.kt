package io.github.michaelmassoni.tessera.messages.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.michaelmassoni.tessera.messages.MainActivity
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Message notifications: one per conversation, listing its unread messages, with Reply and Mark
 * as read. Each conversation is also published as a shortcut, so Android shows it in the
 * notification shade's Conversations section. Calls block; run them off the main thread.
 */
class Notifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Shows (or updates, or clears) the notification for [thread] from its unread messages. After a
     * reply from the notification, [afterReply] shows the latest messages, including the reply,
     * silently.
     */
    fun notify(
        thread: Long,
        afterReply: Boolean = false,
    ) {
        if (Foreground.openThread == thread) {
            if (context.isDefaultSmsApp()) MessageStore(context).markRead(listOf(thread))
            return
        }
        val repository = MessagesRepository(context)
        val shown = if (afterReply) repository.messages(thread, repository.addresses(thread)) else repository.unreadIncoming(thread)
        if (shown.isEmpty()) return cancel(thread)
        if (!manager.areNotificationsEnabled()) return
        createChannel()

        val addresses = repository.addresses(thread)
        val group = addresses.size > 1
        val title = addresses.joinToString { repository.name(it) ?: it }.ifEmpty { context.getString(R.string.unknown) }
        val me = Person.Builder().setName(context.getString(R.string.you)).build()
        val people = mutableMapOf<String, Person>()

        fun person(address: String) =
            people.getOrPut(address) {
                Person
                    .Builder()
                    .setName(repository.name(address) ?: address)
                    .setKey(address)
                    // Lets Do Not Disturb match the sender against starred contacts.
                    .apply { if (address.any(Char::isDigit) && !address.contains('@')) setUri("tel:$address") }
                    .build()
            }
        val style =
            NotificationCompat.MessagingStyle(me).setGroupConversation(group).also { if (group) it.conversationTitle = title }
        shown.takeLast(MAX_LINES).forEach { m ->
            val text = m.body.ifEmpty { context.getString(R.string.picture_message) }
            // A null person is "me" in MessagingStyle.
            style.addMessage(text, m.date, if (m.outgoing) null else person(m.sender ?: addresses.firstOrNull().orEmpty()))
        }

        val shortcutId = "thread_$thread"
        val open =
            PendingIntent.getActivity(
                context,
                thread.toInt(),
                openIntent(thread),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                ShortcutInfoCompat
                    .Builder(context, shortcutId)
                    .setShortLabel(title)
                    .setLongLived(true)
                    .setLocusId(LocusIdCompat(shortcutId))
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(openIntent(thread).setAction(Intent.ACTION_VIEW))
                    .setPersons(addresses.map(::person).toTypedArray())
                    .build(),
            )
        }

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(DesignR.drawable.ic_chat)
                .setStyle(style)
                .setShortcutId(shortcutId)
                .setLocusId(LocusIdCompat(shortcutId))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                // MessagingStyle's senders aren't in the people list Do Not Disturb checks for starred contacts.
                .apply { addresses.forEach { addPerson(person(it)) } }
                .setContentIntent(open)
                .setAutoCancel(true)
                .setSilent(afterReply)
                .addAction(replyAction(thread))
                .addAction(
                    NotificationCompat.Action
                        .Builder(
                            // Icons, as Android Auto shows actions as buttons.
                            DesignR.drawable.ic_mark_chat_read,
                            context.getString(R.string.mark_read),
                            action(thread, NotificationActionReceiver.ACTION_MARK_READ, mutable = false),
                        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false)
                        .build(),
                ).build()
        try {
            manager.notify(thread.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications were turned off between the check and here.
        }
    }

    fun cancel(thread: Long) = manager.cancel(thread.toInt())

    private fun openIntent(thread: Long) =
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_THREAD, thread)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun action(
        thread: Long,
        name: String,
        mutable: Boolean,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (thread * 2 + if (mutable) 1 else 0).toInt(),
            Intent(
                context,
                NotificationActionReceiver::class.java,
            ).setAction(name).putExtra(NotificationActionReceiver.EXTRA_THREAD, thread),
            // The reply action must be mutable so Android can add the typed text.
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun replyAction(thread: Long) =
        NotificationCompat.Action
            .Builder(
                DesignR.drawable.ic_reply,
                context.getString(R.string.reply),
                action(thread, NotificationActionReceiver.ACTION_REPLY, mutable = true),
            ).addRemoteInput(RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel(context.getString(R.string.reply)).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()

    private fun createChannel() {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.deleteNotificationChannel(OLD_CHANNEL)
        // The default sound, and vibration (a channel doesn't vibrate unless asked to). The phone's
        // own settings still decide: silent mode, vibrate mode, Do Not Disturb.
        val channel =
            NotificationChannel(CHANNEL, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
        notifications.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "incoming_messages"

        /** Before 0.3.3: it didn't vibrate, and a channel's vibration can't be changed once made. */
        const val OLD_CHANNEL = "messages"
        const val MAX_LINES = 8
    }
}
