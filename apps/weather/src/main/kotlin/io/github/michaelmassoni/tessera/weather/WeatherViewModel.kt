package io.github.michaelmassoni.tessera.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.weather.data.ForecastCache
import io.github.michaelmassoni.tessera.weather.data.Locator
import io.github.michaelmassoni.tessera.weather.data.PlaceStore
import io.github.michaelmassoni.tessera.weather.data.WeatherApi
import io.github.michaelmassoni.tessera.weather.data.WeatherSettings
import io.github.michaelmassoni.tessera.weather.engine.Bom
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastCodec
import io.github.michaelmassoni.tessera.weather.engine.OpenMeteo
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.Timeline
import io.github.michaelmassoni.tessera.weather.engine.Units
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A page: a place and what we know of its weather. */
data class PlacePage(
    val place: Place,
    val forecast: Forecast? = null,
    val fetchedAt: Long = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

data class WeatherState(
    val loaded: Boolean = false,
    val pages: List<PlacePage> = emptyList(),
    val units: Units = Units(),
    /** Null until chosen on the welcome screen. */
    val useLocation: Boolean? = null,
    val locationAllowed: Boolean = false,
    val startPlace: Long = Place.HERE,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
)

sealed interface WeatherEvent {
    data object UpdateFailed : WeatherEvent

    data object LocationUnavailable : WeatherEvent
}

class WeatherViewModel(
    private val api: WeatherApi,
    private val places: PlaceStore,
    private val cache: ForecastCache,
    private val settings: WeatherSettings,
    private val locator: Locator,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<WeatherEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var search: Job? = null
    private var started = false

    /**
     * Starts once per ViewModel. The activity calls it on every create: one restored after the
     * process was killed has saved state but a fresh ViewModel, which would otherwise never load.
     */
    fun startOnce() {
        if (!started) start()
    }

    /** Builds the pages from what's stored, then refreshes whatever is stale. */
    fun start() {
        started = true
        viewModelScope.launch {
            val saved = withContext(io) { places.load() }
            val useLocation = settings.useLocation
            val allowed = locator.allowed()
            val here =
                if (useLocation == true &&
                    allowed
                ) {
                    listOf(settings.lastFix ?: Place(Place.HERE, "", latitude = 0.0, longitude = 0.0))
                } else {
                    emptyList()
                }
            val pages =
                withContext(io) {
                    (here + saved).map { place ->
                        val entry = cache.read(place.id)
                        PlacePage(
                            place = place,
                            forecast = entry?.let { runCatching { ForecastCodec.decode(it.body) }.getOrNull() },
                            fetchedAt = entry?.fetchedAt ?: 0,
                        )
                    }
                }
            _state.update {
                it.copy(
                    loaded = true,
                    pages = pages,
                    units = settings.units,
                    useLocation = useLocation,
                    locationAllowed = allowed,
                    startPlace = settings.lastPlace,
                )
            }
            refresh(force = false)
        }
    }

    /** Settings may have changed while away. */
    fun reloadSettings() {
        val useLocation = settings.useLocation
        val allowed = locator.allowed()
        val s = _state.value
        _state.update { it.copy(units = settings.units) }
        if (s.loaded && (useLocation != s.useLocation || allowed != s.locationAllowed)) start()
    }

    fun refresh(force: Boolean = true) {
        _state.value.pages.forEach { page ->
            if (force || page.forecast == null || Timeline.isStale(page.fetchedAt, clock())) fetch(page.place)
        }
    }

    private fun fetch(place: Place) {
        setPage(place.id) { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            var target = place
            if (place.id == Place.HERE) {
                val fix = locator.locate()
                if (fix == null) {
                    setPage(place.id) { it.copy(loading = false, failed = it.forecast == null) }
                    if (_state.value.pages
                            .firstOrNull { it.place.id == Place.HERE }
                            ?.forecast == null
                    ) {
                        _events.tryEmit(WeatherEvent.LocationUnavailable)
                    }
                    return@launch
                }
                val name = locator.name(fix).orEmpty()
                target = Place(Place.HERE, name, latitude = fix.latitude, longitude = fix.longitude)
                settings.lastFix = target
            }
            val result =
                runCatching {
                    // The Bureau for Australian places; Open-Meteo elsewhere, or if BOM fails.
                    val stored =
                        (if (Bom.covers(target.latitude, target.longitude)) runCatching { loadBom(target) }.getOrNull() else null)
                            ?: ForecastCodec.openMeteo(api.get(OpenMeteo.forecastUrl(target.latitude, target.longitude)))
                    val forecast = withContext(io) { ForecastCodec.decode(stored) }
                    val now = clock()
                    withContext(io) { cache.write(target.id, stored, now) }
                    forecast to now
                }
            result
                .onSuccess { (forecast, at) ->
                    setPage(place.id) { it.copy(place = target, forecast = forecast, fetchedAt = at, loading = false, failed = false) }
                }.onFailure {
                    setPage(place.id) { it.copy(place = target, loading = false, failed = true) }
                    _events.tryEmit(WeatherEvent.UpdateFailed)
                }
        }
    }

    /** BOM's location, daily and hourly forecasts and observations, fetched together and checked. */
    private suspend fun loadBom(place: Place): String =
        coroutineScope {
            val g = Bom.geohash(place.latitude, place.longitude)
            val location = async { api.get(Bom.locationUrl(g)) }
            val daily = async { api.get(Bom.dailyUrl(g)) }
            val hourly = async { api.get(Bom.hourlyUrl(g)) }
            val observations = async { api.get(Bom.observationsUrl(g)) }
            // Open-Meteo's hours for what BOM's three hourly days don't cover (the day details); optional.
            val openMeteo = async { runCatching { api.get(OpenMeteo.forecastUrl(place.latitude, place.longitude)) }.getOrNull() }
            val stored = ForecastCodec.bom(location.await(), daily.await(), hourly.await(), observations.await(), openMeteo.await())
            // A response that doesn't parse (BOM changed its API) falls back to Open-Meteo.
            withContext(io) { ForecastCodec.decode(stored) }.also { check(it.days.isNotEmpty() && it.hours.isNotEmpty()) }
            stored
        }

    private fun setPage(
        id: Long,
        change: (PlacePage) -> PlacePage,
    ) = _state.update { s -> s.copy(pages = s.pages.map { if (it.place.id == id) change(it) else it }) }

    fun pageShown(id: Long) {
        settings.lastPlace = id
    }

    /** Opens [id]'s page (from the places list). */
    fun show(id: Long) {
        settings.lastPlace = id
        _state.update { it.copy(startPlace = id) }
    }

    // ---- Places ----

    fun setUseLocation(on: Boolean) {
        settings.useLocation = on
        start()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query, searchFailed = false) }
        search?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        search =
            viewModelScope.launch {
                delay(300)
                _state.update { it.copy(searching = true) }
                val language =
                    java.util.Locale
                        .getDefault()
                        .language
                        .ifBlank { "en" }
                runCatching { OpenMeteo.parseSearch(api.get(OpenMeteo.searchUrl(query, language))) }
                    .onSuccess { results -> _state.update { it.copy(results = results, searching = false) } }
                    .onFailure { _state.update { it.copy(results = emptyList(), searching = false, searchFailed = true) } }
            }
    }

    fun add(place: Place) {
        val saved = savedPlaces()
        if (saved.none { it.id == place.id }) persist(saved + place)
        settings.lastPlace = place.id
        _state.update { it.copy(query = "", results = emptyList(), startPlace = place.id) }
        if (_state.value.pages.none { it.place.id == place.id }) {
            _state.update { it.copy(pages = it.pages + PlacePage(place)) }
            fetch(place)
        }
    }

    fun remove(place: Place) {
        persist(savedPlaces().filter { it.id != place.id })
        viewModelScope.launch(io) { cache.delete(place.id) }
        _state.update { s -> s.copy(pages = s.pages.filter { it.place.id != place.id }) }
    }

    fun move(
        place: Place,
        by: Int,
    ) {
        val saved = savedPlaces().toMutableList()
        val from = saved.indexOfFirst { it.id == place.id }
        val to = from + by
        if (from < 0 || to !in saved.indices) return
        saved.add(to, saved.removeAt(from))
        persist(saved)
        _state.update { s ->
            val here = s.pages.filter { it.place.id == Place.HERE }
            val byId = s.pages.associateBy { it.place.id }
            s.copy(pages = here + saved.mapNotNull { byId[it.id] })
        }
    }

    private fun savedPlaces() =
        _state.value.pages
            .map { it.place }
            .filter { it.id != Place.HERE }

    private fun persist(list: List<Place>) {
        viewModelScope.launch(io) { places.save(list) }
    }
}
