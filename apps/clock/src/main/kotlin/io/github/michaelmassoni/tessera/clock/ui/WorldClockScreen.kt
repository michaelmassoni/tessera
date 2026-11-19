package io.github.michaelmassoni.tessera.clock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.worldclock.City
import io.github.michaelmassoni.tessera.clock.worldclock.CityCatalog
import io.github.michaelmassoni.tessera.clock.worldclock.CityOffset
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import io.github.michaelmassoni.tessera.core.design.R as DesignR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen(
    now: ZonedDateTime,
    cities: List<City>,
    onRemove: (City) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberClockFormatter()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(formatter.time(now), style = MaterialTheme.typography.displayLarge)
                Text(
                    formatter.date(now),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (cities.isEmpty()) {
            item { EmptyState(DesignR.drawable.ic_public, stringResource(R.string.cities_empty)) }
        }
        items(cities, key = { it.zoneId }) { city ->
            val state = rememberSwipeToDismissBoxState()
            LaunchedEffect(state.currentValue) {
                if (state.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) onRemove(city)
            }
            val removeLabel = stringResource(R.string.action_remove)
            SwipeToDismissBox(state = state, backgroundContent = {}, modifier = Modifier.animateItem()) {
                CityRow(
                    city = city,
                    now = now,
                    modifier =
                        Modifier.semantics {
                            customActions =
                                listOf(CustomAccessibilityAction(removeLabel) { onRemove(city).let { true } })
                        },
                )
            }
        }
    }
}

@Composable
private fun CityRow(
    city: City,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberClockFormatter()
    val zone = ZoneId.of(city.zoneId)
    val offset = CityOffset.of(now, zone)
    val day =
        when (offset.dayDelta) {
            1 -> stringResource(R.string.day_tomorrow)
            -1 -> stringResource(R.string.day_yesterday)
            else -> stringResource(R.string.day_today)
        }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(city.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "$day, ${offsetText(offset.minutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatter.time(now.withZoneSameInstant(zone)), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun offsetText(minutes: Long): String {
    if (minutes == 0L) return stringResource(R.string.offset_same)
    val h = (abs(minutes) / 60).toInt()
    val m = (abs(minutes) % 60).toInt()
    val amount =
        when {
            m == 0 -> pluralStringResource(R.plurals.offset_hours, h, h)
            h == 0 -> pluralStringResource(R.plurals.offset_minutes, m, m)
            else -> stringResource(R.string.offset_hours_minutes, h, m)
        }
    return stringResource(if (minutes > 0) R.string.offset_ahead else R.string.offset_behind, amount)
}

/** Full-screen city search. Back (with predictive back) closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPicker(
    catalog: CityCatalog,
    onPick: (City) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, catalog) { catalog.search(query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(DesignR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.cities_search)) },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.zoneId }) { city ->
                    ListItem(
                        supportingContent = { if (city.country.isNotEmpty()) Text(city.country) },
                        modifier = Modifier.clickable { onPick(city) },
                    ) { Text(city.name) }
                }
            }
        }
    }
}

@TesseraPreviews
@Composable
private fun WorldClockPreview() {
    ClockPreview {
        WorldClockScreen(
            now = ZonedDateTime.of(2026, 11, 18, 22, 5, 0, 0, ZoneId.of("America/New_York")),
            cities =
                listOf(
                    City("Europe/London", "London", "United Kingdom"),
                    City("Asia/Tokyo", "Tokyo", "Japan"),
                    City("America/St_Johns", "St. John’s", "Canada"),
                ),
            onRemove = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}
