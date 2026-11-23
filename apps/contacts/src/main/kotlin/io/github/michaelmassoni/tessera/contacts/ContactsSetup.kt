package io.github.michaelmassoni.tessera.contacts

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.contacts.sync.ContactsAccounts
import io.github.michaelmassoni.tessera.core.dav.DavSetupPage
import io.github.michaelmassoni.tessera.core.dav.DavSetupTexts
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Contacts's first-run setup (see core:settings Setup): contacts access, then sync with a server. */
internal val contactsSetup =
    SetupSpec(
        custom = listOf(SetupCustom(afterSteps = true) { next -> SyncPage(next) }),
        steps =
            listOf(
                SetupStep(
                    DesignR.drawable.ic_contacts,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
                ),
            ),
    )

/** "Sync with a server?": the phone's accounts as they are, or a CardDAV server's address books. */
@Composable
private fun SyncPage(next: () -> Unit) {
    val context = LocalContext.current
    DavSetupPage(
        icon = DesignR.drawable.ic_contacts,
        texts =
            DavSetupTexts(
                intro = stringResource(R.string.setup_sync_text),
                keepLocal = stringResource(R.string.sync_local),
                servers = stringResource(R.string.sync_server_text),
                pickTitle = stringResource(R.string.setup_pick_title),
                pickText = stringResource(R.string.setup_pick_text),
                empty = stringResource(R.string.no_server_address_books),
                use = stringResource(R.string.use_address_books),
                notFound = stringResource(R.string.problem_not_carddav),
            ),
        signIn = { ContactsAccounts.signIn(context, it.address, it.username, it.password) },
        onUse = { useAddressBooks(context, it) },
        next = next,
    )
}
