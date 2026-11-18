package io.github.michaelmassoni.tessera.contacts.ui

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.contacts.EditorState
import io.github.michaelmassoni.tessera.contacts.R
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.Labeled
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import io.github.michaelmassoni.tessera.core.design.R as DesignR

private val PhoneTypes =
    listOf(
        Phone.TYPE_MOBILE,
        Phone.TYPE_HOME,
        Phone.TYPE_WORK,
        Phone.TYPE_WORK_MOBILE,
        Phone.TYPE_MAIN,
        Phone.TYPE_FAX_HOME,
        Phone.TYPE_FAX_WORK,
        Phone.TYPE_PAGER,
        Phone.TYPE_OTHER,
    )
private val EmailTypes = listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_MOBILE, Email.TYPE_OTHER)
private val PostalTypes = listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER)

/** The contact editor. [onClose] asks before discarding changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditor(
    state: EditorState,
    onChange: (ContactForm) -> Unit,
    onAccount: (AccountRef) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val resources = LocalResources.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.contactId == null) R.string.editor_new else R.string.editor_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    Button(onClick = onSave, enabled = !state.saving && !form.isEmpty, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.contactId == null && state.accounts.size > 1) {
                    AccountPicker(state.account, state.accounts, onAccount)
                }
                Field(form.givenName, { onChange(form.copy(givenName = it)) }, stringResource(R.string.field_first_name), words = true)
                Field(form.familyName, { onChange(form.copy(familyName = it)) }, stringResource(R.string.field_last_name), words = true)
                Field(form.company, { onChange(form.copy(company = it)) }, stringResource(R.string.field_company), words = true)

                LabeledList(
                    items = form.phones,
                    types = PhoneTypes,
                    typeLabel = { t, l -> Phone.getTypeLabel(resources, t, l).toString() },
                    fieldLabel = stringResource(R.string.field_phone),
                    addLabel = stringResource(R.string.add_phone),
                    keyboard = KeyboardType.Phone,
                    newType = if (form.phones.isEmpty()) Phone.TYPE_MOBILE else Phone.TYPE_HOME,
                    onChange = { onChange(form.copy(phones = it)) },
                )
                LabeledList(
                    items = form.emails,
                    types = EmailTypes,
                    typeLabel = { t, l -> Email.getTypeLabel(resources, t, l).toString() },
                    fieldLabel = stringResource(R.string.field_email),
                    addLabel = stringResource(R.string.add_email),
                    keyboard = KeyboardType.Email,
                    newType = Email.TYPE_HOME,
                    onChange = { onChange(form.copy(emails = it)) },
                )
                LabeledList(
                    items = listOfNotNull(form.address),
                    types = PostalTypes,
                    typeLabel = { t, l -> StructuredPostal.getTypeLabel(resources, t, l).toString() },
                    fieldLabel = stringResource(R.string.field_address),
                    addLabel = stringResource(R.string.add_address),
                    keyboard = KeyboardType.Text,
                    newType = StructuredPostal.TYPE_HOME,
                    maxItems = 1,
                    multiLine = true,
                    onChange = { onChange(form.copy(address = it.firstOrNull())) },
                )
                BirthdayField(form.birthday) { onChange(form.copy(birthday = it)) }
                Field(form.note, { onChange(form.copy(note = it)) }, stringResource(R.string.field_notes), singleLine = false)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    words: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions =
            KeyboardOptions(
                capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
                keyboardType = keyboard,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledList(
    items: List<Labeled>,
    types: List<Int>,
    typeLabel: (Int, String?) -> String,
    fieldLabel: String,
    addLabel: String,
    keyboard: KeyboardType,
    newType: Int,
    onChange: (List<Labeled>) -> Unit,
    maxItems: Int = Int.MAX_VALUE,
    multiLine: Boolean = false,
) {
    // The field added last takes focus, so the user can type straight away.
    var focusIndex by remember { mutableStateOf(-1) }
    items.forEachIndexed { index, item ->
        val focus = remember { FocusRequester() }
        LaunchedEffect(focusIndex) {
            if (index == focusIndex) {
                focus.requestFocus()
                focusIndex = -1
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.value,
                    onValueChange = { v -> onChange(items.toMutableList().also { it[index] = item.copy(value = v) }) },
                    label = { Text(fieldLabel) },
                    singleLine = !multiLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.weight(1f).then(if (index == focusIndex) Modifier.focusRequester(focus) else Modifier),
                )
                IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) {
                    Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.remove_field))
                }
            }
            TypeMenu(item.type, item.label, types, typeLabel) { t ->
                onChange(items.toMutableList().also { it[index] = item.copy(type = t, label = null) })
            }
        }
    }
    if (items.size < maxItems) {
        OutlinedButton(onClick = {
            onChange(items + Labeled("", newType))
            focusIndex = items.size
        }) {
            Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null)
            Text(addLabel, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun TypeMenu(
    type: Int,
    label: String?,
    types: List<Int>,
    typeLabel: (Int, String?) -> String,
    onType: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(typeLabel(type, label))
            Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = stringResource(R.string.field_type))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            types.forEach { t ->
                DropdownMenuItem(text = { Text(typeLabel(t, null)) }, onClick = {
                    open = false
                    onType(t)
                })
            }
        }
    }
}

@Composable
private fun AccountPicker(
    account: AccountRef,
    accounts: List<AccountRef>,
    onAccount: (AccountRef) -> Unit,
) {
    val resources = LocalResources.current
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            stringResource(R.string.save_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { open = true }) {
                Icon(painterResource(DesignR.drawable.ic_account_circle), contentDescription = null)
                Text(accountName(account, resources), modifier = Modifier.padding(horizontal = 8.dp))
                Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = null)
            }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                accounts.forEach { a ->
                    DropdownMenuItem(text = { Text(accountName(a, resources)) }, onClick = {
                        open = false
                        onAccount(a)
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayField(
    value: String,
    onChange: (String) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (value.isBlank()) {
        OutlinedButton(onClick = { picking = true }) {
            Icon(painterResource(DesignR.drawable.ic_cake), contentDescription = null)
            Text(stringResource(R.string.add_birthday), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(DesignR.drawable.ic_cake), contentDescription = null)
                Text(
                    stringResource(R.string.field_birthday) + ": " + formatEventDate(value),
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
            IconButton(onClick = { onChange("") }) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.remove_field))
            }
        }
    }
    if (picking) {
        val initial =
            runCatching {
                LocalDate
                    .parse(value)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(
                            Instant
                                .ofEpochMilli(it)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString(),
                        )
                    }
                    picking = false
                }) { Text(stringResource(R.string.date_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

/** Asks before throwing away edits. */
@Composable
fun DiscardDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_title)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.keep_editing)) } },
    )
}
