package io.github.michaelmassoni.tessera.phone.ui

import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.phone.R
import io.github.michaelmassoni.tessera.phone.data.ContactNumber
import io.github.michaelmassoni.tessera.phone.engine.CallGroup
import io.github.michaelmassoni.tessera.phone.engine.CallType
import io.github.michaelmassoni.tessera.phone.engine.Dialpad
import io.github.michaelmassoni.tessera.phone.engine.NumberMatch
import java.time.LocalDate
import io.github.michaelmassoni.tessera.core.design.R as DesignR

data class PhoneState(
    val loaded: Boolean = false,
    val recents: List<Pair<LocalDate, List<CallGroup>>> = emptyList(),
    val numbers: List<ContactNumber> = emptyList(),
    val isDefault: Boolean = false,
    val hasPermissions: Boolean = true,
)

interface PhoneActions {
    fun call(number: String)

    fun copy(number: String)

    fun makeDefault()

    fun grant()

    fun openSettings()
}

private enum class Tab(
    val label: Int,
    val icon: Int,
) {
    Favourites(R.string.tab_favourites, DesignR.drawable.ic_star),
    Recents(R.string.tab_recents, DesignR.drawable.ic_schedule),
    Contacts(R.string.tab_contacts, DesignR.drawable.ic_contacts),
    Keypad(R.string.tab_keypad, DesignR.drawable.ic_dialpad),
}

