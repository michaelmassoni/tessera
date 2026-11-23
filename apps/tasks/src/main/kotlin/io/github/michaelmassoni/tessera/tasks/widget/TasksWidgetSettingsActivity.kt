package io.github.michaelmassoni.tessera.tasks.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.data.TasksPrefs
import io.github.michaelmassoni.tessera.tasks.engine.Sort
import io.github.michaelmassoni.tessera.tasks.engine.TaskList
import io.github.michaelmassoni.tessera.tasks.tasks
import io.github.michaelmassoni.tessera.tasks.ui.title
import kotlinx.coroutines.launch

/**
 * A Tasks widget's settings: the list, the order (due date first by default), what each row shows,
 * and the background and theme. Opens when a widget is placed and from the launcher's widget
 * settings (it's reconfigurable); leaving without Done keeps what it had.
 */
class TasksWidgetSettingsActivity : ComponentActivity() {
    private var settings by mutableStateOf(TasksWidgetSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId =
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // Placed even if left with Back: the defaults are fine.
        setResult(RESULT_OK, result)
        val glanceId = runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(widgetId) }.getOrNull()
        lifecycleScope.launch {
            glanceId?.let {
                settings =
                    TasksWidgetSettings.from(getAppWidgetState(this@TasksWidgetSettingsActivity, PreferencesGlanceStateDefinition, it))
            }
        }
        val lists =
            tasks.data.value.lists
                .sortedBy { it.order }
        val stars = TasksPrefs.stars(this)
        setTesseraContent {
            Screen(lists, stars, settings, { settings = it }, onDone = {
                lifecycleScope.launch {
                    glanceId?.let { id ->
                        updateAppWidgetState(this@TasksWidgetSettingsActivity, id) { settings.write(it) }
                        TasksWidget().update(this@TasksWidgetSettingsActivity, id)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                }
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    lists: List<TaskList>,
    stars: Boolean,
    settings: TasksWidgetSettings,
    onChange: (TasksWidgetSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings)) },
                actions = { TextButton(onClick = onDone) { Text(stringResource(R.string.done)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (lists.size > 1) {
                SettingsHeader(stringResource(R.string.widget_list))
                Column(Modifier.selectableGroup()) {
                    lists.forEachIndexed { i, l ->
                        // No list chosen (or it was deleted): the first, as the widget shows.
                        val selected = settings.list == l.id || (i == 0 && lists.none { it.id == settings.list })
                        RadioSetting(l.title(), selected = selected, onSelect = { onChange(settings.copy(list = l.id)) })
                    }
                }
            }
            SettingsHeader(stringResource(R.string.widget_sort))
            Column(Modifier.selectableGroup()) {
                Sort.entries.filter { stars || it != Sort.Starred }.forEach { s ->
                    RadioSetting(
                        stringResource(
                            when (s) {
                                Sort.Date -> R.string.sort_date
                                Sort.MyOrder -> R.string.sort_my_order
                                Sort.Starred -> R.string.sort_starred
                                Sort.Title -> R.string.sort_title
                            },
                        ),
                        selected = settings.sort == s,
                        onSelect = { onChange(settings.copy(sort = s)) },
                    )
                }
            }
            SettingsHeader(stringResource(R.string.widget_show))
            SwitchSetting(stringResource(R.string.widget_show_name), settings.showName, { onChange(settings.copy(showName = it)) })
            SwitchSetting(stringResource(R.string.widget_show_dates), settings.showDates, { onChange(settings.copy(showDates = it)) })
            SettingsHeader(stringResource(R.string.widget_background))
            Column(Modifier.selectableGroup()) {
                WidgetBackground.entries.forEach { b ->
                    RadioSetting(
                        stringResource(
                            when (b) {
                                WidgetBackground.Tinted -> R.string.widget_background_tinted
                                WidgetBackground.Solid -> R.string.widget_background_solid
                                WidgetBackground.Transparent -> R.string.widget_background_none
                            },
                        ),
                        selected = settings.background == b,
                        onSelect = { onChange(settings.copy(background = b)) },
                    )
                }
            }
            if (settings.background != WidgetBackground.Transparent) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    val label = stringResource(R.string.widget_opacity, settings.opacity)
                    Text(label)
                    val state = remember { SliderState(settings.opacity.toFloat(), trackRange = 0f..100f) }
                    state.value = settings.opacity.toFloat()
                    Slider(
                        state = state,
                        onValueChange = { onChange(settings.copy(opacity = (it / 5).toInt() * 5)) },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            SettingsHeader(stringResource(R.string.widget_theme))
            Column(Modifier.selectableGroup().padding(bottom = 24.dp)) {
                WidgetTheme.entries.forEach { t ->
                    RadioSetting(
                        stringResource(
                            when (t) {
                                WidgetTheme.App -> R.string.widget_theme_app
                                WidgetTheme.Light -> R.string.widget_theme_light
                                WidgetTheme.Dark -> R.string.widget_theme_dark
                            },
                        ),
                        selected = settings.theme == t,
                        onSelect = { onChange(settings.copy(theme = t)) },
                    )
                }
            }
        }
    }
}
