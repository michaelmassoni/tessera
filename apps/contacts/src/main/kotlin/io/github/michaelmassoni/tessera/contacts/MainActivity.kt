package io.github.michaelmassoni.tessera.contacts

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.contacts.data.ContactsRepository
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.contacts.engine.Labeled
import io.github.michaelmassoni.tessera.contacts.engine.VCard
import io.github.michaelmassoni.tessera.contacts.sync.ContactsDavStore
import io.github.michaelmassoni.tessera.contacts.sync.ContactsSyncWorker
import io.github.michaelmassoni.tessera.contacts.ui.ContactsApp
import io.github.michaelmassoni.tessera.contacts.ui.DetailActions
import io.github.michaelmassoni.tessera.contacts.ui.EditActions
import io.github.michaelmassoni.tessera.contacts.ui.ListActions
import io.github.michaelmassoni.tessera.contacts.ui.PermissionScreen
import io.github.michaelmassoni.tessera.core.design.clearAvatarCache
import io.github.michaelmassoni.tessera.core.settings.AskForLocalNetwork
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** What another app asked to pick, if this activity was started with ACTION_PICK. */
private enum class PickKind { Contact, Phone, Email }

class MainActivity : ComponentActivity() {
    private val viewModel: ContactsViewModel by viewModels {
        viewModelFactory {
            initializer {
                ContactsViewModel(
                    repository = ContactsRepository(contentResolver),
                    io = Dispatchers.IO,
                    lastAccount = { defaultAccount },
                    rememberAccount = { defaultAccount = it },
                )
            }
        }
    }
    private var granted by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var pick by mutableStateOf<PickKind?>(null)
    private var pendingIntent: Intent? = null

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // A photo may have changed behind the same URI.
                clearAvatarCache()
                // A sync changes contacts one at a time; reload once it settles.
                viewModel.refresh(after = 500)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasPermission()
        pick = pickKind(intent)
        if (savedInstanceState == null) pendingIntent = intent
        val listActions = ListActionsImpl()
        val detailActions = DetailActionsImpl()
        val editActions = EditActionsImpl()
        setTesseraContent {
            SetupGate(contactsSetup) {
                // An account made before Android 17 asked for the local network.
                AskForLocalNetwork(remember { ContactsDavStore(this@MainActivity).account?.address }) {
                    ContactsSyncWorker.schedule.soon(this@MainActivity, delaySeconds = 0)
                }
                val request =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                        granted = hasPermission()
                        denied = !granted && result.isNotEmpty()
                        if (granted) onGranted()
                    }
                if (!granted) {
                    PermissionScreen(
                        denied = denied,
                        onGrant = { request.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
                        onSettings = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                        },
                    )
                } else {
                    val list by viewModel.list.collectAsStateWithLifecycle()
                    val openId by viewModel.openId.collectAsStateWithLifecycle()
                    val detail by viewModel.detail.collectAsStateWithLifecycle()
                    val editor by viewModel.editor.collectAsStateWithLifecycle()
                    ContactsApp(
                        list,
                        openId,
                        detail,
                        editor,
                        viewModel.events,
                        listActions,
                        detailActions,
                        editActions,
                        pickMode = pick != null,
                    )
                    val picking = pick
                    val d = detail
                    if (picking != null && picking != PickKind.Contact && d != null) PickValueDialog(picking, d)
                }
            }
        }
        if (granted) onGranted()
    }

    override fun onResume() {
        super.onResume()
        if (!granted && hasPermission()) {
            granted = true
            denied = false
            onGranted()
        }
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pick = pickKind(intent)
        if (pick != null) {
            viewModel.cancelEdit()
            viewModel.close()
        }
        if (granted) handleIntent(intent) else pendingIntent = intent
    }

    private fun hasPermission() =
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private var observing = false

    private fun onGranted() {
        if (!observing) {
            contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
            observing = true
        }
        viewModel.refresh()
        pendingIntent?.let(::handleIntent)
        pendingIntent = null
    }

    private fun pickKind(intent: Intent): PickKind? {
        if (intent.action != Intent.ACTION_PICK) return null
        val type = intent.type ?: intent.data?.let { contentResolver.getType(it) }
        return when (type) {
            Phone.CONTENT_TYPE -> PickKind.Phone
            Email.CONTENT_TYPE -> PickKind.Email
            else -> PickKind.Contact
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> {
                viewModel.create(prefill(intent))
            }

            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                val data = intent.data ?: return
                val type = intent.type ?: contentResolver.getType(data)
                if (type == "text/x-vcard" || type == "text/vcard" || data.scheme == "file" ||
                    data.lastPathSegment?.endsWith(".vcf") == true
                ) {
                    importFrom(data)
                    return
                }
                lifecycleScope.launch {
                    val id = withContext(Dispatchers.IO) { ContactsRepository(contentResolver).resolveId(data) } ?: return@launch
                    viewModel.open(id)
                    if (intent.action == Intent.ACTION_EDIT) {
                        viewModel.detail.first { it?.id == id }
                        viewModel.editOpen()
                    }
                }
            }
        }
    }

    /** Fields passed with ContactsContract.Intents.Insert. */
    private fun prefill(intent: Intent): ContactForm {
        val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME).orEmpty().trim()
        val given = name.substringBeforeLast(' ', name)
        val family = if (name.contains(' ')) name.substringAfterLast(' ') else ""
        val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
        val email = intent.getStringExtra(ContactsContract.Intents.Insert.EMAIL)
        return ContactForm(
            givenName = given,
            familyName = family,
            phones = listOfNotNull(phone?.let { Labeled(it, Phone.TYPE_MOBILE) }),
            emails = listOfNotNull(email?.let { Labeled(it, Email.TYPE_HOME) }),
            company = intent.getStringExtra(ContactsContract.Intents.Insert.COMPANY).orEmpty(),
            note = intent.getStringExtra(ContactsContract.Intents.Insert.NOTES).orEmpty(),
        )
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            val cards =
                withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { VCard.parse(it.readBytes().decodeToString()) }.orEmpty()
                    } catch (_: IOException) {
                        emptyList()
                    } catch (_: SecurityException) {
                        emptyList()
                    }
                }
            viewModel.import(cards)
        }
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun returnResult(uri: Uri) {
        setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    @Composable
    private fun PickValueDialog(
        kind: PickKind,
        detail: ContactDetail,
    ) {
        val values = if (kind == PickKind.Phone) detail.phones else detail.emails
        val base = if (kind == PickKind.Phone) Phone.CONTENT_URI else Email.CONTENT_URI
        val single = values.singleOrNull()
        if (single?.id != null) {
            returnResult(android.content.ContentUris.withAppendedId(base, single.id))
            return
        }
        AlertDialog(
            onDismissRequest = viewModel::close,
            title = { Text(stringResource(if (kind == PickKind.Phone) R.string.pick_number else R.string.pick_email)) },
            text = {
                Column {
                    values.forEach { v ->
                        TextButton(
                            onClick = { v.id?.let { returnResult(android.content.ContentUris.withAppendedId(base, it)) } },
                        ) { Text(v.value) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::close) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    private inner class ListActionsImpl : ListActions {
        override fun setSearch(text: String) = viewModel.setSearch(text)

        override fun open(contact: ContactSummary) {
            if (pick == PickKind.Contact) {
                returnResult(Contacts.getLookupUri(contact.id, contact.lookupKey))
            } else {
                viewModel.open(contact.id)
            }
        }

        override fun create() = viewModel.create()

        override fun setAccountFilter(account: AccountRef?) = viewModel.setAccountFilter(account)

        override fun import() = importLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream"))

        override fun export() = exportLauncher.launch("contacts-${LocalDate.now()}.vcf")

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importFrom(uri) }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
            if (uri == null) return@registerForActivityResult
            val keys = viewModel.visibleLookupKeys()
            lifecycleScope.launch {
                val ok =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val source = ContactsRepository(contentResolver).vcardUri(keys)
                            contentResolver.openInputStream(source)!!.use { input ->
                                contentResolver.openOutputStream(uri, "wt")!!.use { input.copyTo(it) }
                            }
                        }.isSuccess
                    }
                Toast.makeText(this@MainActivity, if (ok) R.string.exported else R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }

    private inner class DetailActionsImpl : DetailActions {
        override fun back() = viewModel.close()

        override fun call(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

        override fun message(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

        override fun email(address: String) = start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))

        override fun map(address: String) = start(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri()))

        override fun web(url: String) = start(Intent(Intent.ACTION_VIEW, (if (url.contains("://")) url else "https://$url").toUri()))

        override fun toggleStarred() = viewModel.toggleStarred()

        override fun edit() = viewModel.editOpen()

        override fun share(detail: ContactDetail) {
            val uri = ContactsRepository(contentResolver).vcardUri(listOf(detail.lookupKey))
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(Contacts.CONTENT_VCARD_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            start(Intent.createChooser(send, getString(R.string.action_share)))
        }

        override fun delete() = viewModel.delete()
    }

    private inner class EditActionsImpl : EditActions {
        override fun change(form: ContactForm) = viewModel.updateForm(form)

        override fun account(account: AccountRef) = viewModel.setAccount(account)

        override fun save() = viewModel.save()

        override fun cancel() = viewModel.cancelEdit()
    }
}

/** The account new contacts go to; remembered from the last one used. */
var Context.defaultAccount: AccountRef?
    get() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.contains("account_set")) return null
        return AccountRef(prefs.getString("account_name", null), prefs.getString("account_type", null))
    }
    set(value) =
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit {
            if (value == null) {
                remove("account_set")
            } else {
                putBoolean("account_set", true)
                putString("account_name", value.name)
                putString("account_type", value.type)
            }
        }