@Composable
fun PhoneApp(
    state: PhoneState,
    actions: PhoneActions,
    modifier: Modifier = Modifier,
    initialTab: Int = 1,
    initialNumber: String = "",
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = tab == t.ordinal,
                    onClick = { tab = t.ordinal },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(stringResource(t.label)) },
                )
            }
        },
    ) {
        PhoneTab(Tab.entries[tab], state, actions, initialNumber)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTab(
    tab: Tab,
    state: PhoneState,
    actions: PhoneActions,
    initialNumber: String,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.label)) },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.hasPermissions) {
                Banner(stringResource(R.string.permissions_needed), stringResource(R.string.grant), actions::grant)
            } else if (!state.isDefault && tab != Tab.Keypad) {
                Banner(stringResource(R.string.not_default), stringResource(R.string.make_default), actions::makeDefault)
            }
            when (tab) {
                Tab.Favourites -> Favourites(state, actions)
                Tab.Recents -> Recents(state, actions)
                Tab.Contacts -> Contacts(state, actions)
                Tab.Keypad -> Keypad(state, actions, initialNumber)
            }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Favourites(
    state: PhoneState,
    actions: PhoneActions,
) {
    val favourites = state.numbers.filter { it.starred }.distinctBy { it.contactId }
    if (state.loaded && favourites.isEmpty()) {
        Empty(stringResource(R.string.empty_favourites))
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(favourites, key = { it.contactId }) { c ->
            Column(
                Modifier.fillMaxWidth().padding(4.dp).clickable { actions.call(c.number) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Initial(c.name, 72)
                Text(
                    c.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** A name's first letter, or a person icon when there's no name (a number on its own). */
@Composable
private fun Initial(
    name: String?,
    size: Int,
) {
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        val letter = name?.firstOrNull { it.isLetterOrDigit() }?.takeIf { it.isLetter() }
        if (letter != null) {
            Text(letter.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(
                painterResource(DesignR.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Recents(
    state: PhoneState,
    actions: PhoneActions,
) {
    if (state.loaded && state.recents.isEmpty()) {
        Empty(stringResource(R.string.empty_recents))
        return
    }
    val context = LocalContext.current
    val country = LocalConfiguration.current.locales[0].country
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        state.recents.forEach { (day, groups) ->
            item(key = "d$day") {
                Text(
                    DateUtils
                        .getRelativeTimeSpanString(
                            day.toEpochDay() * 86_400_000L,
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                        ).toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(groups, key = { "c${it.latest.id}" }) { g ->
                val call = g.latest
                val display =
                    call.name ?: PhoneNumberUtils.formatNumber(call.number, country)
                        ?: call.number.ifBlank { stringResource(R.string.unknown_number) }
                val missed = call.type == CallType.Missed || call.type == CallType.Rejected
                ListItem(
                    leadingContent = { Initial(call.name, 40) },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(
                                    when (call.type) {
                                        CallType.Outgoing -> DesignR.drawable.ic_call_made
                                        CallType.Missed, CallType.Rejected, CallType.Blocked -> DesignR.drawable.ic_call_missed
                                        else -> DesignR.drawable.ic_call_received
                                    },
                                ),
                                contentDescription = stringResource(callTypeLabel(call.type)),
                                tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                DateUtils.formatDateTime(context, call.date, DateUtils.FORMAT_SHOW_TIME),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { actions.call(call.number) }, enabled = call.number.isNotBlank()) {
                            Icon(
                                painterResource(DesignR.drawable.ic_call),
                                contentDescription = stringResource(R.string.call_back, display),
                            )
                        }
                    },
                    modifier =
                        Modifier.combinedClickable(onClick = {
                            if (call.number.isNotBlank()) actions.call(call.number)
                        }, onLongClick = { actions.copy(call.number) }),
                ) {
                    Text(
                        if (g.count > 1) "$display (${g.count})" else display,
                        color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun callTypeLabel(type: CallType) =
    when (type) {
        CallType.Outgoing -> R.string.type_outgoing
        CallType.Missed -> R.string.type_missed
        CallType.Rejected -> R.string.type_rejected
        CallType.Blocked -> R.string.type_blocked
        else -> R.string.type_incoming
    }

@Composable
private fun Contacts(
    state: PhoneState,
    actions: PhoneActions,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by remember { mutableStateOf<List<ContactNumber>?>(null) }
    val resources = LocalResources.current
    val byContact =
        remember(state.numbers) {
            state.numbers
                .groupBy { it.contactId }
                .values
                .toList()
        }
    val shown =
        remember(byContact, query) {
            if (query.isBlank()) {
                byContact
            } else {
                byContact.filter { nums ->
                    nums.first().name.contains(query, ignoreCase = true) ||
                        nums.any { NumberMatch.matches(query, it.number) }
                }
            }
        }
    Column {
        OutlinedTextField(query, {
            query = it
        }, placeholder = {
            Text(stringResource(R.string.search_contacts))
        }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.first().contactId }) { nums ->
                ListItem(
                    onClick = { if (nums.size == 1) actions.call(nums[0].number) else choosing = nums },
                    leadingContent = { Initial(nums.first().name, 40) },
                    supportingContent = { Text(nums.first().number, maxLines = 1) },
                ) { Text(nums.first().name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    choosing?.let { nums ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(nums.first().name) },
            text = {
                Column {
                    nums.forEach { n ->
                        val (type, label) = n.label.split("|", limit = 2).let { (it[0].toIntOrNull() ?: 0) to it.getOrNull(1) }
                        TextButton(onClick = {
                            choosing = null
                            actions.call(n.number)
                        }) { Text("${Phone.getTypeLabel(resources, type, label)}: ${n.number}") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Keypad(
    state: PhoneState,
    actions: PhoneActions,
    initialNumber: String,
) {
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    val country = LocalConfiguration.current.locales[0].country
    val suggestions = remember(number, state.numbers) { state.numbers.filter { NumberMatch.matches(number, it.number) }.take(3) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        suggestions.forEach { s ->
            ListItem(onClick = {
                actions.call(s.number)
            }, supportingContent = { Text(s.number) }, modifier = Modifier.widthIn(max = 480.dp)) { Text(s.name) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                PhoneNumberUtils.formatNumber(number, country) ?: number,
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        KeypadGrid(onKey = { number += it }, onLongKey = { number += it })
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(56.dp))
            FilledIconButton(
                onClick = { if (number.isNotBlank()) actions.call(number) },
                modifier = Modifier.size(width = 96.dp, height = 64.dp),
            ) {
                Icon(painterResource(DesignR.drawable.ic_call), contentDescription = stringResource(R.string.call))
            }
            IconButton(onClick = { number = Dialpad.delete(number) }, enabled = number.isNotEmpty(), modifier = Modifier.size(56.dp)) {
                Icon(painterResource(DesignR.drawable.ic_backspace), contentDescription = stringResource(R.string.delete_digit))
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
