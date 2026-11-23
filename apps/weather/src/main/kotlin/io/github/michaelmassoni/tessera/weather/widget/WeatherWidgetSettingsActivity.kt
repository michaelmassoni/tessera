package io.github.michaelmassoni.tessera.weather.widget

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
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.PlaceStore
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Place
import kotlinx.coroutines.launch
import java.io.File

/**
 * A weather widget's settings (either kind): its place, what it shows, and its background and
 * theme. Opens when a widget is placed and from the launcher's widget settings; leaving without
 * Done keeps what it had.
 */
class WeatherWidgetSettingsActivity : ComponentActivity() {
    private var settings by mutableStateOf(WeatherWidgetSettings())

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
        val date =
            AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(widgetId)
                ?.provider
                ?.className == DateWeatherWidgetReceiver::class.java.name
        val defaults = if (date) WeatherWidgetSettings.DATE_DEFAULTS else WeatherWidgetSettings()
        settings = defaults
        val glanceId = runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(widgetId) }.getOrNull()
        lifecycleScope.launch {
            glanceId?.let {
                settings =
                    WeatherWidgetSettings.from(
                        getAppWidgetState(this@WeatherWidgetSettingsActivity, PreferencesGlanceStateDefinition, it),
                        defaults,
                    )
            }
        }
        val prefs = weatherSettings()
        val here = if (prefs.useLocation == true) listOf(Place.HERE to getString(R.string.current_location)) else emptyList()
        val places = here + PlaceStore(File(filesDir, "places.json")).load().map { it.id to it.description }
        val main = WeatherJobs.primaryPlace(this)?.let { it.name.ifBlank { getString(R.string.current_location) } }
        setTesseraContent {
            Screen(date, main, places, settings, { settings = it }, onDone = {
                lifecycleScope.launch {
                    glanceId?.let { id ->
                        updateAppWidgetState(this@WeatherWidgetSettingsActivity, id) { settings.write(it) }
                        WeatherJobs.schedule(applicationContext)
                        WeatherWidgets.refresh(this@WeatherWidgetSettingsActivity)
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
    date: Boolean,
    main: String?,
    places: List<Pair<Long, String>>,
    settings: WeatherWidgetSettings,
    onChange: (WeatherWidgetSettings) -> Unit,
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
            SettingsHeader(stringResource(R.string.widget_place))
            Column(Modifier.selectableGroup()) {
                RadioSetting(
                    main?.let { stringResource(R.string.widget_place_main_named, it) } ?: stringResource(R.string.widget_place_main),
                    selected = settings.place == null || places.none { it.first == settings.place },
                    onSelect = { onChange(settings.copy(place = null)) },
                )
                places.forEach { (id, name) ->
                    RadioSetting(name, selected = settings.place == id, onSelect = { onChange(settings.copy(place = id)) })
                }
            }
            SettingsHeader(stringResource(R.string.widget_show))
            if (date) {
                SwitchSetting(
                    stringResource(R.string.widget_show_condition),
                    settings.showCondition,
                    { onChange(settings.copy(showCondition = it)) },
                )
            }
            SwitchSetting(
                stringResource(R.string.widget_show_high_low),
                settings.showHighLow,
                { onChange(settings.copy(showHighLow = it)) },
            )
            SwitchSetting(stringResource(R.string.widget_show_name), settings.showName, { onChange(settings.copy(showName = it)) })
            if (!date) {
                SwitchSetting(
                    stringResource(R.string.widget_show_hours),
                    settings.showHours,
                    { onChange(settings.copy(showHours = it)) },
                    summary = stringResource(R.string.widget_show_hours_summary),
                )
                SwitchSetting(
                    stringResource(R.string.widget_show_days),
                    settings.showDays,
                    { onChange(settings.copy(showDays = it)) },
                    summary = stringResource(R.string.widget_show_days_summary),
                )
            }
            if (date) {
                SettingsHeader(stringResource(R.string.widget_date))
                Column(Modifier.selectableGroup()) {
                    DateStyle.entries.forEach { s ->
                        RadioSetting(
                            stringResource(if (s == DateStyle.Long) R.string.widget_date_long else R.string.widget_date_short),
                            selected = settings.dateStyle == s,
                            onSelect = { onChange(settings.copy(dateStyle = s)) },
                        )
                    }
                }
            }
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
