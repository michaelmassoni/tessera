package io.github.michaelmassoni.tessera.calendar.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.data.CalendarRepository
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.ui.calendarName
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * The month widget's settings: shown when the widget is added, and from its long-press menu
 * (Settings). A live preview of the widget, then its appearance and which calendars it shows.
 */
class MonthWidgetSettingsActivity : ComponentActivity() {
    private var glanceId: GlanceId? = null
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId =
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of a new widget's settings cancels adding it, as the platform expects.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            val id = runCatching { GlanceAppWidgetManager(this@MonthWidgetSettingsActivity).getGlanceIdBy(appWidgetId) }.getOrNull()
            if (id == null) {
                finish()
                return@launch
            }
            glanceId = id
            val initial =
                MonthWidgetSettings.from(
                    getAppWidgetState(this@MonthWidgetSettingsActivity, PreferencesGlanceStateDefinition, id),
                )
            val calendars =
                withContext(Dispatchers.IO) {
                    if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                        runCatching { CalendarRepository(contentResolver).calendars().filter { it.visible } }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
            setTesseraContent {
                WidgetSettingsScreen(
                    initial = initial,
                    calendars = calendars,
                    preview = { settings -> preview(id, settings) },
                    onClose = ::finish,
                    onSave = ::save,
                )
            }
        }
    }

    private suspend fun preview(
        id: GlanceId,
        settings: MonthWidgetSettings,
    ): RemoteViews? {
        val state = mutablePreferencesOf().also { settings.copy(offset = 0).write(it) }
        return runCatching { MonthWidget().compose(this, id, size = PreviewSize, state = state) }.getOrNull()
    }

    private fun save(settings: MonthWidgetSettings) {
        val id = glanceId ?: return
        lifecycleScope.launch {
            updateAppWidgetState(this@MonthWidgetSettingsActivity, id) { prefs ->
                // Keep the month the widget is on; bump the stamp so it reloads with the new calendars.
                settings.copy(offset = prefs[MonthWidgetSettings.OFFSET] ?: 0, stamp = System.currentTimeMillis()).write(prefs)
            }
            MonthWidget().update(this@MonthWidgetSettingsActivity, id)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    private companion object {
        val PreviewSize = DpSize(320.dp, 360.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsScreen(
    initial: MonthWidgetSettings,
    calendars: List<CalendarInfo>,
    preview: suspend (MonthWidgetSettings) -> RemoteViews?,
    onClose: () -> Unit,
    onSave: (MonthWidgetSettings) -> Unit,
) {
    var settings by remember { mutableStateOf(initial) }
    var remote by remember { mutableStateOf<RemoteViews?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(settings) { remote = preview(settings) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_month_name)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    Button(
                        onClick = { onSave(settings) },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "preview") {
                // The real widget, drawn with these settings, on a stand-in for the home screen.
                Box(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { FrameLayout(it) },
                        update = { frame ->
                            frame.removeAllViews()
                            remote?.let { views -> runCatching { frame.addView(views.apply(context, frame)) } }
                        },
                        modifier = Modifier.size(320.dp, 360.dp),
                    )
                }
            }
            item(key = "appearance") {
                Section(stringResource(R.string.widget_appearance)) {
                    Label(stringResource(R.string.widget_background))
                    Choice(
                        options = WidgetBackground.entries,
                        selected = settings.background,
                        label = {
                            when (it) {
                                WidgetBackground.Solid -> R.string.widget_background_solid
                                WidgetBackground.Tinted -> R.string.widget_background_tinted
                                WidgetBackground.Transparent -> R.string.widget_background_transparent
                            }
                        },
                        onSelect = { settings = settings.copy(background = it) },
                    )
                    if (settings.background != WidgetBackground.Transparent) {
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Label(stringResource(R.string.widget_opacity), Modifier.weight(1f))
                            Text(
                                stringResource(R.string.widget_opacity_value, settings.opacity),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val opacity = remember { SliderState(settings.opacity.toFloat(), 9, 0f..100f) }
                        Slider(
                            state = opacity,
                            onValueChange = {
                                opacity.value = it
                                settings = settings.copy(opacity = (it / 10).roundToInt() * 10)
                            },
                        )
                    }
                    Label(stringResource(R.string.widget_theme), Modifier.padding(top = 12.dp))
                    Choice(
                        options = WidgetTheme.entries,
                        selected = settings.theme,
                        label = {
                            when (it) {
                                WidgetTheme.App -> R.string.widget_theme_app
                                WidgetTheme.Light -> R.string.widget_theme_light
                                WidgetTheme.Dark -> R.string.widget_theme_dark
                            }
                        },
                        onSelect = { settings = settings.copy(theme = it) },
                    )
                }
            }
            if (calendars.isNotEmpty()) {
                item(key = "calendars-title") {
                    Text(
                        stringResource(R.string.widget_calendars),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp)
                                .semantics { heading() },
                    )
                }
                items(calendars, key = { it.id }) { calendar ->
                    val shown = calendar.id !in settings.hidden
                    Surface(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            Modifier
                                .toggleable(value = shown, role = Role.Checkbox, onValueChange = {
                                    settings =
                                        settings.copy(hidden = if (it) settings.hidden - calendar.id else settings.hidden + calendar.id)
                                })
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(calendar.color).copy(alpha = 1f)))
                            Text(
                                calendarName(calendar, resources),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                            Checkbox(checked = shown, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        )
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
        }
    }
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
private fun <E> Choice(
    options: List<E>,
    selected: E,
    label: (E) -> Int,
    onSelect: (E) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label(option)), maxLines = 1) }
        }
    }
}
