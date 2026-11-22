package io.github.michaelmassoni.tessera.tasks

import android.app.Application
import android.content.Context
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.data.TaskStore
import io.github.michaelmassoni.tessera.tasks.reminders.Reminders
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import io.github.michaelmassoni.tessera.tasks.widget.TasksWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

/**
 * Holds the one [TaskStore], and keeps everything that follows the tasks in step with them: the
 * reminder alarm, the widget, and a sync a few seconds after a change.
 */
class TasksApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store: TaskStore by lazy { TaskStore(File(filesDir, "tasks.json"), scope) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        store.data
            .drop(1)
            .debounce(CHANGE_DELAY_MS)
            .onEach { data ->
                Reminders.schedule(this)
                TasksWidget.refresh(this)
                val synced =
                    data.lists
                        .filter { it.synced }
                        .map { it.id }
                        .toSet()
                if (AccountStore(this).account != null && data.tasks.any { it.listId in synced && (it.dirty || it.deleted) }) {
                    SyncWorker.soon(this)
                }
            }.launchIn(scope)
    }

    private companion object {
        const val CHANGE_DELAY_MS = 500L
    }
}

val Context.tasks: TaskStore get() = (applicationContext as TasksApplication).store
