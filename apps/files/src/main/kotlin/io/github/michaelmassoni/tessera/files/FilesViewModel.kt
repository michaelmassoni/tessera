package io.github.michaelmassoni.tessera.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.files.data.FilesSettings
import io.github.michaelmassoni.tessera.files.data.OperationKind
import io.github.michaelmassoni.tessera.files.data.RunningOperation
import io.github.michaelmassoni.tessera.files.data.TransferOutcome
import io.github.michaelmassoni.tessera.files.data.Transfers
import io.github.michaelmassoni.tessera.files.data.ViewOptions
import io.github.michaelmassoni.tessera.files.data.Volume
import io.github.michaelmassoni.tessera.files.engine.Archives
import io.github.michaelmassoni.tessera.files.engine.FileEntry
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Naming
import io.github.michaelmassoni.tessera.files.engine.OperationException
import io.github.michaelmassoni.tessera.files.engine.Progress
import io.github.michaelmassoni.tessera.files.engine.Search
import io.github.michaelmassoni.tessera.files.engine.Sorting
import io.github.michaelmassoni.tessera.files.engine.Trash
import io.github.michaelmassoni.tessera.files.engine.TrashedItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

enum class ClipMode { Copy, Move }

data class Clipboard(
    val mode: ClipMode,
    val files: List<File>,
)

data class SearchState(
    val query: String = "",
    val results: List<FileEntry> = emptyList(),
    val running: Boolean = false,
)

data class FilesUiState(
    /** null = the home screen (volumes and standard folders). */
    val location: File? = null,
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    /** The folder exists but can't be listed (e.g. Android/data). */
    val unreadable: Boolean = false,
    val selection: Set<File> = emptySet(),
    val options: ViewOptions = ViewOptions(),
    /** Bookmarked folders that still exist. */
    val bookmarks: List<File> = emptyList(),
    val clipboard: Clipboard? = null,
    val operation: RunningOperation? = null,
    val search: SearchState? = null,
    val volumes: List<Volume> = emptyList(),
    /** The Trash screen, when it's open. */
    val trash: TrashState? = null,
)

data class TrashState(
    val items: List<TrashedItem> = emptyList(),
    val selection: Set<String> = emptySet(),
    val loading: Boolean = false,
)

sealed interface FilesEvent {
    data class Done(
        val kind: OperationKind,
        val count: Int,
    ) : FilesEvent

    data class Deleted(
        val count: Int,
    ) : FilesEvent

    /** Moved to the trash; [ids] undo it. */
    data class Trashed(
        val ids: List<String>,
    ) : FilesEvent

    data class Restored(
        val count: Int,
    ) : FilesEvent

    data class Failed(
        val reason: Reason,
    ) : FilesEvent

    enum class Reason { IntoItself, NoSpace, Io, Cancelled, NoApp, BadArchive }
}

private const val SEARCH_DELAY_MS = 250L

/**
 * Browser state and file actions. All disk access runs on [io]; the UI only renders state.
 */
