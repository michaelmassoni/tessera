package io.github.michaelmassoni.tessera.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.messages.data.Foreground
import io.github.michaelmassoni.tessera.messages.data.HeadlessSmsSendService
import io.github.michaelmassoni.tessera.messages.data.LocalThreads
import io.github.michaelmassoni.tessera.messages.data.MessageStore
import io.github.michaelmassoni.tessera.messages.data.MessagesRepository
import io.github.michaelmassoni.tessera.messages.data.Mms
import io.github.michaelmassoni.tessera.messages.data.Notifier
import io.github.michaelmassoni.tessera.messages.data.TAG
import io.github.michaelmassoni.tessera.messages.data.isDefaultSmsApp
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.ui.MessagesActions
import io.github.michaelmassoni.tessera.messages.ui.MessagesApp
import io.github.michaelmassoni.tessera.messages.ui.MessagesState
import io.github.michaelmassoni.tessera.messages.ui.OpenThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val repository by lazy { MessagesRepository(this) }
    private val store by lazy { MessageStore(this) }
    private val local by lazy { LocalThreads(this) }
    private var state by mutableStateOf(MessagesState())
    private var open by mutableStateOf<OpenThread?>(null)

    /** Text and pictures shared from another app (or forwarded), waiting for a recipient. */
    private var pendingDraft = ""
    private var pendingAttachments = emptyList<String>()

    private var observing = false
    private var resumed = false

    private val permissions =
        listOfNotNull(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        ).toTypedArray()

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }

    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload() }
    private val picker =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICTURES)) { uris ->
            open = open?.let { it.copy(attachments = (it.attachments + uris.map(Uri::toString)).distinct().take(MAX_PICTURES)) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        val actions = Actions()
        setTesseraContent { MessagesApp(state, open, actions) }
        if (!hasAll()) request.launch(permissions)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        reload()
        markOpenRead()
    }

    override fun onPause() {
        resumed = false
        Foreground.openThread = null
        super.onPause()
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

    private fun handleIntent(intent: Intent) {
        // From a notification: open that conversation.
        intent.getLongExtra(EXTRA_THREAD, -1).takeIf { it >= 0 }?.let { thread ->
            intent.removeExtra(EXTRA_THREAD)
            lifecycleScope.launch {
                val addresses = withContext(Dispatchers.IO) { repository.addresses(thread) }
                showThread(OpenThread(thread, addresses))
            }
            return
        }
        when (intent.action) {
            // sms:/smsto:/mms:/mmsto: links open (or start) the conversation, with any text as a draft.
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme !in SCHEMES) return
                val addresses = HeadlessSmsSendService.recipients(data)
                if (addresses.isEmpty()) return
                pendingDraft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                startThread(addresses)
            }

            // Shared text or pictures from another app: pick who to send them to.
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                pendingDraft = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val one = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val many = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                pendingAttachments =
                    (listOfNotNull(one) + many).filter { contentResolver.getType(it)?.startsWith("image/") == true }.map(Uri::toString)
                open = null
                state = state.copy(composing = true)
            }
        }
    }

    private fun showThread(thread: OpenThread) {
        open = thread
        state = state.copy(composing = false)
        loadOpen()
        markOpenRead()
    }

    private fun startThread(addresses: List<String>) {
        val draft = pendingDraft
        val attachments = pendingAttachments
        pendingDraft = ""
        pendingAttachments = emptyList()
        showThread(OpenThread(null, addresses, draft = draft, attachments = attachments))
        lifecycleScope.launch {
            val thread =
                withContext(Dispatchers.IO) { if (granted(Manifest.permission.READ_SMS)) repository.threadFor(addresses) else null }
            if (open?.addresses == addresses) {
                open = open?.copy(threadId = thread)
                loadOpen()
                markOpenRead()
            }
        }
    }

    /** The open conversation is read: clear its notification and, as default app, mark it read. */
    private fun markOpenRead() {
        val thread = open?.threadId
        Foreground.openThread = if (resumed) thread else null
        if (thread == null || !resumed) return
        lifecycleScope.launch(Dispatchers.IO) {
            Notifier(this@MainActivity).cancel(thread)
            if (isDefaultSmsApp()) runCatching { store.markRead(listOf(thread)) }
        }
    }

    private fun reload() {
        if (!granted(Manifest.permission.READ_SMS)) {
            state = state.copy(loaded = true, hasPermissions = false, isDefault = isDefaultSmsApp())
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
            state =
                state.copy(
                    loaded = true,
                    conversations = conversations,
                    names = names,
                    hasPermissions = hasAll(),
                    isDefault = isDefaultSmsApp(),
                    pinned = local.pinned,
                    archived = local.archived,
                )
            loadOpen()
        }
    }

    private fun loadOpen() {
        val current = open ?: return
        val thread = current.threadId
        lifecycleScope.launch {
            val messages =
                withContext(Dispatchers.IO) { runCatching { repository.messages(thread, current.addresses) }.getOrDefault(emptyList()) }
            if (open?.threadId == thread && open?.addresses == current.addresses) {
                open = open?.copy(messages = messages)
                val missing = current.addresses.filter { state.names[it] == null }
                if (missing.isNotEmpty() && granted(Manifest.permission.READ_CONTACTS)) {
                    val found = withContext(Dispatchers.IO) { missing.mapNotNull { a -> repository.name(a)?.let { a to it } } }
                    if (found.isNotEmpty()) state = state.copy(names = state.names + found)
                }
            }
        }
    }

    /** Runs [block] off the main thread, then reloads. */
    private fun write(block: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching(block).onFailure { Log.w(TAG, "Write failed", it) } }
            reload()
        }
    }

    private fun toast(text: Int) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private inner class Actions : MessagesActions {
        override fun open(conversation: Conversation) = showThread(OpenThread(conversation.threadId, conversation.addresses))

        override fun startNew(addresses: List<String>) = startThread(addresses)

        override fun close() {
            open = null
            Foreground.openThread = null
        }

        override fun compose(draft: String) {
            pendingDraft = draft
            pendingAttachments = emptyList()
            close()
            state = state.copy(composing = true)
        }

        override fun cancelCompose() {
            pendingDraft = ""
            pendingAttachments = emptyList()
            state = state.copy(composing = false)
        }

        override fun setDraft(text: String) {
            open = open?.copy(draft = text)
        }

        override fun send() {
            val current = open ?: return
            val text = current.draft.trim()
            if ((text.isEmpty() && current.attachments.isEmpty()) || !granted(Manifest.permission.SEND_SMS)) return
            open = current.copy(draft = "", attachments = emptyList())
            lifecycleScope.launch {
                val result =
                    withContext(
                        Dispatchers.IO,
                    ) { runCatching { repository.send(current.addresses, text, current.attachments.map(String::toUri)) } }
                if (result.isFailure) toast(R.string.send_failed)
                if (current.threadId == null) startThread(current.addresses) else reload()
            }
        }

        override fun attach() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

        override fun removeAttachment(uri: String) {
            open = open?.let { it.copy(attachments = it.attachments - uri) }
        }

        override fun suggestions(query: String) =
            if (granted(Manifest.permission.READ_CONTACTS)) repository.suggestions(query) else emptyList()

        override fun grant() = request.launch(permissions)

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun makeDefault() {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roles.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                role.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        }

        override fun pin(
            threads: Set<Long>,
            on: Boolean,
        ) = write { local.pin(threads, on) }

        override fun archive(
            threads: Set<Long>,
            on: Boolean,
        ) {
            write { local.archive(threads, on) }
            toast(if (on) R.string.archived_done else R.string.unarchived_done)
        }

        override fun markRead(threads: Set<Long>) =
            write {
                store.markRead(threads)
                threads.forEach(Notifier(this@MainActivity)::cancel)
            }

        override fun deleteThreads(threads: Set<Long>) {
            if (open?.threadId in threads) close()
            write {
                store.deleteThreads(threads)
                local.forget(threads)
                threads.forEach(Notifier(this@MainActivity)::cancel)
            }
        }

        override fun block(address: String) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { store.block(address) }.getOrDefault(false) }
                toast(if (ok) R.string.blocked else R.string.block_failed)
            }
        }

        override fun deleteMessages(ids: Set<String>) = write { store.deleteMessages(ids) }

        override fun download(messageId: String) {
            val id = messageId.removePrefix("m").toLongOrNull() ?: return
            toast(R.string.downloading)
            write { Mms(this@MainActivity).retry(id) }
        }

        override fun openAttachment(attachment: Attachment) {
            lifecycleScope.launch {
                // Other apps can't read the message database, so hand them a private copy.
                val uri =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val dir = File(cacheDir, "mms/open").apply { mkdirs() }
                            val file =
                                File(
                                    dir,
                                    attachment.uri.toUri().lastPathSegment + "." +
                                        (attachment.mime.substringAfter('/').substringBefore(';')),
                                )
                            contentResolver.openInputStream(attachment.uri.toUri())?.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                            FileProvider.getUriForFile(this@MainActivity, "$packageName.mms", file)
                        }.getOrNull()
                    }
                if (uri == null) return@launch toast(R.string.cant_open)
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.cant_open)
                }
            }
        }

        override fun share(text: String) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        }
    }

    companion object {
        const val EXTRA_THREAD = "io.github.michaelmassoni.tessera.messages.THREAD"
        private const val MAX_PICTURES = 5
        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
