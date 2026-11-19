package io.github.michaelmassoni.tessera.messages.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message

private const val T = 1_795_089_600_000L

private val conversations =
    listOf(
        Conversation(1, listOf("+61 412 345 678"), "See you at seven!", T, unread = true),
        Conversation(2, listOf("0298765432"), "Thanks, got it", T - 3_600_000, unread = false),
        Conversation(3, listOf("Telstra"), "Your bill is ready", T - 86_400_000, unread = false),
    )

private val names = mapOf("+61 412 345 678" to "Ada Lovelace")

private object NoActions : MessagesActions {
    override fun open(conversation: Conversation) = Unit

    override fun startNew(address: String) = Unit

    override fun close() = Unit

    override fun setDraft(text: String) = Unit

    override fun send() = Unit

    override fun suggestions(query: String) = emptyList<Pair<String, String>>()

    override fun grant() = Unit

    override fun openSettings() = Unit
}

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
        MessagesApp(MessagesState(true, conversations, names), OpenThread(1, "+61 412 345 678", messages, draft = "On my way"), NoActions)
    }
}
