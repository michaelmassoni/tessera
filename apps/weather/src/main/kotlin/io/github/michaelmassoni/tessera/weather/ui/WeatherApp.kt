package io.github.michaelmassoni.tessera.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraSearchTopBar
import io.github.michaelmassoni.tessera.weather.PlacePage
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.WeatherState
import io.github.michaelmassoni.tessera.weather.engine.Metric
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.Warning
import java.time.LocalDate
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface WeatherActions {
    fun refresh()

    fun pageShown(id: Long)

    fun show(id: Long)

    fun search(query: String)

    fun add(place: Place)

    fun remove(place: Place)

    fun move(
        place: Place,
        by: Int,
    )

    /** Turning it on asks for the permission first. */
    fun setUseLocation(on: Boolean)

    fun openWarning(warning: Warning)

    fun closeWarning()

    fun openSettings()
}

@Composable
fun WeatherApp(
    state: WeatherState,
    actions: WeatherActions,
    now: Long,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    // The forecast leaves composition while the places list is shown: this keeps its scroll.
    val saveable = rememberSaveableStateHolder()
    when {
        !state.loaded -> {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }
        }

        state.pages.isEmpty() || showPlaces -> {
            saveable.SaveableStateProvider(PLACES_KEY) {
                PlacesScreen(
                    state = state,
                    actions = actions,
                    snackbar = snackbar,
                    onBack =
                        if (state.pages.isNotEmpty()) {
                            {
                                saveable.removeState(PLACES_KEY)
                                showPlaces = false
                            }
                        } else {
                            null
                        },
                    onOpen = { id ->
                        actions.show(id)
                        saveable.removeState(PLACES_KEY)
                        showPlaces = false
                    },
                    modifier = modifier,
                )
            }
        }

        else -> {
            saveable.SaveableStateProvider(FORECAST_KEY) {
                ForecastPager(state, actions, now, snackbar, onPlaces = { showPlaces = true }, modifier = modifier)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastPager(
    state: WeatherState,
    actions: WeatherActions,
    now: Long,
    snackbar: SnackbarHostState,
    onPlaces: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = state.pages
    val start = pages.indexOfFirst { it.place.id == state.startPlace }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { pages.size }
    val shown by rememberUpdatedState(pages)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i -> shown.getOrNull(i)?.let { actions.pageShown(it.place.id) } }
    }
    // A place picked in the list, or just added.
    LaunchedEffect(state.startPlace, pages.size) {
        val i = pages.indexOfFirst { it.place.id == state.startPlace }
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    val current = pages.getOrNull(pager.currentPage) ?: pages.first()
    // The day details open over the pager, for the page they were opened from.
    var detailDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailMetric by rememberSaveable { mutableStateOf(Metric.Temperature.name) }
    val detailForecast = current.forecast
    val day = detailDay
    // The pager leaves composition while a day's details are shown: this keeps each page's scroll.
    val saveable = rememberSaveableStateHolder()
    if (day != null && detailForecast != null) {
        saveable.SaveableStateProvider(DAY_KEY) {
            DayDetailScreen(
                forecast = detailForecast,
                placeName = placeName(current),
                units = state.units,
                now = now,
                date = LocalDate.ofEpochDay(day),
                metric = Metric.valueOf(detailMetric),
                onSelect = { date, metric ->
                    detailDay = date.toEpochDay()
                    detailMetric = metric.name
                },
                onBack = {
                    saveable.removeState(DAY_KEY)
                    detailDay = null
                },
                modifier = modifier,
            )
        }
        return
    }
    saveable.SaveableStateProvider(PAGER_KEY) {
        ForecastScaffold(
            state,
            actions,
            now,
            snackbar,
            onPlaces,
            pager,
            current,
            onDetail = { date, metric ->
                detailDay = date.toEpochDay()
                detailMetric = metric.name
            },
            modifier = modifier,
        )
    }
    state.openWarning?.let { WarningSheet(it, state.warningText, state.warningFailed, actions::closeWarning) }
}

private const val PLACES_KEY = "places"
private const val FORECAST_KEY = "forecast"
private const val PAGER_KEY = "pager"
private const val DAY_KEY = "day"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastScaffold(
    state: WeatherState,
    actions: WeatherActions,
    now: Long,
    snackbar: SnackbarHostState,
    onPlaces: () -> Unit,
    pager: PagerState,
    current: PlacePage,
    onDetail: (LocalDate, Metric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = state.pages
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (current.place.id == Place.HERE) {
                                Icon(painterResource(R.drawable.ic_navigation), contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text(placeName(current), maxLines = 1, modifier = Modifier.padding(start = 4.dp))
                        }
                        if (pages.size > 1) PageDots(pages.size, pager.currentPage)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onPlaces) {
                        Icon(painterResource(R.drawable.ic_list), contentDescription = stringResource(R.string.places))
                    }
                },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding), key = { pages[it].place.id }) { i ->
            val page = pages[i]
            ForecastPage(
                page,
                placeName(page),
                state.units,
                now,
                onRefresh = actions::refresh,
                onDetail = onDetail,
                onWarning = actions::openWarning,
            )
        }
    }
}

