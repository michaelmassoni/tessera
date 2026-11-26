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
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.NeedsAccessScreen
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.messages.data.Foreground
import io.github.michaelmassoni.tessera.messages.data.HeadlessSmsSendService
import io.github.michaelmassoni.tessera.messages.data.LocalThreads
import io.github.michaelmassoni.tessera.messages.data.MessageStore
import io.github.michaelmassoni.tessera.messages.data.MessagesRepository
import io.github.michaelmassoni.tessera.messages.data.Mms
import io.github.michaelmassoni.tessera.messages.data.Notifier
import io.github.michaelmassoni.tessera.messages.data.PhoneNumbers
import io.github.michaelmassoni.tessera.messages.data.ScheduledMessages
import io.github.michaelmassoni.tessera.messages.data.TAG
import io.github.michaelmassoni.tessera.messages.data.isDefaultSmsApp
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.Sending
import io.github.michaelmassoni.tessera.messages.ui.MessagesActions
import io.github.michaelmassoni.tessera.messages.ui.MessagesApp
import io.github.michaelmassoni.tessera.messages.ui.MessagesState
import io.github.michaelmassoni.tessera.messages.ui.OpenThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class MainActivity : ComponentActivity() {
    private val repository by lazy { MessagesRepository(this) }
    private val phoneNumbers by lazy { PhoneNumbers(this) }
    private val scheduled by lazy { ScheduledMessages(this) }
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

    private val handler = Handler(Looper.getMainLooper())
    private val reloadSoon = Runnable { reload() }

    /**
     * One incoming message notifies several of the URIs observed (and a sync can notify many
     * times), so changes are gathered into a single reload shortly after the last one.
     */
    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(reloadSoon)
                handler.postDelayed(reloadSoon, RELOAD_DELAY_MS)
            }
        }

    /** A contact was added or renamed: names are read again, then the list reloads. */
    private val contactsObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                repository.forgetContacts()
                observer.onChange(selfChange)
            }
        }
    private var observingContacts = false

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
        setTesseraContent {
            SetupGate(messagesSetup) {
                if (state.loaded && !state.hasPermissions) {
                    // Conversations and sending all need these: nothing works without them.
                    NeedsAccessScreen(
                        DesignR.drawable.ic_contacts,
                        stringResource(R.string.setup_access_title),
                        stringResource(R.string.setup_access_text),
                        onAllow = actions::grant,
                    )
                } else {
                    MessagesApp(state, open, actions)
                }
            }
        }
        // Alarms can be lost (exact-alarm access revoked, force stop): set them again, and send
        // anything overdue.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { scheduled.rearm() } }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        reload()
        markOpenRead()
    }

    // The provider observers run only while the app is visible: onResume reloads anyway, and in
    // the background every incoming message would otherwise wake a reload nobody sees.
    override fun onStop() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        if (observingContacts) contentResolver.unregisterContentObserver(contactsObserver)
        observing = false
        observingContacts = false
        handler.removeCallbacks(reloadSoon)
        super.onStop()
    }

    override fun onPause() {
        resumed = false
        Foreground.openThread = null
        super.onPause()
    }

    override fun onDestroy() {
        if (observing) contentResolver.unregisterContentObserver(observer)
        if (observingContacts) contentResolver.unregisterContentObserver(contactsObserver)
        handler.removeCallbacks(reloadSoon)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasAll() = messagesRequired.all(::granted)

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
            // The launcher shortcut: straight to a new message.
            ACTION_NEW_MESSAGE -> {
                open = null
                state = state.copy(composing = true)
            }

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

    private fun scheduledFor(addresses: List<String>) = scheduled.all().filter { it.isFor(addresses) }

    private suspend fun refreshScheduled() {
        val addresses = open?.addresses ?: return
        val list = withContext(Dispatchers.IO) { scheduledFor(addresses) }
        if (open?.addresses == addresses) open = open?.copy(scheduled = list)
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
        if (!observingContacts && granted(Manifest.permission.READ_CONTACTS)) {
            contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
            observingContacts = true
        }
        lifecycleScope.launch {
            val (conversations, names, photos) =
                withContext(Dispatchers.IO) {
                    val list = runCatching { repository.conversations() }.getOrDefault(emptyList())
                    val addresses = list.flatMap { it.addresses }.distinct()
                    if (granted(Manifest.permission.READ_CONTACTS)) {
                        Triple(list, repository.names(addresses), repository.photos(addresses))
                    } else {
                        Triple(list, emptyMap(), emptyMap())
                    }
                }
            state =
                state.copy(
                    loaded = true,
                    conversations = conversations,
                    names = names,
                    photos = photos,
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
            suspend fun load(limit: Int?) =
                withContext(
                    Dispatchers.IO,
                ) { runCatching { repository.messages(thread, current.addresses, limit) }.getOrDefault(emptyList()) }

            fun stillOpen() = open?.threadId == thread && open?.addresses == current.addresses
            // Opening: show the newest messages straight away, then fill in the rest above them.
            // (Refreshes keep what's on screen and reload everything.)
            var first: List<Message>? = null
            if (open?.loaded != true) {
                first = load(FIRST_PAGE)
                if (!stillOpen()) return@launch
                open = open?.let { o -> o.copy(messages = Sending.merge(o.messages, first, System.currentTimeMillis()), loaded = true) }
            }
            // A thread shorter than the first page is already complete.
            val messages = if (first != null && first.size < FIRST_PAGE) first else load(null)
            if (stillOpen()) {
                open =
                    open?.let { o ->
                        o.copy(
                            messages = Sending.merge(o.messages, messages, System.currentTimeMillis()),
                            loaded = true,
                            scheduled = scheduledFor(current.addresses),
                        )
                    }
                val missing = current.addresses.filter { state.names[it] == null }
                if (missing.isNotEmpty() && granted(Manifest.permission.READ_CONTACTS)) {
                    val found = withContext(Dispatchers.IO) { repository.names(missing) }
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
            // Show it straight away; the stored copy replaces it on the next reload.
            val shown = Sending.message(text, current.attachments.map { Attachment(it, "image/jpeg") }, System.currentTimeMillis())
            open = current.copy(draft = "", attachments = emptyList(), messages = current.messages + shown)
            lifecycleScope.launch {
                val result =
                    withContext(
                        Dispatchers.IO,
                    ) { runCatching { repository.send(current.addresses, text, current.attachments.map(String::toUri)) } }
                if (result.isFailure) {
                    toast(R.string.send_failed)
                    open = open?.let { o -> o.copy(messages = o.messages.filterNot { it.key == shown.key }) }
                }
                if (current.threadId == null) startThread(current.addresses) else reload()
            }
        }

        override fun attach() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

        override fun removeAttachment(uri: String) {
            open = open?.let { it.copy(attachments = it.attachments - uri) }
        }

        override fun formatNumber(dialable: String) = phoneNumbers.format(dialable)

        override fun isValidNumber(dialable: String) = phoneNumbers.isValid(dialable)

        override fun suggestions(query: String) =
            if (granted(Manifest.permission.READ_CONTACTS)) repository.suggestions(query) else emptyList()

        override fun searchMessages(query: String) = repository.searchMessages(query)

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

        override fun schedule(at: Long) {
            val current = open ?: return
            val text = current.draft.trim()
            if (text.isEmpty() && current.attachments.isEmpty()) return
            open = current.copy(draft = "", attachments = emptyList())
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { scheduled.schedule(current.addresses, text, current.attachments.map(String::toUri), at) }
                refreshScheduled()
            }
        }

        override fun cancelScheduled(id: Long) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { scheduled.cancel(id) }
                refreshScheduled()
            }
        }

        override fun sendScheduledNow(id: Long) {
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { scheduled.send(id) } }
                if (result.isFailure) toast(R.string.send_failed)
                refreshScheduled()
                reload()
            }
        }

        override fun scheduleIsExact() = scheduled.exact()

        override fun allowExactSchedule() {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null)))
        }
    }

    companion object {
        const val EXTRA_THREAD = "io.github.michaelmassoni.tessera.messages.THREAD"
        const val ACTION_NEW_MESSAGE = "io.github.michaelmassoni.tessera.messages.NEW_MESSAGE"
        private const val MAX_PICTURES = 5
        private const val RELOAD_DELAY_MS = 300L

        /** Messages shown first when a conversation opens; the rest load just after. */
        private const val FIRST_PAGE = 100
        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