class FilesViewModel(
    private val settings: FilesSettings,
    private val volumes: () -> List<Volume>,
    private val io: CoroutineDispatcher,
    private val ops: FileOperations,
    private val trash: Trash,
    // Starts a transfer in the foreground service ([Transfers]); null runs it here (tests).
    private val startTransfer: ((OperationKind, List<File>, File?) -> Boolean)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(FilesUiState(options = settings.options, bookmarks = bookmarked(settings)))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val eventChannel = Channel<FilesEvent>(Channel.BUFFERED)
    val events: Flow<FilesEvent> = eventChannel.receiveAsFlow()

    private var listing: Job? = null
    private var searching: Job? = null
    private var cancelOperation = AtomicBoolean(false)

    init {
        refreshVolumes()
        if (startTransfer != null) {
            // A transfer runs in the service: its progress shows here, and how it ended.
            viewModelScope.launch {
                Transfers.running.collect { r ->
                    _state.update { it.copy(operation = r ?: it.operation?.takeIf { op -> op.kind == null }) }
                }
            }
            viewModelScope.launch {
                Transfers.outcomes.collect { o ->
                    eventChannel.trySend(
                        if (o.failure ==
                            null
                        ) {
                            FilesEvent.Done(o.kind, o.count)
                        } else {
                            FilesEvent.Failed(reasonOf(o.failure))
                        },
                    )
                    reload()
                }
            }
        }
        // Items kept longer than 30 days go for good.
        viewModelScope.launch(io) { runCatching { trash.purge() } }
    }

    // ------------------------------------------------------------------ navigation

    fun open(dir: File?) {
        _state.update { it.copy(location = dir, selection = emptySet(), search = null, entries = emptyList()) }
        reload()
    }

    /** Back: leaves selection, then search, then goes up a folder, then home. @return false at home. */
    fun back(): Boolean {
        val s = _state.value
        when {
            s.trash != null && s.trash.selection.isNotEmpty() -> {
                clearTrashSelection()
            }

            s.trash != null -> {
                closeTrash()
            }

            s.selection.isNotEmpty() -> {
                clearSelection()
            }

            s.search != null -> {
                closeSearch()
            }

            s.location == null -> {
                return false
            }

            else -> {
                val parent = s.location.parentFile
                val atVolumeRoot = s.volumes.any { it.root == s.location } || parent == null || !parent.canRead()
                open(if (atVolumeRoot) null else parent)
            }
        }
        return true
    }

    fun refresh() {
        refreshVolumes()
        reload()
    }

    private fun refreshVolumes() {
        viewModelScope.launch {
            val list = withContext(io) { volumes() }
            _state.update { it.copy(volumes = list) }
        }
    }

    private fun reload() {
        val dir = _state.value.location ?: return
        val options = _state.value.options
        listing?.cancel()
        _state.update { it.copy(loading = true) }
        listing =
            viewModelScope.launch {
                val entries = withContext(io) { FileEntry.list(dir, options.showHidden)?.let { Sorting.sort(it, options.sort) } }
                _state.update {
                    if (it.location != dir) it else it.copy(entries = entries.orEmpty(), unreadable = entries == null, loading = false)
                }
            }
    }

    // ------------------------------------------------------------------ view options

    fun setOptions(options: ViewOptions) {
        settings.options = options
        val sortChanged = options.sort != _state.value.options.sort
        val hiddenChanged = options.showHidden != _state.value.options.showHidden
        _state.update { it.copy(options = options, entries = if (sortChanged) Sorting.sort(it.entries, options.sort) else it.entries) }
        if (hiddenChanged) reload()
    }

    /** Picks up changes made on the settings screen. */
    fun reloadOptions() {
        val stored = settings.options
        if (stored != _state.value.options) setOptions(stored)
    }

    // ------------------------------------------------------------------ selection

    fun toggleSelection(file: File) =
        _state.update { s -> s.copy(selection = if (file in s.selection) s.selection - file else s.selection + file) }

    fun selectAll() = _state.update { s -> s.copy(selection = (s.search?.results ?: s.entries).map { it.file }.toSet()) }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    // ------------------------------------------------------------------ copy / move / delete

    /** Adds [dir] to the bookmarks, or removes it if it's there. */
    fun toggleBookmark(dir: File) {
        val paths = settings.bookmarks
        val path = dir.absolutePath
        settings.bookmarks = if (path in paths) paths - path else paths + path
        _state.update { it.copy(bookmarks = bookmarked(settings), selection = emptySet()) }
    }

    fun copySelection() = clip(ClipMode.Copy)

    fun moveSelection() = clip(ClipMode.Move)

    private fun clip(mode: ClipMode) =
        _state.update { s -> s.copy(clipboard = Clipboard(mode, s.selection.toList()), selection = emptySet(), search = null) }

    fun cancelClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() {
        val s = _state.value
        val clip = s.clipboard ?: return
        val target = s.location ?: return
        val kind = if (clip.mode == ClipMode.Copy) OperationKind.Copy else OperationKind.Move
        if (transfer(kind, clip.files, target)) _state.update { it.copy(clipboard = null) }
    }

    /** Zips the selection into a new .zip in this folder. */
    fun compressSelection() {
        val s = _state.value
        val target = s.location ?: return
        if (transfer(OperationKind.Compress, s.selection.toList(), target)) _state.update { it.copy(selection = emptySet()) }
    }

    /** Extracts a zip into a new folder beside it. */
    fun extract(zip: File) {
        if (transfer(OperationKind.Extract, listOf(zip), null)) _state.update { it.copy(selection = emptySet()) }
    }

    private fun transfer(
        kind: OperationKind,
        files: List<File>,
        target: File?,
    ): Boolean {
        if (files.isEmpty()) return false
        startTransfer?.let { return it(kind, files, target) }
        cancelOperation = AtomicBoolean(false)
        val cancel = cancelOperation
        _state.update { it.copy(operation = RunningOperation(kind, null)) }
        viewModelScope.launch {
            val error =
                withContext(io) {
                    try {
                        val report: (Progress) -> Unit = { p -> _state.update { it.copy(operation = RunningOperation(kind, p)) } }
                        when (kind) {
                            OperationKind.Copy -> ops.copy(files, target!!, cancel, report)
                            OperationKind.Move -> ops.move(files, target!!, cancel, report)
                            OperationKind.Compress -> Archives.compress(files, target!!, cancel, report)
                            OperationKind.Extract -> files.forEach { Archives.extract(it, cancel, report) }
                        }
                        null
                    } catch (e: IOException) {
                        e
                    }
                }
            _state.update { it.copy(operation = null) }
            eventChannel.trySend(
                if (error ==
                    null
                ) {
                    FilesEvent.Done(kind, files.size)
                } else {
                    FilesEvent.Failed(reasonOf(Transfers.failureOf(error)))
                },
            )
            reload()
        }
        return true
    }

    fun cancelRunningOperation() {
        cancelOperation.set(true)
        Transfers.cancel()
    }

    /** No installed app can open a file the user tapped. */
    fun reportNoApp() {
        eventChannel.trySend(FilesEvent.Failed(FilesEvent.Reason.NoApp))
    }

    fun delete(files: Collection<File>) {
        val list = files.toList()
        _state.update { it.copy(operation = RunningOperation(null, null), selection = emptySet()) }
        viewModelScope.launch {
            // Into the trash, not gone: Undo (or the Trash screen) brings it back for 30 days.
            val moved = mutableListOf<TrashedItem>()
            val error =
                withContext(io) {
                    runCatching { moved += trash.move(list, ::rootOf) }.exceptionOrNull() as? IOException
                }
            _state.update { s ->
                s.copy(
                    operation = null,
                    search =
                        s.search?.let { search ->
                            search.copy(results = search.results.filter { it.file.exists() })
                        },
                )
            }
            eventChannel.trySend(if (error == null) FilesEvent.Trashed(moved.map { it.id }) else FilesEvent.Failed(FilesEvent.Reason.Io))
            reload()
        }
    }

    // ------------------------------------------------------------------ trash

    /** The volume [file] is on, whose root holds its trash (its own folder as a last resort). */
    private fun rootOf(file: File): File =
        _state.value.volumes
            .firstOrNull { Naming.isInside(file, it.root) }
            ?.root ?: file.absoluteFile.parentFile ?: file

    fun openTrash() {
        _state.update { it.copy(trash = TrashState(loading = true), selection = emptySet(), search = null) }
        reloadTrash()
    }

    private fun reloadTrash() {
        viewModelScope.launch {
            val items =
                withContext(io) {
                    trash.purge()
                    trash.items()
                }
            _state.update { s ->
                s.trash?.let { t ->
                    s.copy(
                        trash =
                            t.copy(
                                items = items,
                                loading = false,
                                selection =
                                    t.selection
                                        .filter { id ->
                                            items.any {
                                                it.id ==
                                                    id
                                            }
                                        }.toSet(),
                            ),
                    )
                }
                    ?: s
            }
        }
    }

    fun closeTrash() = _state.update { it.copy(trash = null) }

    fun toggleTrashSelection(id: String) =
        _state.update { s ->
            s.copy(
                trash =
                    s.trash?.let { t ->
                        t.copy(
                            selection =
                                if (id in
                                    t.selection
                                ) {
                                    t.selection - id
                                } else {
                                    t.selection + id
                                },
                        )
                    },
            )
        }

    fun selectAllTrash() = _state.update { s -> s.copy(trash = s.trash?.let { t -> t.copy(selection = t.items.map { it.id }.toSet()) }) }

    fun clearTrashSelection() = _state.update { s -> s.copy(trash = s.trash?.copy(selection = emptySet())) }

    /** Puts items back (also Undo after moving to the trash). */
    fun restore(ids: Collection<String>) {
        viewModelScope.launch {
            val restored = withContext(io) { runCatching { trash.restore(ids) }.getOrNull() }
            _state.update { s -> s.copy(trash = s.trash?.copy(selection = emptySet())) }
            eventChannel.trySend(if (restored != null) FilesEvent.Restored(restored.size) else FilesEvent.Failed(FilesEvent.Reason.Io))
            if (_state.value.trash != null) reloadTrash()
            reload()
        }
    }

    /** Deletes items in the trash for good; [ids] null empties it. */
    fun deleteForever(ids: Collection<String>?) {
        viewModelScope.launch {
            val count =
                ids?.size ?: _state.value.trash
                    ?.items
                    ?.size ?: 0
            val error = withContext(io) { runCatching { if (ids == null) trash.empty() else trash.delete(ids) }.exceptionOrNull() }
            _state.update { s -> s.copy(trash = s.trash?.copy(selection = emptySet())) }
            eventChannel.trySend(if (error == null) FilesEvent.Deleted(count) else FilesEvent.Failed(FilesEvent.Reason.Io))
            reloadTrash()
        }
    }

    /** @return the problem with [newName], or null if the rename was started. */
    fun rename(
        file: File,
        newName: String,
    ): NameProblem? {
        val name = newName.trim()
        if (name == file.name) return null
        // Case-only renames ("a.txt" -> "A.txt") are allowed even though the name "exists".
        val problem =
            Naming.validate(name, file.parentFile).takeUnless {
                it == NameProblem.Exists &&
                    name.equals(file.name, ignoreCase = true)
            }
        if (problem != null) return problem
        _state.update { it.copy(selection = emptySet()) }
        viewModelScope.launch {
            val ok = withContext(io) { file.renameTo(File(file.parentFile, name)) }
            if (!ok) eventChannel.trySend(FilesEvent.Failed(FilesEvent.Reason.Io))
            reload()
        }
        return null
    }

    fun createFolder(name: String): NameProblem? {
        val dir = _state.value.location ?: return NameProblem.Empty
        val trimmed = name.trim()
        Naming.validate(trimmed, dir)?.let { return it }
        viewModelScope.launch {
            val ok = withContext(io) { File(dir, trimmed).mkdir() }
            if (!ok) eventChannel.trySend(FilesEvent.Failed(FilesEvent.Reason.Io))
            reload()
        }
        return null
    }

    // ------------------------------------------------------------------ search

    fun openSearch() = _state.update { it.copy(search = SearchState(), selection = emptySet()) }

    fun search(query: String) {
        val root =
            _state.value.location ?: _state.value.volumes
                .firstOrNull { it.isPrimary }
                ?.root ?: return
        val showHidden = _state.value.options.showHidden
        searching?.cancel()
        _state.update { it.copy(search = SearchState(query, emptyList(), running = query.isNotBlank())) }
        if (query.isBlank()) return
        searching =
            viewModelScope.launch {
                // Typing restarts the search: wait for a pause rather than walking the disk per key.
                delay(SEARCH_DELAY_MS)
                val job = coroutineContext[Job]
                withContext(io) {
                    val batch = mutableListOf<FileEntry>()
                    var lastFlush = System.nanoTime()
                    Search.search(root, query, showHidden, isCancelled = { job?.isActive == false }) { file ->
                        batch += FileEntry.of(file)
                        // Stream results in small batches so the list fills in as the walk goes.
                        if (System.nanoTime() - lastFlush > 100_000_000) {
                            val chunk = batch.toList()
                            batch.clear()
                            lastFlush = System.nanoTime()
                            _state.update { s -> s.copy(search = s.search?.copy(results = s.search.results + chunk)) }
                        }
                    }
                    val rest = batch.toList()
                    _state.update { s -> s.copy(search = s.search?.copy(results = s.search.results + rest, running = false)) }
                }
            }
    }

    fun closeSearch() {
        searching?.cancel()
        _state.update { it.copy(search = null) }
    }

    private fun reasonOf(f: TransferOutcome.Failure) =
        when (f) {
            TransferOutcome.Failure.IntoItself -> FilesEvent.Reason.IntoItself
            TransferOutcome.Failure.NoSpace -> FilesEvent.Reason.NoSpace
            TransferOutcome.Failure.Cancelled -> FilesEvent.Reason.Cancelled
            TransferOutcome.Failure.BadArchive -> FilesEvent.Reason.BadArchive
            TransferOutcome.Failure.Io -> FilesEvent.Reason.Io
        }
}

private fun bookmarked(settings: FilesSettings) = settings.bookmarks.map(::File).filter { it.isDirectory }
