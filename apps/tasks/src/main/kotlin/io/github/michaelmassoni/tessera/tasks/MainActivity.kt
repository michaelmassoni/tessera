package io.github.michaelmassoni.tessera.tasks

import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.data.Accounts
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.data.TasksPrefs
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import io.github.michaelmassoni.tessera.tasks.engine.Task
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import io.github.michaelmassoni.tessera.tasks.reminders.Reminders
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import io.github.michaelmassoni.tessera.tasks.ui.SyncNotice
import io.github.michaelmassoni.tessera.tasks.ui.TasksActions
import io.github.michaelmassoni.tessera.tasks.ui.TasksApp
import io.github.michaelmassoni.tessera.tasks.ui.TasksState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var openTask by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<SyncNotice?>(null)
    private var exactAlarms by mutableStateOf(true)
    private var stars by mutableStateOf(true)
    private val accounts by lazy { AccountStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openTask = intent.getStringExtra(EXTRA_TASK)
        val actions = Actions()
        val syncing = WorkManager.getInstance(this).getWorkInfosForUniqueWorkFlow("sync-soon")
        setTesseraContent {
            SetupGate(tasksSetup) {
                val data by tasks.data.collectAsStateWithLifecycle()
                val work by syncing.collectAsStateWithLifecycle(emptyList())
                val running =
                    work.any {
                        it.state == WorkInfo.State.RUNNING || (it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0)
                    }
                val alarmsLate = !exactAlarms && data.tasks.any { !it.completed && !it.deleted && it.due?.time != null }
                TasksApp(TasksState(data, accounts.account != null, running, notice, alarmsLate, stars), actions, openTask = openTask)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TASK)?.let { openTask = it }
    }

    override fun onResume() {
        super.onResume()
        stars = TasksPrefs.stars(this)
        exactAlarms = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        // Opening the app brings the server's changes.
        if (accounts.account != null) SyncWorker.schedule.soon(this, delaySeconds = 0)
        Reminders.schedule(this)
    }

    override fun onStop() {
        // Tasks deleted from phone-only lists are kept a while for Undo; now they go.
        tasks.update { d ->
            val local =
                d.lists
                    .filterNot { it.synced }
                    .map { it.id }
                    .toSet()
            d.copy(tasks = d.tasks.filterNot { it.deleted && (it.listId in local || it.href == null) })
        }
        super.onStop()
    }

    private fun now() = System.currentTimeMillis()

    private fun change(block: (TaskData) -> TaskData) = tasks.update(block)

    private fun TaskData.replace(
        ids: Set<String>,
        change: (Task) -> Task,
    ) = copy(tasks = tasks.map { if (it.id in ids) change(it) else it })

    private inner class Actions : TasksActions {
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
            change { d ->
                val siblings = d.tasksOf(listId).filter { it.parentId == parentId && !it.completed }
                // New tasks go at the top, new subtasks at the bottom of their parent's.
                val order = if (parentId == null) TaskOps.newOrder(siblings) else (siblings.maxOfOrNull { it.order } ?: 0) + 1
                val task =
                    Task(id, listId, title, notes, due, repeat, starred, parentId, order = order, createdAt = now(), modifiedAt = now())
                d.copy(tasks = d.tasks + task)
            }
            return id
        }

        override fun toggle(id: String) {
            val zone = ZoneId.systemDefault()
            change { d ->
                val task = d.task(id) ?: return@change d
                if (task.completed) {
                    d.replace(setOf(id)) { TaskOps.reopen(it, now()) }
                } else {
                    val done = TaskOps.complete(task, now(), zone)
                    // Finishing a task finishes its subtasks (not when a repeat just moves it on).
                    val subtasks =
                        if (done.completed) {
                            d.tasks
                                .filter { it.parentId == id && !it.completed }
                                .map { it.id }
                                .toSet()
                        } else {
                            emptySet()
                        }
                    d.replace(setOf(id)) { done }.replace(subtasks) { it.copy(completedAt = now(), modifiedAt = now(), dirty = true) }
                }
            }
            Reminders.cancel(this@MainActivity, id)
        }

        override fun save(task: Task) = change { d -> d.replace(setOf(task.id)) { task } }

        override fun delete(id: String) =
            change { d ->
                val task = d.task(id) ?: return@change d
                d.replace(TaskOps.withSubtasks(task, d.tasks).map { it.id }.toSet()) { it.copy(deleted = true) }
            }

        override fun undelete(id: String) =
            change { d ->
                val task = d.task(id) ?: return@change d
                d.replace(TaskOps.withSubtasks(task, d.tasks).map { it.id }.toSet()) { it.copy(deleted = false) }
            }

        override fun move(
            listId: String,
            id: String,
            to: Int,
        ) = change { d ->
            val top = d.tasksOf(listId).filter { !it.completed && (it.parentId == null || d.task(it.parentId)?.completed != false) }
            val moved = TaskOps.move(top, id, to, now()).associateBy { it.id }
            d.copy(tasks = d.tasks.map { moved[it.id] ?: it })
        }

        override fun newList(
            name: String,
            onServer: Boolean,
        ) {
            if (!onServer) {
                change { d -> d.copy(lists = d.lists + TaskList(UUID.randomUUID().toString(), name, order = d.lists.size)) }
                return
            }
            lifecycleScope.launch {
                val list = Accounts.createRemoteList(this@MainActivity, name)
                if (list == null) {
                    notice = SyncNotice.Failed
                } else {
                    change { d -> d.copy(lists = d.lists + list.copy(order = d.lists.size)) }
                }
            }
        }

        override fun renameList(
            id: String,
            name: String,
        ) {
            val list = tasks.data.value.list(id) ?: return
            change { d -> d.copy(lists = d.lists.map { if (it.id == id) it.copy(name = name) else it }) }
            val href = list.href ?: return
            onServer { client -> client.rename(href, name) }
        }

        override fun deleteList(id: String) {
            val list = tasks.data.value.list(id) ?: return
            change { d ->
                val rest = d.lists.filterNot { it.id == id }
                // There's always a list to add to.
                val lists =
                    rest.ifEmpty {
                        listOf(TaskList(UUID.randomUUID().toString(), io.github.michaelmassoni.tessera.tasks.data.TaskStore.DEFAULT_NAME))
                    }
                d.copy(lists = lists, tasks = d.tasks.filterNot { it.listId == id })
            }
            val href = list.href ?: return
            onServer { client -> client.deleteCollection(href) }
        }

        override fun deleteCompleted(listId: String) =
            change { d -> d.copy(tasks = d.tasks.map { if (it.listId == listId && it.completed) it.copy(deleted = true) else it }) }

        override fun refresh() {
            notice = null
            SyncWorker.schedule.soon(this@MainActivity, delaySeconds = 0)
            lifecycleScope.launch {
                // Say so if this sync fails.
                kotlinx.coroutines.delay(SYNC_WAIT_MS)
                notice =
                    when (accounts.lastProblem) {
                        null -> null
                        SyncProblem.WrongLogin.name -> SyncNotice.SignIn
                        else -> SyncNotice.Failed
                    }
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun allowAlarms() = startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
    }

    /** A list change on the server, off the main thread; failures say so. */
    private fun onServer(block: (DavClient) -> Unit) {
        val account = accounts.account ?: return
        lifecycleScope.launch {
            val ok =
                withContext(Dispatchers.IO) {
                    val password = accounts.password() ?: return@withContext false
                    runCatching { block(DavClient(account.username, password)) }.isSuccess
                }
            if (!ok) notice = SyncNotice.Failed
        }
    }

    companion object {
        const val EXTRA_TASK = "task"
        private const val SYNC_WAIT_MS = 6_000L
    }
}
