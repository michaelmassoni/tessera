package io.github.michaelmassoni.tessera.tasks.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.michaelmassoni.tessera.tasks.MainActivity
import io.github.michaelmassoni.tessera.tasks.QuickAddActivity
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.data.TaskData
import io.github.michaelmassoni.tessera.tasks.data.TaskStore
import io.github.michaelmassoni.tessera.tasks.engine.Sort
import io.github.michaelmassoni.tessera.tasks.engine.TaskOps
import io.github.michaelmassoni.tessera.tasks.tasks
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * A list's open tasks on the home screen: tick one off, tap one to open it, + to add one without
 * opening the app, and the list's name to show another list.
 */
class TasksWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val store = context.tasks
        provideContent {
            val data by store.data.collectAsState()
            val settings = TasksWidgetSettings.from(currentState<Preferences>())
            val (scheme, background) = settings.colors(context)
            GlanceTheme(colors = scheme) { Content(context, data, settings, background) }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        data: TaskData,
        settings: TasksWidgetSettings,
        background: ColorProvider,
    ) {
        val list = data.list(settings.list.orEmpty()) ?: data.lists.minByOrNull { it.order }
        val rows = list?.let { TaskOps.view(data.tasksOf(it.id), settings.sort).open.take(MAX_ROWS) }.orEmpty()
        // With one list there's nothing to switch to.
        val switchable = data.lists.size > 1
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(background)
                .padding(12.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val name =
                    list
                        ?.let {
                            if (it.name == TaskStore.DEFAULT_NAME && !it.synced) context.getString(R.string.my_tasks) else it.name
                        }.orEmpty()
                if (settings.showName) {
                    // "▾" and tap-to-switch only when there's another list to switch to.
                    Text(
                        if (switchable) "$name ▾" else name,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier =
                            GlanceModifier
                                .defaultWeight()
                                .padding(4.dp)
                                .let { if (switchable) it.clickable(actionRunCallback<NextList>()) else it },
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                Image(
                    ImageProvider(DesignR.drawable.ic_add),
                    contentDescription = context.getString(R.string.new_task),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier =
                        GlanceModifier
                            .size(40.dp)
                            .padding(8.dp)
                            .clickable(
                                actionStartActivity(
                                    Intent(context, QuickAddActivity::class.java)
                                        .putExtra(QuickAddActivity.EXTRA_LIST, list?.id)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                                ),
                            ),
                )
            }
            if (rows.isEmpty()) {
                Text(
                    context.getString(R.string.all_done),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(8.dp),
                )
            } else {
                LazyColumn {
                    items(rows, itemId = {
                        it.task.id
                            .hashCode()
                            .toLong()
                    }) { row ->
                        val t = row.task
                        val late = TaskOps.overdue(t, now)
                        Row(
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(start = (row.depth * 24).dp)
                                .clickable(
                                    actionStartActivity(
                                        Intent(context, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_TASK, t.id)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                    ),
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckBox(
                                checked = false,
                                onCheckedChange = actionRunCallback<ToggleTask>(actionParametersOf(TASK to t.id)),
                                modifier = GlanceModifier.semantics { contentDescription = context.getString(R.string.mark_done) },
                            )
                            Column(GlanceModifier.defaultWeight()) {
                                Text(
                                    t.title.ifBlank { context.getString(R.string.untitled) },
                                    style = TextStyle(color = GlanceTheme.colors.onSurface),
                                    maxLines = 2,
                                )
                                t.due?.takeIf { settings.showDates }?.let { due ->
                                    val day =
                                        when (due.localDate) {
                                            today -> context.getString(R.string.today)
                                            today.plusDays(1) -> context.getString(R.string.tomorrow)
                                            else -> due.localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                        }
                                    val time = due.localTime?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                                    Text(
                                        listOfNotNull(day, time).joinToString(", "),
                                        style =
                                            TextStyle(
                                                color = if (late) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                                fontSize = 12.sp,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(GlanceModifier.size(0.dp))
        }
    }

    companion object {
        val LIST = TasksWidgetSettings.LIST
        val TASK = ActionParameters.Key<String>("task")

        // Glance shows at most 500 views; a row is a few.
        private const val MAX_ROWS = 60

        suspend fun refresh(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(TasksWidget::class.java).forEach { TasksWidget().update(context, it) }
        }
    }
}

/** Ticks a task from the widget (a repeating one moves to its next date). */
class ToggleTask : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TasksWidget.TASK] ?: return
        val now = System.currentTimeMillis()
        context.tasks.update { d ->
            d.copy(
                tasks =
                    d.tasks.map {
                        if (it.id ==
                            id
                        ) {
                            TaskOps.complete(it, now, ZoneId.systemDefault())
                        } else {
                            it
                        }
                    },
            )
        }
        TasksWidget().update(context, glanceId)
    }
}

/** Shows the next list in the widget. */
class NextList : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val lists =
            context.tasks.data.value.lists
                .sortedBy { it.order }
        if (lists.isEmpty()) return
        updateAppWidgetState(context, glanceId) { prefs ->
            val at = lists.indexOfFirst { it.id == prefs[TasksWidget.LIST] }
            prefs[TasksWidget.LIST] = lists[(at + 1) % lists.size].id
        }
        TasksWidget().update(context, glanceId)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
