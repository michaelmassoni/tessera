package io.github.michaelmassoni.tessera.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.gallery.data.MediaRepository
import io.github.michaelmassoni.tessera.gallery.engine.Album
import io.github.michaelmassoni.tessera.gallery.engine.Albums
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which items a grid or the viewer is showing. */
sealed interface Collection {
    data object All : Collection

    data class Folder(
        val id: Long,
        val name: String,
    ) : Collection

    data object Favorites : Collection

    data object Videos : Collection

    data object Trash : Collection
}

data class GalleryState(
    val loaded: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val trash: List<MediaItem> = emptyList(),
    val selection: Set<Long> = emptySet(),
)

/** Media lists and selection. Reloads when MediaStore changes (the activity calls [refresh]). */
class GalleryViewModel(
    private val repository: MediaRepository,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()
    private var loading: Job? = null

    /**
     * Reloads the library. [after] waits first, so a burst of MediaStore changes (a camera burst, a
     * download, a scan) loads once: each call cancels the one before.
     */
    fun refresh(after: Long = 0) {
        loading?.cancel()
        loading =
            viewModelScope.launch {
                if (after > 0) delay(after)
                // The library and the trash are two MediaStore queries; run them together.
                val trashJob = async(io) { repository.loadTrash() }
                val items = withContext(io) { repository.loadAll() }
                val trash = trashJob.await()
                _state.update { s ->
                    val ids = (items + trash).map { it.id }.toSet()
                    s.copy(
                        loaded = true,
                        items = items,
                        albums = Albums.fromItems(items),
                        trash = trash,
                        selection =
                            s.selection intersect ids,
                    )
                }
            }
    }

    fun items(collection: Collection): List<MediaItem> {
        val s = _state.value
        return when (collection) {
            Collection.All -> s.items
            is Collection.Folder -> s.items.filter { it.bucketId == collection.id }
            Collection.Favorites -> s.items.filter { it.favorite }
            Collection.Videos -> s.items.filter { it.isVideo }
            Collection.Trash -> s.trash
        }
    }

    fun toggleSelection(id: Long) = _state.update { s -> s.copy(selection = if (id in s.selection) s.selection - id else s.selection + id) }

    /** Selects all of [ids], or clears them if they are all selected already (tapping a day heading). */
    fun toggleGroup(ids: List<Long>) =
        _state.update { s ->
            s.copy(selection = if (s.selection.containsAll(ids)) s.selection - ids.toSet() else s.selection + ids)
        }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun selectedItems(): List<MediaItem> {
        val s = _state.value
        return (s.items + s.trash).filter { it.id in s.selection }
    }
}
