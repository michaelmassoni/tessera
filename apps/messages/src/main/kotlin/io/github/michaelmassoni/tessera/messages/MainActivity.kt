package io.github.michaelmassoni.tessera.messages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.messages.data.MessagesRepository
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.ui.MessagesActions
import io.github.michaelmassoni.tessera.messages.ui.MessagesApp
import io.github.michaelmassoni.tessera.messages.ui.MessagesState
import io.github.michaelmassoni.tessera.messages.ui.OpenThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val repository by lazy { MessagesRepository(this) }
    private var state by mutableStateOf(MessagesState())
    private var open by mutableStateOf<OpenThread?>(null)
    private val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    private var observing = false

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }

    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val actions = Actions()
        setTesseraContent { MessagesApp(state, open, actions) }
        if (!hasAll()) request.launch(permissions)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasAll() = permissions.all(::granted)

    /** sms:/smsto: links from other apps open (or start) the conversation, with any text as a draft. */
    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SENDTO && intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme != "sms" && data.scheme != "smsto") return
        val address =
            Uri
                .decode(data.schemeSpecificPart)
                .substringBefore('?')
                .split(',', ';')
                .firstOrNull()
                ?.trim()
                .orEmpty()
        if (address.isEmpty()) return
        val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        startThread(address, body)
    }

    private fun startThread(
        address: String,
        draft: String = "",
    ) {
        open = OpenThread(null, address, draft = draft)
        lifecycleScope.launch {
            val thread = withContext(Dispatchers.IO) { if (granted(Manifest.permission.READ_SMS)) repository.threadFor(address) else null }
            open = open?.copy(threadId = thread)
            loadOpen()
        }
    }

    private fun reload() {
        if (!granted(Manifest.permission.READ_SMS)) {
            state = state.copy(loaded = true, hasPermissions = false)
            return
        }
        if (!observing) {
            // Sent messages written by the system notify content://sms rather than the combined URI.
            listOf("content://mms-sms/", "content://sms", "content://mms").forEach {
                contentResolver.registerContentObserver(it.toUri(), true, observer)
            }
            observing = true
        }
        lifecycleScope.launch {
            val (conversations, names) =
                withContext(Dispatchers.IO) {
                    val list = runCatching { repository.conversations() }.getOrDefault(emptyList())
                    val names =
                        if (granted(Manifest.permission.READ_CONTACTS)) {
                            list
                                .flatMap { it.addresses }
                                .distinct()
                                .mapNotNull { a ->
                                    repository.name(a)?.let {
                                        a to
                                            it
                                    }
                                }.toMap()
                        } else {
                            emptyMap()
                        }
                    list to names
                }
            state = MessagesState(true, conversations, names, hasAll())
            loadOpen()
        }
    }

    private fun loadOpen() {
        val current = open ?: return
        val thread = current.threadId
        lifecycleScope.launch {
            val messages =
                withContext(Dispatchers.IO) { runCatching { repository.messages(thread, current.address) }.getOrDefault(emptyList()) }
            if (open?.threadId == thread) {
                open = open?.copy(messages = messages)
                if (state.names[current.address] == null) {
                    withContext(Dispatchers.IO) { repository.name(current.address) }?.let {
                        state =
                            state.copy(names = state.names + (current.address to it))
                    }
                }
            }
        }
    }

    private inner class Actions : MessagesActions {
        override fun open(conversation: Conversation) {
            open = OpenThread(conversation.threadId, conversation.addresses.firstOrNull().orEmpty())
            loadOpen()
        }

        override fun startNew(address: String) = startThread(address)

        override fun close() {
            open = null
        }

        override fun setDraft(text: String) {
            open = open?.copy(draft = text)
        }

        override fun send() {
            val current = open ?: return
            val text = current.draft.trim()
            if (text.isEmpty() || !granted(Manifest.permission.SEND_SMS)) return
            open = current.copy(draft = "")
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { repository.send(current.address, text) } }
                if (current.threadId == null) startThread(current.address) else reload()
            }
        }

        override fun suggestions(query: String) =
            if (granted(Manifest.permission.READ_CONTACTS)) repository.suggestions(query) else emptyList()

        override fun grant() = request.launch(permissions)

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }
}
