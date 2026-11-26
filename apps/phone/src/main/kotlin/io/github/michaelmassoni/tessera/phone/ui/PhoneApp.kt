package io.github.michaelmassoni.tessera.phone.ui

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.Avatar
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import io.github.michaelmassoni.tessera.core.design.keyboardVisible
import io.github.michaelmassoni.tessera.phone.R
import io.github.michaelmassoni.tessera.phone.data.ContactNumber
import io.github.michaelmassoni.tessera.phone.engine.CallEntry
import io.github.michaelmassoni.tessera.phone.engine.CallGroup
import io.github.michaelmassoni.tessera.phone.engine.CallType
import io.github.michaelmassoni.tessera.phone.engine.ContactSections
import io.github.michaelmassoni.tessera.phone.engine.Dialpad
import io.github.michaelmassoni.tessera.phone.engine.DurationUnit
import io.github.michaelmassoni.tessera.phone.engine.NumberMatch
import io.github.michaelmassoni.tessera.phone.engine.RecentsFilter
import io.github.michaelmassoni.tessera.phone.engine.SpamNumbers
import io.github.michaelmassoni.tessera.phone.engine.T9
import io.github.michaelmassoni.tessera.phone.engine.callDuration
import kotlinx.coroutines.launch
import java.time.LocalDate
import io.github.michaelmassoni.tessera.core.design.R as DesignR

data class PhoneState(
    val loaded: Boolean = false,
    val recents: List<Pair<LocalDate, List<CallGroup>>> = emptyList(),
    val numbers: List<ContactNumber> = emptyList(),
    val isDefault: Boolean = false,
    val hasPermissions: Boolean = true,
    /** The raw call log, newest first, for a number's call history. */
    val calls: List<CallEntry> = emptyList(),
    val blocked: List<String> = emptyList(),
    /** Only the default phone app can block numbers. */
    val canBlock: Boolean = false,
    /** Where unknown numbers are from ("Melbourne"), by number as the call log has it. */
    val locations: Map<String, String> = emptyMap(),
    /** Numbers reported as spam, in E.164. */
    val reported: Set<String> = emptySet(),
    /** The phone's country ("AU"), for matching numbers. */
    val home: String = "",
)

fun PhoneState.isSpam(number: String) = SpamNumbers.isReported(number, reported, home)

interface PhoneActions {
    fun call(number: String)

    fun copy(number: String)

    fun message(number: String)

    fun createContact(number: String)

    fun addToContact(number: String)

    fun viewContact(contact: ContactNumber)

    fun delete(ids: List<Long>)

    fun block(number: String)

    fun unblock(number: String)

