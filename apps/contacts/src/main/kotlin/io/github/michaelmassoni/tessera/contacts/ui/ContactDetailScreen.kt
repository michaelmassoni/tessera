package io.github.michaelmassoni.tessera.contacts.ui

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.contacts.R
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactQr
import io.github.michaelmassoni.tessera.contacts.engine.Labeled
import io.github.michaelmassoni.tessera.core.design.Avatar
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the detail screen can do; implemented by the activity (intents) and ViewModel. */
interface DetailActions {
    fun back()

    fun call(number: String)

    fun message(number: String)

    fun email(address: String)

    fun map(address: String)

    fun web(url: String)

    fun toggleStarred()

    fun edit()

    fun share(detail: ContactDetail)

    fun delete()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    detail: ContactDetail,
    actions: DetailActions,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    var menu by remember { mutableStateOf(false) }
    var showQr by rememberSaveable { mutableStateOf(false) }
    if (showQr) QrCodeDialog(detail.name, remember(detail) { ContactQr.vcard(detail) }) { showQr = false }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = actions::back) {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = actions::toggleStarred) {
                        Icon(
                            painterResource(if (detail.starred) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                            contentDescription =
                                stringResource(
                                    if (detail.starred) R.string.action_unfavourite else R.string.action_favourite,
                                ),
                        )
                    }
                    IconButton(onClick = actions::edit) {
                        Icon(painterResource(DesignR.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_share), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    actions.share(detail)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share_qr)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    showQr = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    actions.delete()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val name = detail.name.ifBlank { stringResource(R.string.unnamed) }
                Avatar(name, detail.lookupKey, detail.photoUri, size = 112.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    val org = listOf(detail.title, detail.company).filter { it.isNotBlank() }.joinToString(", ")
                    if (org.isNotBlank()) {
                        Text(org, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                QuickActions(detail, actions)
                ContactInfo(detail, actions)
                About(detail, actions)
            }
        }
    }
}

@Composable
private fun QuickActions(
    detail: ContactDetail,
    actions: DetailActions,
) {
    val phone = detail.phones.firstOrNull()?.value
    val email = detail.emails.firstOrNull()?.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickAction(DesignR.drawable.ic_call, stringResource(R.string.action_call), phone != null) { phone?.let(actions::call) }
        QuickAction(DesignR.drawable.ic_chat, stringResource(R.string.action_text), phone != null) { phone?.let(actions::message) }
        QuickAction(DesignR.drawable.ic_mail, stringResource(R.string.action_email), email != null) { email?.let(actions::email) }
    }
}

@Composable
private fun QuickAction(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // The button carries the label for TalkBack; the caption under it is not read a second time.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.widthIn(min = 64.dp),
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
        Text(
            label,
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactInfo(
    detail: ContactDetail,
    actions: DetailActions,
) {
    if (detail.phones.isEmpty() && detail.emails.isEmpty() && detail.addresses.isEmpty()) return
    val resources = LocalResources.current
    val country = LocalConfiguration.current.locales[0].country
    InfoCard(stringResource(R.string.section_contact_info)) {
        detail.phones.forEach { phone ->
            val formatted = PhoneNumberUtils.formatNumber(phone.value, country) ?: phone.value
            InfoRow(
                icon = DesignR.drawable.ic_call,
                text = formatted,
                label = Phone.getTypeLabel(resources, phone.type, phone.label).toString(),
                onClick = { actions.call(phone.value) },
                trailing = {
                    IconButton(onClick = { actions.message(phone.value) }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_chat),
                            contentDescription = stringResource(R.string.action_message_number, formatted),
                        )
                    }
                },
            )
        }
        detail.emails.forEach { email ->
            InfoRow(
                DesignR.drawable.ic_mail,
                email.value,
                Email.getTypeLabel(resources, email.type, email.label).toString(),
            ) { actions.email(email.value) }
        }
        detail.addresses.forEach { address ->
            InfoRow(
                DesignR.drawable.ic_location_on,
                address.value,
                StructuredPostal.getTypeLabel(resources, address.type, address.label).toString(),
            ) { actions.map(address.value) }
        }
    }
}

@Composable
private fun About(
    detail: ContactDetail,
    actions: DetailActions,
) {
    val resources = LocalResources.current
    InfoCard(stringResource(R.string.section_about)) {
        detail.events.forEach { event ->
            InfoRow(
                if (event.type == Event.TYPE_BIRTHDAY) DesignR.drawable.ic_cake else DesignR.drawable.ic_event,
                formatEventDate(event.value),
                eventLabel(event, resources),
            )
        }
        detail.websites.forEach { url ->
            InfoRow(DesignR.drawable.ic_link, url, stringResource(R.string.label_website)) { actions.web(url) }
        }
        if (detail.note.isNotBlank()) InfoRow(DesignR.drawable.ic_notes, detail.note, stringResource(R.string.label_notes))
        InfoRow(
            DesignR.drawable.ic_account_circle,
            detail.accounts.joinToString { accountName(it, resources) },
            stringResource(R.string.label_saved_in),
        )
    }
}

private fun eventLabel(
    event: Labeled,
    resources: android.content.res.Resources,
): String =
    if (event.type == Event.TYPE_CUSTOM && !event.label.isNullOrBlank()) {
        event.label
    } else {
        resources.getString(Event.getTypeResource(event.type))
    }

fun accountName(
    account: AccountRef,
    resources: android.content.res.Resources,
): String = if (account.isDevice) resources.getString(R.string.device_account) else account.name.orEmpty()

/** "1990-04-23" → "23 April 1990"; "--04-23" → "23 April". Anything else is shown as stored. */
fun formatEventDate(value: String): String {
    val locale = Locale.getDefault()
    return runCatching {
        if (value.startsWith("--")) {
            MonthDay
                .parse(
                    value,
                ).atYear(
                    2000,
                ).format(
                    DateTimeFormatter.ofPattern(
                        android.text.format.DateFormat
                            .getBestDateTimePattern(locale, "MMMMd"),
                        locale,
                    ),
                )
        } else {
            LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        }
    }.getOrDefault(value)
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun InfoRow(
    icon: Int,
    text: String,
    label: String,
    trailing: (@Composable () -> Unit)? = null,
    // Last, so a trailing lambda at a call site is the click action, never composable content.
    onClick: (() -> Unit)? = null,
) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val leading: @Composable () -> Unit = { Icon(painterResource(icon), contentDescription = null) }
    val supporting: @Composable () -> Unit = { Text(label) }
    if (onClick != null) {
        ListItem(onClick = onClick, leadingContent = leading, supportingContent = supporting, trailingContent = trailing, colors = colors) {
            Text(text)
        }
    } else {
        ListItem(leadingContent = leading, supportingContent = supporting, trailingContent = trailing, colors = colors) { Text(text) }
    }
}