@Composable
fun placeName(page: PlacePage): String = page.place.name.ifBlank { stringResource(R.string.current_location) }

@Composable
private fun PageDots(
    count: Int,
    current: Int,
) {
    Row(Modifier.padding(top = 4.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** Saved places, current location, and search to add more. Also the first-run screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlacesScreen(
    state: WeatherState,
    actions: WeatherActions,
    snackbar: SnackbarHostState,
    onBack: (() -> Unit)?,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            // Like Contacts: the search bar is the top bar, with settings inside it.
            TesseraSearchTopBar(
                query = state.query,
                onQuery = actions::search,
                placeholder = stringResource(R.string.search_places),
                onBack = onBack,
                action = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.query.isNotBlank()) {
                when {
                    state.searching && state.results.isEmpty() -> {
                        item(key = "searching") { LoadingIndicator(Modifier.padding(24.dp)) }
                    }

                    state.searchFailed -> {
                        item(key = "failed") { Message(stringResource(R.string.search_failed)) }
                    }

                    state.results.isEmpty() -> {
                        item(key = "none") { Message(stringResource(R.string.no_matches, state.query.trim())) }
                    }

                    else -> {
                        items(state.results, key = { "r${it.id}" }) { place ->
                            PlaceRow(
                                headline = place.name,
                                supporting =
                                    listOfNotNull(
                                        place.region,
                                        place.country,
                                    ).filter { it.isNotBlank() }.distinct().joinToString(", "),
                                onClick = {
                                    actions.add(place)
                                    onOpen(place.id)
                                },
                            )
                        }
                    }
                }
            } else {
                // First run: the welcome card offers location; after that, a switch.
                if (state.useLocation == null && state.pages.isEmpty()) {
                    item(key = "welcome") { Welcome(actions) }
                } else {
                    item(key = "location") { LocationRow(state, actions) }
                }
                items(state.pages.filter { it.place.id != Place.HERE }, key = { "p${it.place.id}" }) { page ->
                    SavedPlaceRow(page, state, actions, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(24.dp),
    )
}

@Composable
private fun Welcome(actions: WeatherActions) {
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(R.drawable.ic_partly_cloudy_day),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = { actions.setUseLocation(true) }) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.use_location), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LocationRow(
    state: WeatherState,
    actions: WeatherActions,
) {
    val on = state.useLocation == true && state.locationAllowed
    Surface(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            supportingContent = { Text(stringResource(R.string.use_location_summary)) },
            leadingContent = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = actions::setUseLocation),
        ) { Text(stringResource(R.string.use_location)) }
    }
}

@Composable
private fun PlaceRow(
    headline: String,
    supporting: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            supportingContent = if (supporting.isNotBlank()) ({ Text(supporting) }) else null,
            leadingContent = { Icon(painterResource(DesignR.drawable.ic_location_on), contentDescription = null) },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) { Text(headline) }
    }
}

@Composable
private fun SavedPlaceRow(
    page: PlacePage,
    state: WeatherState,
    actions: WeatherActions,
    onOpen: (Long) -> Unit,
) {
    val place = page.place
    val saved = state.pages.filter { it.place.id != Place.HERE }
    val index = saved.indexOfFirst { it.place.id == place.id }
    var menu by remember { mutableStateOf(false) }
    PlaceRow(
        headline = place.name,
        supporting = listOfNotNull(place.region, place.country).filter { it.isNotBlank() }.distinct().joinToString(", "),
        onClick = { onOpen(place.id) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                page.forecast?.current?.let { now ->
                    Icon(
                        painterResource(now.condition.icon(now.isDay)),
                        contentDescription = stringResource(now.condition.label(now.isDay)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        temp(now.temperature, state.units),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.more_options, place.name),
                        )
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (index > 0) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = {
                                menu = false
                                actions.move(place, -1)
                            })
                        }
                        if (index in 0 until saved.lastIndex) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = {
                                menu = false
                                actions.move(place, 1)
                            })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                            menu = false
                            actions.remove(place)
                        })
                    }
                }
            }
        },
    )
}
