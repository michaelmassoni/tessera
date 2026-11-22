package io.github.michaelmassoni.tessera.contacts

import android.Manifest
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Contacts's first-run setup (see core:settings Setup). */
internal val contactsSetup =
    SetupSpec(
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
