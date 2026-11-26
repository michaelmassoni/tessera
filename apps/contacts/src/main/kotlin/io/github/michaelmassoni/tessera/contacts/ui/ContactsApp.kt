package io.github.michaelmassoni.tessera.contacts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.contacts.ContactsEvent
import io.github.michaelmassoni.tessera.contacts.EditorState
import io.github.michaelmassoni.tessera.contacts.ListState
import io.github.michaelmassoni.tessera.contacts.R
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import io.github.michaelmassoni.tessera.core.design.keyboardVisible
import kotlinx.coroutines.flow.Flow
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the list screen can do. */
interface ListActions {
    fun setSearch(text: String)

    fun open(contact: ContactSummary)

    fun create()

    fun setAccountFilter(account: AccountRef?)

    fun import()

    fun export()

    fun openSettings()
}

/** What the editor can do. */
interface EditActions {
    fun change(form: ContactForm)

    fun account(account: AccountRef)

    fun save()

    fun cancel()
}

private val ExpandedWidth = 840.dp

/** What the single pane shows on compact and medium widths: the top of [contactPages]. */
private data class Screen(
    val detail: ContactDetail?,
    val editor: EditorState?,
) {
    val key = contactPages(detail?.id, editor != null, editor?.contactId).last()
}

/**
 * The app: list, detail and editor. One at a time on compact and medium widths; list beside
 * detail (or editor) on expanded widths. Back leaves the editor (asking about unsaved changes),
 * then the detail, then the search.
 */
