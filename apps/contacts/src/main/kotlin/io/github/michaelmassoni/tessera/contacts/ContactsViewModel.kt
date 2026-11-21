package io.github.michaelmassoni.tessera.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.contacts.data.ContactsRepository
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.ContactIndex
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.contacts.engine.Section
import io.github.michaelmassoni.tessera.contacts.engine.VCardContact
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ListState(
    val loaded: Boolean = false,
    val total: Int = 0,
    val sections: List<Pair<Section, List<ContactSummary>>> = emptyList(),
    val search: String = "",
    val results: List<ContactSummary> = emptyList(),
    val accounts: List<AccountRef> = emptyList(),
    val accountFilter: AccountRef? = null,
)

data class EditorState(
    /** null for a new contact. */
    val contactId: Long?,
    val rawContactId: Long?,
    val original: ContactForm,
    val form: ContactForm,
    val account: AccountRef,
    val accounts: List<AccountRef>,
    val saving: Boolean = false,
) {
    val changed: Boolean get() = form != original
}

sealed interface ContactsEvent {
    data class Saved(
        val id: Long?,
    ) : ContactsEvent

    data object SaveFailed : ContactsEvent

    data class Deleted(
        val name: String,
    ) : ContactsEvent

    data class Imported(
        val count: Int,
    ) : ContactsEvent

    data object ImportFailed : ContactsEvent
}

/**
 * The contact list, the open contact and the editor. All provider access runs on [io]; the list
 * reloads when the contacts database changes (the activity calls [refresh] from a ContentObserver).
 */
class ContactsViewModel(
    private val repository: ContactsRepository,
    private val io: CoroutineDispatcher,
    private val lastAccount: () -> AccountRef?,
    private val rememberAccount: (AccountRef) -> Unit,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    private val all = MutableStateFlow<List<ContactSummary>?>(null)
    private val accounts = MutableStateFlow<List<AccountRef>>(emptyList())
    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow<AccountRef?>(null)

    val list: StateFlow<ListState> =
        combine(all, accounts, search, filter) { contacts, accts, q, f ->
            val visible = contacts.orEmpty().filter { f == null || f in it.accounts }
            ListState(
                loaded = contacts != null,
                total = visible.size,
                sections = ContactIndex.sections(visible, locale()),
                search = q,
                results = if (q.isBlank()) emptyList() else ContactIndex.search(visible, q, locale()),
                accounts = accts,
                accountFilter = f,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ListState())

    private val _detail = MutableStateFlow<ContactDetail?>(null)
    val detail: StateFlow<ContactDetail?> = _detail.asStateFlow()

    /** The id of the open contact (its detail may still be loading). */
    private val _openId = MutableStateFlow<Long?>(null)
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private val eventChannel = Channel<ContactsEvent>(Channel.BUFFERED)
    val events: Flow<ContactsEvent> = eventChannel.receiveAsFlow()

    private var loading: Job? = null

    /** Reloads the list; [after] waits first, so a burst of changes (each cancels the last) loads once. */
    fun refresh(after: Long = 0) {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                if (after > 0) delay(after)
                val (contacts, accts) = withContext(io) { repository.loadAll() to repository.accounts() }
                all.value = contacts
                accounts.value = accts
                if (filter.value != null && filter.value !in accts) filter.value = null
                _openId.value?.let { id -> reloadDetail(id) }
            }
    }

    fun setSearch(text: String) {
        search.value = text
    }

    fun setAccountFilter(account: AccountRef?) {
        filter.value = account
    }

    // ------------------------------------------------------------------ detail

    fun open(id: Long) {
        if (_openId.value == id && _detail.value != null) return
        _openId.value = id
        _detail.value = null
        viewModelScope.launch { reloadDetail(id) }
    }

    fun close() {
        _openId.value = null
        _detail.value = null
    }

    private suspend fun reloadDetail(id: Long) {
        val detail = withContext(io) { repository.loadDetail(id) }
        if (_openId.value != id) return
        if (detail == null) close() else _detail.value = detail
    }

    fun toggleStarred() {
        val d = _detail.value ?: return
        _detail.value = d.copy(starred = !d.starred)
        viewModelScope.launch(io) { repository.setStarred(d.id, !d.starred) }
    }

    fun delete() {
        val d = _detail.value ?: return
        close()
        viewModelScope.launch {
            withContext(io) { repository.delete(d.id) }
            eventChannel.trySend(ContactsEvent.Deleted(d.name))
        }
    }

    // ------------------------------------------------------------------ editor

    fun editOpen() {
        val d = _detail.value ?: return
        _editor.value =
            EditorState(
                contactId = d.id,
                rawContactId = d.rawContactId,
                original = d.form,
                form = d.form,
                account = d.accounts.firstOrNull() ?: AccountRef.Device,
                accounts = emptyList(),
            )
    }

    /** Opens the editor for a new contact, optionally pre-filled (from another app's insert intent). */
    fun create(prefill: ContactForm = ContactForm()) {
        val accts = accounts.value.ifEmpty { listOf(AccountRef.Device) }
        val account = lastAccount()?.takeIf { it in accts } ?: accts.first()
        _editor.value = EditorState(null, null, ContactForm(), prefill, account, accts)
    }

    fun updateForm(form: ContactForm) = _editor.update { it?.copy(form = form) }

    fun setAccount(account: AccountRef) = _editor.update { it?.copy(account = account) }

    fun cancelEdit() {
        _editor.value = null
    }

    fun save() {
        val e = _editor.value ?: return
        if (e.saving) return
        if (!e.changed || e.form.isEmpty) {
            _editor.value = null
            return
        }
        _editor.value = e.copy(saving = true)
        viewModelScope.launch {
            val id =
                withContext(io) {
                    runCatching {
                        if (e.contactId != null && e.rawContactId != null) {
                            repository.update(e.rawContactId, e.original, e.form)
                            e.contactId
                        } else {
                            repository.create(e.account, e.form).also { rememberAccount(e.account) }
                        }
                    }
                }
            _editor.value = null
            id
                .onSuccess { newId ->
                    eventChannel.trySend(ContactsEvent.Saved(newId))
                    refresh()
                    if (newId != null) open(newId)
                }.onFailure { eventChannel.trySend(ContactsEvent.SaveFailed) }
        }
    }

    // ------------------------------------------------------------------ import

    fun import(cards: List<VCardContact>) {
        if (cards.isEmpty()) {
            eventChannel.trySend(ContactsEvent.ImportFailed)
            return
        }
        val account = lastAccount()?.takeIf { it in accounts.value } ?: AccountRef.Device
        viewModelScope.launch {
            val count = withContext(io) { repository.import(account, cards) }
            eventChannel.trySend(if (count > 0) ContactsEvent.Imported(count) else ContactsEvent.ImportFailed)
            refresh()
        }
    }

    fun reportImportFailed() {
        eventChannel.trySend(ContactsEvent.ImportFailed)
    }

    /** Lookup keys of every contact shown (for export). */
    fun visibleLookupKeys(): List<String> =
        list.value.sections
            .filter { it.first is Section.Letter }
            .flatMap { it.second }
            .map { it.lookupKey }
            .filter { it.isNotEmpty() }
}
