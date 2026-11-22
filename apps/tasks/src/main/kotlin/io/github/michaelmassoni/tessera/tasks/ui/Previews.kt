package io.github.michaelmassoni.tessera.tasks.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import java.time.LocalDate

// Only today, tomorrow and yesterday: they read the same whatever day the screenshots are taken.
private val today = LocalDate.now()

private val sample =
    TaskData(
        lists = listOf(TaskList("meds", "Medications", order = 0), TaskList("home", "Home", order = 1)),
        tasks =
            listOf(
                Task(
                    "1",
                    "meds",
                    "Morning tablets",
                    due = Due.of(today, java.time.LocalTime.of(8, 0)),
                    repeat = Repeat(Repeat.Frequency.Daily),
                    starred = true,
                    order = 1,
                ),
                Task(
                    "2",
                    "meds",
                    "Evening tablets",
                    "One with dinner",
                    due = Due.of(today.plusDays(1), java.time.LocalTime.of(19, 0)),
                    repeat = Repeat(Repeat.Frequency.Daily),
                    order = 2,
                ),
                Task("3", "meds", "Refill prescription", due = Due.of(today.minusDays(1)), order = 3),
                Task("4", "meds", "Call the pharmacy", parentId = "3", order = 1),
                Task("5", "meds", "Book a blood test", order = 4),
                Task("6", "meds", "Vitamin D", completedAt = 1, order = 5),
                Task("7", "home", "Pay rent", due = Due.of(today.plusDays(1)), repeat = Repeat(Repeat.Frequency.Monthly), starred = true),
            ),
    )

private object NoActions : TasksActions {
    override fun add(
        listId: String,
        title: String,
        notes: String,
        due: Due?,
        repeat: Repeat?,
        starred: Boolean,
        parentId: String?,
    ) = ""

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

@TesseraPreviews
@Composable
private fun ListPreview() {
    TesseraTheme(dynamicColor = false) { TasksApp(TasksState(sample, signedIn = true), NoActions) }
}

@TesseraPreviews
@Composable
private fun EditorPreview() {
    TesseraTheme(dynamicColor = false) { TasksApp(TasksState(sample), NoActions, openTask = "3") }
}