@Composable
fun ContactsApp(
    list: ListState,
    openId: Long?,
    detail: ContactDetail?,
    editor: EditorState?,
    events: Flow<ContactsEvent>,
    listActions: ListActions,
    detailActions: DetailActions,
    editActions: EditActions,
    modifier: Modifier = Modifier,
    pickMode: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val closeEditor = { if (editor?.changed == true) confirmDiscard = true else editActions.cancel() }
    val actions =
        remember(detailActions) {
            object : DetailActions by detailActions {
                override fun delete() {
                    confirmDelete = true
                }
            }
        }

    ContactEvents(events, snackbar)
    BackHandler(enabled = editor != null) { closeEditor() }
    BackHandler(enabled = editor == null && detail != null) { detailActions.back() }
    // Not while the keyboard is up: Back closes it first, as in every other app.
    BackHandler(enabled = editor == null && detail == null && list.search.isNotEmpty() && !keyboardVisible()) { listActions.setSearch("") }

    // The list (and a contact under its editor) keeps its scroll position while a page is open over
    // it; a closed page's is dropped.
    val saveable = rememberSaveableStateHolder()
    val pages = remember(saveable) { PageStack(saveable::removeState) }
    val stack = contactPages(detail?.id, editor != null, editor?.contactId)
    SideEffect { pages.show(stack) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= ExpandedWidth
        val listWidth = (maxWidth * 0.38f).coerceIn(360.dp, 440.dp)
        val listPane: @Composable (Modifier) -> Unit = { m ->
            ListScreen(list, if (expanded) openId else null, snackbar, listActions, pickMode, m)
        }
        val editorPane: @Composable (EditorState) -> Unit = { e ->
            ContactEditor(e, editActions::change, editActions::account, editActions::save, closeEditor)
        }
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                listPane(Modifier.width(listWidth).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                    when {
                        editor != null -> {
                            editorPane(editor)
                        }

                        detail != null -> {
                            ContactDetailScreen(detail, actions, showBack = false)
                        }

                        else -> {
                            EmptyMessage(
                                DesignR.drawable.ic_person,
                                pluralStringResource(R.plurals.contact_count, list.total, list.total),
                            )
                        }
                    }
                }
            }
        } else {
            AnimatedContent(
                Screen(detail, editor),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it.key },
                label = "screen",
            ) { s ->
                saveable.SaveableStateProvider(s.key) {
                    when {
                        s.editor != null -> editorPane(s.editor)
                        s.detail != null -> ContactDetailScreen(s.detail, actions)
                        else -> listPane(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        DiscardDialog(
            onDiscard = {
                confirmDiscard = false
                editActions.cancel()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }
    if (confirmDelete && detail != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_confirm, detail.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    detailActions.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ContactEvents(
    events: Flow<ContactsEvent>,
    snackbar: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collect { e ->
            val text =
                when (e) {
                    is ContactsEvent.Saved -> resources.getString(R.string.saved)
                    ContactsEvent.SaveFailed -> resources.getString(R.string.save_failed)
                    is ContactsEvent.Deleted -> resources.getString(R.string.deleted, e.name)
                    is ContactsEvent.Imported -> resources.getQuantityString(R.plurals.imported, e.count, e.count)
                    ContactsEvent.ImportFailed -> resources.getString(R.string.import_failed)
                }
            snackbar.showSnackbar(text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(
    state: ListState,
    selectedId: Long?,
    snackbar: SnackbarHostState,
    actions: ListActions,
    pickMode: Boolean,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var choosingAccount by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            val text = rememberTextFieldState(state.search)
            LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(actions::setSearch) }
            LaunchedEffect(state.search) { if (state.search.isEmpty() && text.text.isNotEmpty()) text.edit { replace(0, length, "") } }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TesseraSearchBar(
                    text = text,
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
                    trailingIcon = {
                        if (text.text.isNotEmpty()) {
                            IconButton(onClick = { text.edit { replace(0, length, "") } }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_close),
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        } else if (!pickMode) {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(
                                        painterResource(DesignR.drawable.ic_more_vert),
                                        contentDescription = stringResource(R.string.action_more),
                                    )
                                }
                                ListMenu(
                                    menu,
                                    { menu = false },
                                    actions,
                                    onChooseAccount = { choosingAccount = true },
                                    showAccounts =
                                        state.accounts.size > 1,
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!pickMode) {
                ExtendedFloatingActionButton(
                    onClick = actions::create,
                    icon = { Icon(painterResource(DesignR.drawable.ic_person_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.create_contact)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val searching = state.search.isNotBlank()
            when {
                !state.loaded -> {}

                searching && state.results.isEmpty() -> {
                    EmptyMessage(DesignR.drawable.ic_search, stringResource(R.string.empty_search))
                }

                !searching && state.total == 0 -> {
                    EmptyMessage(DesignR.drawable.ic_contacts, stringResource(R.string.empty_contacts))
                }

                else -> {
                    ContactList(
                        sections = state.sections,
                        results = if (searching) state.results else null,
                        selectedId = selectedId,
                        onOpen = actions::open,
                        contentPadding = PaddingValues(bottom = 96.dp),
                    )
                }
            }
        }
    }
    if (choosingAccount) {
        AccountFilterDialog(state, onSelect = {
            actions.setAccountFilter(it)
            choosingAccount = false
        }, onDismiss = { choosingAccount = false })
    }
}

@Composable
private fun ListMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    actions: ListActions,
    onChooseAccount: () -> Unit,
    showAccounts: Boolean,
) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        val item: @Composable (Int, Int, () -> Unit) -> Unit = { text, icon, onClick ->
            DropdownMenuItem(
                text = { Text(stringResource(text)) },
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                onClick = {
                    onDismiss()
                    onClick()
                },
            )
        }
        if (showAccounts) item(R.string.all_accounts, DesignR.drawable.ic_account_circle, onChooseAccount)
        item(R.string.action_import, DesignR.drawable.ic_download, actions::import)
        item(R.string.action_export, DesignR.drawable.ic_upload, actions::export)
        HorizontalDivider()
        item(R.string.settings, DesignR.drawable.ic_settings, actions::openSettings)
    }
}

@Composable
private fun AccountFilterDialog(
    state: ListState,
    onSelect: (AccountRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            Column {
                (listOf<AccountRef?>(null) + state.accounts).forEach { account ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.accountFilter == account, onClick = { onSelect(account) })
                        Text(account?.let { accountName(it, resources) } ?: stringResource(R.string.all_accounts))
                    }
                }
            }
        },
    )
}

@Composable
fun EmptyMessage(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shown until contacts access is granted. */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_contacts),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = if (denied) onSettings else onGrant) {
                    Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_grant))
                }
            }
        }
    }
}
