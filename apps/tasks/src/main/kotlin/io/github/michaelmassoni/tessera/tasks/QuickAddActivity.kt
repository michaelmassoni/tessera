package io.github.michaelmassoni.tessera.tasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import io.github.michaelmassoni.tessera.tasks.ui.AddTaskSheet
import io.github.michaelmassoni.tessera.tasks.ui.TasksActions
import java.util.UUID

/** The widget's +: the add sheet over the home screen, without opening the app. */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listId =
            intent.getStringExtra(EXTRA_LIST) ?: tasks.data.value.lists
                .minByOrNull { it.order }
                ?.id
        if (listId == null) {
            finish()
            return
        }
        setTesseraContent { AddTaskSheet(listId, starred = false, actions = Adds(), onDismiss = ::finish) }
    }

    /** Only adding is possible here. */
    private inner class Adds : TasksActions {
        override fun add(
            listId: String,
            title: String,
            notes: String,
            due: Due?,
            repeat: Repeat?,
            starred: Boolean,
            parentId: String?,
        ): String {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            tasks.update { d ->
                val order = TaskOps.newOrder(d.tasksOf(listId).filter { it.parentId == null })
                d.copy(
                    tasks =
                        d.tasks +
                            Task(
                                id,
                                listId,
                                title,
                                notes,
                                due,
                                repeat,
                                starred,
                                parentId,
                                order = order,
                                createdAt = now,
                                modifiedAt = now,
                            ),
                )
            }
            return id
        }

        override fun toggle(id: String) = Unit

        override fun save(task: Task) = Unit

        override fun delete(id: String) = Unit

        override fun undelete(id: String) = Unit

        override fun move(
            listId: String,
            id: String,
            to: Int,
        ) = Unit

        override fun newList(
            name: String,
            onServer: Boolean,
        ) = Unit

        override fun renameList(
            id: String,
            name: String,
        ) = Unit

        override fun deleteList(id: String) = Unit

        override fun deleteCompleted(listId: String) = Unit

        override fun refresh() = Unit

        override fun openSettings() = Unit
    }

    companion object {
        const val EXTRA_LIST = "list"
    }
}
