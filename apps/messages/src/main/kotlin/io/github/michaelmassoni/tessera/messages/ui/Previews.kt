package io.github.michaelmassoni.tessera.messages.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.ScheduledMessage

private const val T = 1_795_089_600_000L

private val conversations =
    listOf(
        Conversation(1, listOf("+61 412 345 678"), "See you at seven!", T, unread = true),
        Conversation(2, listOf("0298765432"), "Thanks, got it", T - 3_600_000, unread = false),
        Conversation(3, listOf("+61 412 345 678", "0298765432", "0400111222"), "Who's bringing dessert?", T - 7_200_000, unread = false),
        Conversation(4, listOf("Telstra"), "Your bill is ready", T - 86_400_000, unread = false),
    )

private val names = mapOf("+61 412 345 678" to "Ada Lovelace", "0298765432" to "Alan Turing")

private object NoActions : MessagesActions {
    override fun open(conversation: Conversation) = Unit

    override fun startNew(addresses: List<String>) = Unit

    override fun close() = Unit

    override fun compose(draft: String) = Unit

    override fun cancelCompose() = Unit

    override fun setDraft(text: String) = Unit

    override fun send() = Unit

    override fun attach() = Unit

    override fun removeAttachment(uri: String) = Unit

    override fun suggestions(query: String) = emptyList<Pair<String, String>>()

    override fun formatNumber(dialable: String) =
        if (dialable.length ==
            10
        ) {
            "${dialable.take(4)} ${dialable.substring(4, 7)} ${dialable.substring(7)}"
        } else {
            null
        }

    override fun isValidNumber(dialable: String) = dialable.length == 10

    override fun grant() = Unit

    override fun openSettings() = Unit

    override fun makeDefault() = Unit

    override fun pin(
        threads: Set<Long>,
        on: Boolean,
    ) = Unit

    override fun archive(
        threads: Set<Long>,
        on: Boolean,
    ) = Unit

    override fun markRead(threads: Set<Long>) = Unit

    override fun deleteThreads(threads: Set<Long>) = Unit

    override fun block(address: String) = Unit

    override fun deleteMessages(ids: Set<String>) = Unit

    override fun download(messageId: String) = Unit

    override fun openAttachment(attachment: Attachment) = Unit

    override fun share(text: String) = Unit

    override fun schedule(at: Long) = Unit

    override fun cancelScheduled(id: Long) = Unit

    override fun sendScheduledNow(id: Long) = Unit

    override fun scheduleIsExact() = true

    override fun allowExactSchedule() = Unit
}

private val state = MessagesState(true, conversations, names, isDefault = true, pinned = setOf(2L))

@TesseraPreviews
@Composable
private fun ThreadPreview() {
    val messages =
        listOf(
            Message("1", "Are we still on for dinner?", T - 600_000, outgoing = false),
            Message("2", "Yes! Booked for seven.", T - 540_000, outgoing = true),
            Message("3", "See you at seven!", T - 500_000, outgoing = false),
        )
    TesseraTheme(dynamicColor = false) {
        MessagesApp(
            state,
            OpenThread(
                1,
                listOf("+61 412 345 678"),
                messages,
                draft = "On my way",
                loaded = true,
                scheduled = listOf(ScheduledMessage(1, listOf("+61 412 345 678"), "Happy birthday!", 1_795_161_600_000)),
            ),
            NoActions,
        )
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ListPreview() {
    TesseraTheme(dynamicColor = false) { MessagesApp(state.copy(isDefault = false), null, NoActions) }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun GroupPreview() {
    val messages =
        listOf(
            Message("1", "Who's bringing dessert?", T - 900_000, outgoing = false, sender = "+61 412 345 678"),
            Message("2", "I can do a pavlova", T - 800_000, outgoing = false, sender = "0298765432"),
            Message("3", "Sounds good", T - 790_000, outgoing = false, sender = "0298765432"),
            Message("4", "", T - 600_000, outgoing = false, sender = "0400111222", downloadable = true),
            Message("5", "Perfect, see you all there", T - 500_000, outgoing = true),
        )
    TesseraTheme(dynamicColor = false, darkTheme = true) {
        MessagesApp(state, OpenThread(3, conversations[2].addresses, messages, loaded = true), NoActions)
    }
}
