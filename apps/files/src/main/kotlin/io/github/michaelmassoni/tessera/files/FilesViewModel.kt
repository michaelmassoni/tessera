package io.github.michaelmassoni.tessera.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.files.data.FilesSettings
import io.github.michaelmassoni.tessera.files.data.ViewOptions
import io.github.michaelmassoni.tessera.files.data.Volume
import io.github.michaelmassoni.tessera.files.engine.FileEntry
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Naming
import io.github.michaelmassoni.tessera.files.engine.OperationException
import io.github.michaelmassoni.tessera.files.engine.Progress
import io.github.michaelmassoni.tessera.files.engine.Search
import io.github.michaelmassoni.tessera.files.engine.Sorting
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

data class RunningOperation(
    val mode: ClipMode?,
    val progress: Progress?,
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
    val clipboard: Clipboard? = null,
    val operation: RunningOperation? = null,
    val search: SearchState? = null,
    val volumes: List<Volume> = emptyList(),
)

sealed interface FilesEvent {
    data class Done(
        val mode: ClipMode,
        val count: Int,
    ) : FilesEvent

    data class Deleted(
        val count: Int,
    ) : FilesEvent

    data class Failed(
        val reason: Reason,
    ) : FilesEvent

    enum class Reason { IntoItself, NoSpace, Io, Cancelled, NoApp }
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
) : ViewModel() {
    private val _state = MutableStateFlow(FilesUiState(options = settings.options))
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val eventChannel = Channel<FilesEvent>(Channel.BUFFERED)
    val events: Flow<FilesEvent> = eventChannel.receiveAsFlow()

    private var listing: Job? = null
    private var searching: Job? = null
    private var cancelOperation = AtomicBoolean(false)

    init {
        refreshVolumes()
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

    fun copySelection() = clip(ClipMode.Copy)

    fun moveSelection() = clip(ClipMode.Move)

    private fun clip(mode: ClipMode) =
        _state.update { s -> s.copy(clipboard = Clipboard(mode, s.selection.toList()), selection = emptySet(), search = null) }

    fun cancelClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() {
        val s = _state.value
        val clip = s.clipboard ?: return
        val target = s.location ?: return
        cancelOperation = AtomicBoolean(false)
        val cancel = cancelOperation
        _state.update { it.copy(operation = RunningOperation(clip.mode, null), clipboard = null) }
        viewModelScope.launch {
            val error =
                withContext(io) {
                    try {
                        val report: (Progress) -> Unit = { p -> _state.update { it.copy(operation = RunningOperation(clip.mode, p)) } }
                        when (clip.mode) {
                            ClipMode.Copy -> ops.copy(clip.files, target, cancel, report)
                            ClipMode.Move -> ops.move(clip.files, target, cancel, report)
                        }
                        null
                    } catch (e: IOException) {
                        e
                    }
                }
            _state.update { it.copy(operation = null) }
            eventChannel.trySend(if (error == null) FilesEvent.Done(clip.mode, clip.files.size) else FilesEvent.Failed(reasonOf(error)))
            reload()
        }
    }

    fun cancelRunningOperation() = cancelOperation.set(true)

    /** No installed app can open a file the user tapped. */
    fun reportNoApp() {
        eventChannel.trySend(FilesEvent.Failed(FilesEvent.Reason.NoApp))
    }

    fun delete(files: Collection<File>) {
        val list = files.toList()
        _state.update { it.copy(operation = RunningOperation(null, null), selection = emptySet()) }
        viewModelScope.launch {
            val error = withContext(io) { runCatching { ops.delete(list) }.exceptionOrNull() as? IOException }
            _state.update { s ->
                s.copy(
                    operation = null,
                    search =
                        s.search?.let { search ->
                            search.copy(results = search.results.filter { it.file.exists() })
                        },
                )
            }
            eventChannel.trySend(if (error == null) FilesEvent.Deleted(list.size) else FilesEvent.Failed(FilesEvent.Reason.Io))
            reload()
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

    private fun reasonOf(e: IOException) =
        when (e) {
            is OperationException.IntoItself -> FilesEvent.Reason.IntoItself
            is OperationException.NotEnoughSpace -> FilesEvent.Reason.NoSpace
            is OperationException.Cancelled -> FilesEvent.Reason.Cancelled
            else -> FilesEvent.Reason.Io
        }
}
