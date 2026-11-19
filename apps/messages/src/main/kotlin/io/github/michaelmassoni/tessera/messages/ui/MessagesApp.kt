package io.github.michaelmassoni.tessera.messages.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.ScheduledMessage

data class MessagesState(
    val loaded: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val hasPermissions: Boolean = true,
    /** Tessera Messages is the default SMS app: it receives, and may delete and mark read. */
    val isDefault: Boolean = false,
    val pinned: Set<Long> = emptySet(),
    val archived: Set<Long> = emptySet(),
    /** The new-message screen is showing (from the button, Forward, or a share from another app). */
    val composing: Boolean = false,
)

/** The open conversation; [threadId] is null for a brand-new one. */
data class OpenThread(
    val threadId: Long?,
    val addresses: List<String>,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    /** False until the first messages have been read, so the screen can show it is loading. */
    val loaded: Boolean = false,
    /** Messages scheduled to send later in this conversation, soonest first. */
    val scheduled: List<ScheduledMessage> = emptyList(),
    /** Pictures waiting to be sent with the draft (content URIs). */
    val attachments: List<String> = emptyList(),
)

interface MessagesActions {
    fun open(conversation: Conversation)

    /** Starts (or opens) the conversation with [addresses], with any pending draft typed in. */
    fun startNew(addresses: List<String>)

    fun close()

    /** Opens the new-message screen; [draft] (forwarded text) goes into the conversation picked. */
    fun compose(draft: String = "")

    fun cancelCompose()

    fun setDraft(text: String)

    fun send()

    fun attach()

    fun removeAttachment(uri: String)

    fun suggestions(query: String): List<Pair<String, String>>

    /** [dialable] laid out for the phone's country, or null. */
    fun formatNumber(dialable: String): String?

    fun isValidNumber(dialable: String): Boolean

    fun grant()

    fun openSettings()

    fun makeDefault()

    fun pin(
        threads: Set<Long>,
        on: Boolean,
    )

    fun archive(
        threads: Set<Long>,
        on: Boolean,
    )

    fun markRead(threads: Set<Long>)

    fun deleteThreads(threads: Set<Long>)

    fun block(address: String)

    fun deleteMessages(ids: Set<String>)

    fun download(messageId: String)

    fun openAttachment(attachment: Attachment)

    fun share(text: String)

    /** Schedules the draft to send at [at] (epoch milliseconds). */
    fun schedule(at: Long)

    fun cancelScheduled(id: Long)

    fun sendScheduledNow(id: Long)

    /** Whether scheduled messages go out on time, or may be a few minutes late. */
    fun scheduleIsExact(): Boolean

    /** Opens the setting that lets scheduled messages go out on time. */
    fun allowExactSchedule()
}

private val Wide = 840.dp

@Composable
fun MessagesApp(
    state: MessagesState,
    open: OpenThread?,
    actions: MessagesActions,
    modifier: Modifier = Modifier,
) {
    val composing = state.composing
    BackHandler(open != null) { actions.close() }
    BackHandler(open == null && composing) { actions.cancelCompose() }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= Wide
        val list: @Composable (
            Modifier,
        ) -> Unit = { m -> ConversationList(state, open?.threadId, actions, onNew = { actions.compose() }, modifier = m) }
        val detail: @Composable (Modifier) -> Unit = { m ->
            when {
                open != null -> {
                    ConversationScreen(open, state, actions, showBack = !wide, onForward = { actions.compose(it) }, modifier = m)
                }

                composing -> {
                    NewMessage(
                        actions,
                        onClose = actions::cancelCompose,
                        onPick = { actions.startNew(it) },
                        modifier = m,
                    )
                }

                else -> {
                    Box(m.background(MaterialTheme.colorScheme.surface))
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width(400.dp).fillMaxHeight())
                VerticalDivider()
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (open != null || composing) {
            detail(Modifier.fillMaxSize())
        } else {
            list(Modifier.fillMaxSize())
        }
    }
}