    /** Labels [number] "Suspected spam" from now on (on this phone only), or stops. */
    fun reportSpam(
        number: String,
        spam: Boolean,
    )

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
    // Keyed on the dial request, so a new one (onNewIntent) opens the keypad with its number.
    var tab by rememberSaveable(initialTab, initialNumber) { mutableIntStateOf(initialTab) }
    var keypadNumber by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    var history by rememberSaveable { mutableStateOf<String?>(null) }
    // The tabs keep their scroll positions while a call history is open over them; a closed history's
    // is dropped.
    val saveable = rememberSaveableStateHolder()
    val pages = remember(saveable) { PageStack(saveable::removeState) }
    val stack = listOfNotNull(LIST_PAGE, history?.let(::historyPageKey))
    SideEffect { pages.show(stack) }
    history?.let { number ->
        BackHandler { history = null }
        saveable.SaveableStateProvider(historyPageKey(number)) {
            CallHistory(number, state, actions, modifier, onBack = { history = null })
        }
        return
    }
    saveable.SaveableStateProvider(LIST_PAGE) {
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
            PhoneTab(Tab.entries[tab], state, actions, keypadNumber, onHistory = { history = it }) { number ->
                keypadNumber = number
                tab = Tab.Keypad.ordinal
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTab(
    tab: Tab,
    state: PhoneState,
    actions: PhoneActions,
    initialNumber: String,
    onHistory: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val search = rememberTextFieldState()
    // Each list tab keeps its own scroll position when switching tabs.
    val tabs = rememberSaveableStateHolder()
    // Not while the keyboard is up: Back closes it first, as in every other app.
    BackHandler(enabled = tab == Tab.Contacts && search.text.isNotEmpty() && !keyboardVisible()) { search.clearText() }
    Scaffold(
        topBar = {
            if (tab == Tab.Contacts) {
                ContactSearch(search, actions)
            } else {
                TopAppBar(
                    title = { Text(stringResource(tab.label)) },
                    actions = {
                        IconButton(onClick = actions::openSettings) {
                            Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (tab == Tab.Contacts && state.hasPermissions) {
                ExtendedFloatingActionButton(
                    onClick = { actions.createContact("") },
                    icon = { Icon(painterResource(DesignR.drawable.ic_person_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.new_contact)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.hasPermissions) {
                Banner(stringResource(R.string.permissions_needed), stringResource(R.string.grant), actions::grant)
            } else if (!state.isDefault && tab != Tab.Keypad) {
                Banner(stringResource(R.string.not_default), stringResource(R.string.make_default), actions::makeDefault)
            }
            if (tab == Tab.Keypad) {
                // Not kept: the keypad starts from the number it is opened with.
                Keypad(state, actions, initialNumber)
            } else {
                tabs.SaveableStateProvider(tabKey(tab.name)) {
                    when (tab) {
                        Tab.Favourites -> Favourites(state, actions)
                        Tab.Recents -> Recents(state, actions, onHistory, onEdit)
                        Tab.Contacts -> Contacts(state, actions, search.text.toString())
                        Tab.Keypad -> Unit
                    }
                }
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
                Avatar(c.name, c.lookupKey, c.photoUri, size = 72.dp)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Recents(
    state: PhoneState,
    actions: PhoneActions,
    onHistory: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    if (state.loaded && state.recents.isEmpty()) {
        Empty(stringResource(R.string.empty_recents))
        return
    }
    val context = LocalContext.current
    val country = LocalConfiguration.current.locales[0].country
    val query = rememberTextFieldState()
    var missedOnly by rememberSaveable { mutableStateOf(false) }
    val shown = remember(state.recents, query.text, missedOnly) { RecentsFilter.filter(state.recents, query.text.toString(), missedOnly) }
    var options by remember { mutableStateOf<CallGroup?>(null) }
    var confirmBlock by rememberSaveable { mutableStateOf<String?>(null) }
    confirmBlock?.let { number -> BlockDialog(number, onBlock = { actions.block(number) }, onDismiss = { confirmBlock = null }) }
    options?.let { group ->
        val call = group.latest
        val contact =
            remember(call, state.numbers) {
                state.numbers.firstOrNull { PhoneNumberUtils.areSamePhoneNumber(it.number, call.number, country) }
            }
        val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { options = null }, sheetState = sheet) {
            val blocked =
                remember(call, state.blocked) { state.blocked.any { PhoneNumberUtils.areSamePhoneNumber(it, call.number, country) } }
            RecentOptions(
                group,
                contact,
                actions,
                blocked = if (state.canBlock) blocked else null,
                spam = state.isSpam(call.number),
                onEdit = onEdit,
                onHistory = onHistory,
                onBlock = { confirmBlock = it },
            ) { action ->
                scope.launch { sheet.hide() }.invokeOnCompletion {
                    options = null
                    action()
                }
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "filter") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TesseraSearchBar(
                    text = query,
                    placeholder = stringResource(R.string.search_recents),
                    leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = missedOnly,
                    onClick = { missedOnly = !missedOnly },
                    label = { Text(stringResource(R.string.filter_missed)) },
                    leadingIcon =
                        if (missedOnly) {
                            { Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.no_matching_calls),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        shown.forEach { (day, groups) ->
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
                val spam = call.name == null && state.isSpam(call.number)
                val missed = call.type == CallType.Missed || call.type == CallType.Rejected
                ListItem(
                    leadingContent = { Avatar(call.name, call.number, null) },
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
                            val time = DateUtils.formatDateTime(context, call.date, DateUtils.FORMAT_SHOW_TIME)
                            val length = durationText(call.durationSeconds)
                            val where = if (spam) stringResource(R.string.suspected_spam) else state.locations[call.number]
                            Text(
                                listOfNotNull(where, time, length.ifEmpty { null }).joinToString(" · "),
                                color = if (spam) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        }, onLongClick = { if (call.number.isNotBlank()) options = g }),
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

/** "3 min, 20 sec" in the phone's language; empty for an unanswered call. */
@Composable
private fun durationText(seconds: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(seconds, locale) {
        val parts = callDuration(seconds)
        if (parts.isEmpty()) {
            ""
        } else {
            val measures =
                parts.map { (amount, unit) ->
                    Measure(
                        amount,
                        when (unit) {
                            DurationUnit.Hours -> MeasureUnit.HOUR
                            DurationUnit.Minutes -> MeasureUnit.MINUTE
                            DurationUnit.Seconds -> MeasureUnit.SECOND
                        },
                    )
                }
            MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures.toTypedArray())
        }
    }
}

/**
 * What a long press on a recent call offers, like the system dialler: message, copy, edit before
 * calling, either the contact or ways to save the number as one, the number's call history,
 * blocking (as the default phone app; [blocked] is null otherwise) and deleting the row's calls.
 * [done] closes the sheet and then runs the choice.
 */
@Composable
internal fun RecentOptions(
    group: CallGroup,
    contact: ContactNumber?,
    actions: PhoneActions,
    blocked: Boolean?,
    onEdit: (String) -> Unit,
    onHistory: (String) -> Unit,
    onBlock: (String) -> Unit,
    spam: Boolean = false,
    done: (() -> Unit) -> Unit,
) {
    val call = group.latest
    val country = LocalConfiguration.current.locales[0].country
    val number = PhoneNumberUtils.formatNumber(call.number, country) ?: call.number
    val name = contact?.name ?: call.name
    Column(Modifier.padding(bottom = 16.dp)) {
        ListItem(
            leadingContent = { Avatar(name, contact?.lookupKey ?: call.number, contact?.photoUri) },
            supportingContent = if (name != null) ({ Text(number) }) else null,
        ) {
            Text(name ?: number, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        @Composable
        fun Option(
            icon: Int,
            label: Int,
            action: () -> Unit,
        ) = ListItem(
            onClick = { done(action) },
            leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        ) { Text(stringResource(label)) }
        if (contact != null) {
            Option(DesignR.drawable.ic_person, R.string.view_contact) { actions.viewContact(contact) }
        } else {
            Option(DesignR.drawable.ic_person_add, R.string.create_contact) { actions.createContact(call.number) }
            Option(DesignR.drawable.ic_account_circle, R.string.add_to_contact) { actions.addToContact(call.number) }
        }
        Option(DesignR.drawable.ic_chat, R.string.send_message) { actions.message(call.number) }
        Option(DesignR.drawable.ic_content_copy, R.string.copy_number) { actions.copy(call.number) }
        Option(DesignR.drawable.ic_dialpad, R.string.edit_before_call) { onEdit(call.number) }
        Option(DesignR.drawable.ic_history, R.string.call_history) { onHistory(call.number) }
        when (blocked) {
            true -> {
                Option(DesignR.drawable.ic_block, R.string.unblock_number) { actions.unblock(call.number) }
            }

            false -> {
                Option(DesignR.drawable.ic_block, R.string.block_number) { onBlock(call.number) }
            }

            null -> {}
        }
        if (contact == null) {
            Option(
                DesignR.drawable.ic_report,
                if (spam) R.string.not_spam else R.string.report_spam,
            ) { actions.reportSpam(call.number, !spam) }
        }
        Option(DesignR.drawable.ic_delete, R.string.delete_from_history) { actions.delete(group.ids) }
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

/** The Contacts tab's search bar, as in the Contacts app: clear while typing, settings otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSearch(
    text: TextFieldState,
    actions: PhoneActions,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TesseraSearchBar(
            text = text,
            placeholder = stringResource(R.string.search_contacts),
            modifier = Modifier.weight(1f),
            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (text.text.isNotEmpty()) {
                    IconButton(onClick = { text.clearText() }) {
                        Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                    }
                } else {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Contacts(
    state: PhoneState,
    actions: PhoneActions,
    query: String,
) {
    var choosing by remember { mutableStateOf<List<ContactNumber>?>(null) }
    val resources = LocalResources.current
    val byContact =
        remember(state.numbers) {
            state.numbers
                .groupBy { it.contactId }
                .values
                .toList()
        }
    val sections =
        remember(byContact, query) {
            val shown = byContact.filter { nums -> ContactSections.matches(query, nums.first().name, nums.map { it.number }) }
            if (query.isBlank()) ContactSections.sections(shown, { it.first().name }) else listOf("" to shown)
        }
    when {
        !state.loaded -> {}

        query.isNotBlank() && sections.all { it.second.isEmpty() } -> {
            Empty(stringResource(R.string.empty_search))
        }

        byContact.isEmpty() -> {
            Empty(stringResource(R.string.empty_contacts))
        }

        else -> {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                sections.forEach { (letter, contacts) ->
                    if (letter.isNotEmpty()) {
                        stickyHeader(key = "s$letter", contentType = "header") {
                            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                Text(
                                    letter,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                                )
                            }
                        }
                    }
                    items(contacts, key = { it.first().contactId }, contentType = { "contact" }) { nums ->
                        val first = nums.first()
                        ListItem(
                            onClick = { if (nums.size == 1) actions.call(first.number) else choosing = nums },
                            onLongClick = { actions.viewContact(first) },
                            onLongClickLabel = stringResource(R.string.view_contact),
                            leadingContent = { Avatar(first.name, first.lookupKey, first.photoUri) },
                            supportingContent = {
                                Text(
                                    if (nums.size ==
                                        1
                                    ) {
                                        numberLine(resources, first)
                                    } else {
                                        pluralStringResource(R.plurals.numbers, nums.size, nums.size)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text(first.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
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
                        TextButton(onClick = {
                            choosing = null
                            actions.call(n.number)
                        }) { Text(numberLine(resources, n)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "Mobile · 0412 345 678". */
private fun numberLine(
    resources: Resources,
    n: ContactNumber,
): String {
    val (type, label) = n.label.split("|", limit = 2).let { (it[0].toIntOrNull() ?: 0) to it.getOrNull(1) }
    return "${Phone.getTypeLabel(resources, type, label)} · ${n.number}"
}

@Composable
private fun Keypad(
    state: PhoneState,
    actions: PhoneActions,
    initialNumber: String,
) {
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    val country = LocalConfiguration.current.locales[0].country
    // By number, or by name typed on the letters (T9), one row per contact.
    val suggestions =
        remember(number, state.numbers) {
            state.numbers
                .filter { NumberMatch.matches(number, it.number) || T9.matches(number, it.name) }
                .distinctBy { it.contactId }
                .take(3)
        }
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

@Composable
private fun BlockDialog(
    number: String,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val country = LocalConfiguration.current.locales[0].country
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(DesignR.drawable.ic_block), contentDescription = null) },
        title = { Text(stringResource(R.string.block_title, PhoneNumberUtils.formatNumber(number, country) ?: number)) },
        text = { Text(stringResource(R.string.block_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onBlock()
            }) { Text(stringResource(R.string.block)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Every call with [number], newest first, under a header with the contact and quick actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallHistory(
    number: String,
    state: PhoneState,
    actions: PhoneActions,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val country = LocalConfiguration.current.locales[0].country
    val calls = remember(number, state.calls) { state.calls.filter { PhoneNumberUtils.areSamePhoneNumber(it.number, number, country) } }
    val contact =
        remember(number, state.numbers) { state.numbers.firstOrNull { PhoneNumberUtils.areSamePhoneNumber(it.number, number, country) } }
    val formatted = PhoneNumberUtils.formatNumber(number, country) ?: number
    val name = contact?.name ?: calls.firstNotNullOfOrNull { it.name }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.call_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name, contact?.lookupKey ?: number, contact?.photoUri, size = 96.dp)
                    Text(
                        name ?: formatted,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (name != null) Text(formatted, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (contact == null) {
                        if (state.isSpam(number)) {
                            Text(stringResource(R.string.suspected_spam), color = MaterialTheme.colorScheme.error)
                        } else {
                            state.locations[number]?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        @Composable
                        fun Action(
                            icon: Int,
                            label: Int,
                            onClick: () -> Unit,
                        ) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
                                Icon(painterResource(icon), contentDescription = null)
                            }
                            Text(
                                stringResource(label),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Action(DesignR.drawable.ic_call, R.string.call) { actions.call(number) }
                        Action(DesignR.drawable.ic_chat, R.string.message) { actions.message(number) }
                        if (contact != null) {
                            Action(DesignR.drawable.ic_person, R.string.contact) { actions.viewContact(contact) }
                        } else {
                            Action(DesignR.drawable.ic_person_add, R.string.add_contact) { actions.createContact(number) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
            items(calls, key = { it.id }) { call ->
                val missed = call.type == CallType.Missed || call.type == CallType.Rejected
                val length = durationText(call.durationSeconds)
                ListItem(
                    leadingContent = {
                        Icon(
                            painterResource(callTypeIcon(call.type)),
                            contentDescription = null,
                            tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = {
                        Text(
                            DateUtils.formatDateTime(
                                context,
                                call.date,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or
                                    DateUtils.FORMAT_SHOW_TIME,
                            ),
                        )
                    },
                    trailingContent = if (length.isNotEmpty()) ({ Text(length) }) else null,
                ) { Text(stringResource(callTypeLabel(call.type))) }
            }
        }
    }
}

private fun callTypeIcon(type: CallType) =
    when (type) {
        CallType.Outgoing -> DesignR.drawable.ic_call_made
        CallType.Missed, CallType.Rejected, CallType.Blocked -> DesignR.drawable.ic_call_missed
        else -> DesignR.drawable.ic_call_received
    }
