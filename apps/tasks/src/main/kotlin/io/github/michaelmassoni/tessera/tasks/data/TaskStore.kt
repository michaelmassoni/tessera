package io.github.michaelmassoni.tessera.tasks.data

import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** Every list and task, as kept in `files/tasks.json`. */
@Serializable
data class TaskData(
    val lists: List<TaskList> = emptyList(),
    val tasks: List<Task> = emptyList(),
) {
    fun list(id: String) = lists.firstOrNull { it.id == id }

    fun task(id: String) = tasks.firstOrNull { it.id == id }

    fun tasksOf(listId: String) = tasks.filter { it.listId == listId && !it.deleted }
}

/**
 * The one copy of the tasks in this process, shared by the app, the widget, reminders and sync.
 * Changes apply at once to [data] and are written to disk in order, atomically.
 */
class TaskStore(
    private val file: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writes = Any()
    private val state = MutableStateFlow(load())
    val data: StateFlow<TaskData> = state.asStateFlow()

    private fun load(): TaskData {
        val loaded =
            try {
                if (file.exists()) json.decodeFromString(TaskData.serializer(), file.readText()) else null
            } catch (_: IOException) {
                null
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        // A first list, on this phone, so there's always somewhere to add a task.
        return loaded?.takeIf { it.lists.isNotEmpty() } ?: TaskData(lists = listOf(TaskList(UUID.randomUUID().toString(), DEFAULT_NAME)))
    }

    /** Applies [change] and saves. Thread-safe; changes apply in the order made. */
    fun update(change: (TaskData) -> TaskData) {
        var updated: TaskData
        synchronized(writes) {
            updated = change(state.value)
            if (updated == state.value) return
            state.value = updated
        }
        val text = json.encodeToString(TaskData.serializer(), updated)
        scope.launch(Dispatchers.IO) {
            synchronized(writes) {
                // A later change may have been written already: write only the latest.
                if (state.value != updated) return@synchronized
                try {
                    val temp = File(file.path + ".tmp")
                    temp.writeText(text)
                    temp.renameTo(file)
                } catch (_: IOException) {
                    // The next change writes everything again.
                }
            }
        }
    }

    /** Re-reads the file (after a restore from backup). */
    fun reload() {
        synchronized(writes) { state.value = load() }
    }

    companion object {
        /** Renamed by the app to the localised "My Tasks" when shown. */
        const val DEFAULT_NAME = "My Tasks"
    }
}
