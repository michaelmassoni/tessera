package io.github.michaelmassoni.tessera.messages.data

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.messages.engine.Addresses
import io.github.michaelmassoni.tessera.messages.engine.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages this app sent. Android records them in the shared SMS database, where every other
 * messaging app sees them, but hides them from a non-default app that sent them. Keeping a
 * private copy lets the conversation show them here too. Capped at the most recent [MAX].
 */
class SentLog(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("sent", Context.MODE_PRIVATE)

    private fun read(): List<JSONObject> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Synchronized
    fun add(
        address: String,
        body: String,
        date: Long,
    ) {
        val entries = read() + JSONObject().put("a", address).put("b", body).put("d", date)
        prefs.edit { putString(KEY, JSONArray(entries.takeLast(MAX)).toString()) }
    }

    /** Sent messages to [address], as outgoing messages. */
    fun messages(address: String): List<Message> =
        read()
            .filter { Addresses.same(it.optString("a"), address) }
            .map { Message(id = "l" + it.optLong("d"), body = it.optString("b"), date = it.optLong("d"), outgoing = true) }

    private companion object {
        const val KEY = "sent"
        const val MAX = 1000
    }
}
