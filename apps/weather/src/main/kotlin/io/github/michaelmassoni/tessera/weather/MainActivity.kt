package io.github.michaelmassoni.tessera.weather

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.ForecastCache
import io.github.michaelmassoni.tessera.weather.data.Locator
import io.github.michaelmassoni.tessera.weather.data.PlaceStore
import io.github.michaelmassoni.tessera.weather.data.WeatherApi
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.Warning
import io.github.michaelmassoni.tessera.weather.ui.WeatherActions
import io.github.michaelmassoni.tessera.weather.ui.WeatherApp
import io.github.michaelmassoni.tessera.weather.widget.WeatherWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by viewModels {
        viewModelFactory {
            initializer {
                WeatherViewModel(
                    api = WeatherApi(),
                    places = PlaceStore(File(filesDir, "places.json")),
                    cache = ForecastCache(File(filesDir, "forecasts")),
                    settings = weatherSettings(),
                    locator = Locator(applicationContext),
                    io = Dispatchers.IO,
                    // The widget shows what the app just fetched.
                    onForecast = { lifecycleScope.launch { WeatherWidget.refresh(applicationContext) } },
                )
            }
        }
    }

    private val snackbar = SnackbarHostState()

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setUseLocation(granted)
            if (!granted) {
                lifecycleScope.launch {
                    val result =
                        snackbar.showSnackbar(
                            getString(R.string.location_denied),
                            actionLabel = getString(R.string.open_settings),
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.startOnce()
        // Keeps the background check in line with the settings and widgets (it does nothing when nothing needs it).
        lifecycleScope.launch { WeatherJobs.schedule(applicationContext) }
        val actions = Actions()
        setTesseraContent {
            SetupGate(weatherSetup) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // "Now" moves on while the app is open, for the hours row and "Updated n min ago".
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000)
                        now = System.currentTimeMillis()
                    }
                }
                val failed = stringResource(R.string.offline)
                val retry = stringResource(R.string.retry)
                val noLocation = stringResource(R.string.location_unavailable)
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            WeatherEvent.UpdateFailed -> {
                                if (snackbar.showSnackbar(failed, actionLabel = retry) ==
                                    SnackbarResult.ActionPerformed
                                ) {
                                    viewModel.refresh()
                                }
                            }

                            WeatherEvent.LocationUnavailable -> {
                                snackbar.showSnackbar(noLocation)
                            }
                        }
                    }
                }
                WeatherApp(state, actions, now, snackbar)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadSettings()
        // Back after a while: refresh what's gone stale.
        if (viewModel.state.value.loaded) viewModel.refresh(force = false)
    }

    private inner class Actions : WeatherActions {
        override fun refresh() = viewModel.refresh()

        override fun pageShown(id: Long) = viewModel.pageShown(id)

        override fun show(id: Long) = viewModel.show(id)

        override fun search(query: String) = viewModel.search(query)

        override fun add(place: Place) = viewModel.add(place)

        override fun remove(place: Place) = viewModel.remove(place)

        override fun move(
            place: Place,
            by: Int,
        ) = viewModel.move(place, by)

        override fun setUseLocation(on: Boolean) {
            if (on && !Locator(applicationContext).allowed()) {
                askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                viewModel.setUseLocation(on)
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun openWarning(warning: Warning) = viewModel.openWarning(warning)

        override fun closeWarning() = viewModel.closeWarning()
    }
}
