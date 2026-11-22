package io.github.michaelmassoni.tessera.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.phone.data.ContactNumber
import io.github.michaelmassoni.tessera.phone.data.PhoneRepository
import io.github.michaelmassoni.tessera.phone.data.SpamReports
import io.github.michaelmassoni.tessera.phone.engine.CallLogGrouping
import io.github.michaelmassoni.tessera.phone.engine.CallerLocation
import io.github.michaelmassoni.tessera.phone.engine.SpamNumbers
import io.github.michaelmassoni.tessera.phone.ui.PhoneActions
import io.github.michaelmassoni.tessera.phone.ui.PhoneApp
import io.github.michaelmassoni.tessera.phone.ui.PhoneState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(PhoneState())
    private var dialNumber by mutableStateOf<String?>(null)

    private val permissions =
        listOfNotNull(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        )

    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { load() }

    /** Calls waiting to be deleted while WRITE_CALL_LOG is asked for. */
    private var pendingDelete: List<Long> = emptyList()
    private val requestWriteLog =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) deleteCalls(pendingDelete)
            pendingDelete = emptyList()
        }

    private val requestRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialNumber = numberFrom(intent)
        val actions = Actions()
        setTesseraContent {
            val number = dialNumber
            // A dial request opens the keypad with the number filled in.
            PhoneApp(state, actions, initialTab = if (number != null) 3 else 1, initialNumber = number.orEmpty())
        }
        if (!hasAll()) request.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberFrom(intent)?.let { dialNumber = it }
    }

    private fun numberFrom(intent: Intent): String? {
        if (intent.action != Intent.ACTION_DIAL && intent.action != Intent.ACTION_VIEW) return null
        return intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart ?: ""
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun hasAll() =
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS).all(::granted)

    private fun load() {
        val isDefault = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        lifecycleScope.launch {
            val repo = PhoneRepository(contentResolver)
            // The call log and the contacts are read at the same time; this runs on every resume.
            val calls =
                async(Dispatchers.IO) {
                    if (granted(
                            Manifest.permission.READ_CALL_LOG,
                        )
                    ) {
                        runCatching { repo.callLog() }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
            val numbers =
                async(Dispatchers.IO) {
                    if (granted(
                            Manifest.permission.READ_CONTACTS,
                        )
                    ) {
                        runCatching { repo.numbers() }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
            val canBlock =
                isDefault && runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(this@MainActivity) }.getOrDefault(false)
            val blocked =
                async(Dispatchers.IO) { if (canBlock) runCatching { repo.blockedNumbers() }.getOrDefault(emptyList()) else emptyList() }
            val log = calls.await()
            val sections = withContext(Dispatchers.Default) { CallLogGrouping.sections(log, ZoneId.systemDefault()) }
            // Where unknown callers are from, worked out on the phone (off the main thread: the data loads lazily).
            val locations =
                withContext(Dispatchers.Default) {
                    log
                        .filter { it.name == null && it.number.isNotBlank() }
                        .map { it.number }
                        .distinct()
                        .mapNotNull { n -> callerLocation.describe(n)?.let { n to it } }
                        .toMap()
                }
            state =
                PhoneState(
                    true,
                    sections,
                    numbers.await(),
                    isDefault,
                    hasAll(),
                    log,
                    blocked.await(),
                    canBlock,
                    locations = locations,
                    reported = spamReports.all(),
                    home = home,
                )
        }
    }

    private fun deleteCalls(ids: List<Long>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { runCatching { PhoneRepository(contentResolver).deleteCalls(ids) } }
            load()
        }
    }

    private val home by lazy {
        getSystemService(TelephonyManager::class.java)?.networkCountryIso?.uppercase()?.ifBlank { null }
            ?: resources.configuration.locales[0].country
    }
    private val callerLocation by lazy { CallerLocation(home) }
    private val spamReports by lazy { SpamReports(this) }

    private inner class Actions : PhoneActions {
        override fun reportSpam(
            number: String,
            spam: Boolean,
        ) {
            SpamNumbers.key(number, home)?.let { spamReports.set(it, spam) }
            state = state.copy(reported = spamReports.all())
        }

        override fun delete(ids: List<Long>) {
            if (granted(Manifest.permission.WRITE_CALL_LOG)) {
                deleteCalls(ids)
            } else {
                pendingDelete = ids
                requestWriteLog.launch(Manifest.permission.WRITE_CALL_LOG)
            }
        }

        override fun block(number: String) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { PhoneRepository(contentResolver).block(number) } }
                load()
            }
        }

        override fun unblock(number: String) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { runCatching { BlockedNumberContract.unblock(this@MainActivity, number) } }
                load()
            }
        }

        override fun call(number: String) {
            val uri = Uri.fromParts("tel", number, null)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                // Telecom routes the call: to our in-call screen when we're the default phone app, otherwise to the current one.
                runCatching { getSystemService(TelecomManager::class.java).placeCall(uri, Bundle()) }
                    .onFailure { startActivity(Intent(Intent.ACTION_DIAL, uri)) }
            } else {
                startActivity(Intent(Intent.ACTION_DIAL, uri))
            }
        }

        override fun copy(number: String) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(number, number))
            if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU
            ) {
                Toast.makeText(this@MainActivity, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }

        override fun message(number: String) = open(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

        override fun createContact(number: String) =
            open(
                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                    .apply { if (number.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, number) },
            )

        override fun addToContact(number: String) =
            open(
                Intent(Intent.ACTION_INSERT_OR_EDIT)
                    .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    .putExtra(ContactsContract.Intents.Insert.PHONE, number),
            )

        override fun viewContact(contact: ContactNumber) =
            open(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.contactId, contact.lookupKey)))

        private fun open(intent: Intent) {
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.no_app, Toast.LENGTH_SHORT).show()
            }
        }

        override fun makeDefault() {
            getSystemService(RoleManager::class.java)?.let { requestRole.launch(it.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }
        }

        override fun grant() = request.launch(permissions.toTypedArray())

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }
}
