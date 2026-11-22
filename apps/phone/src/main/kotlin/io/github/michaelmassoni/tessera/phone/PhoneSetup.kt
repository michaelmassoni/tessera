package io.github.michaelmassoni.tessera.phone

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Phone's first run: the default phone app, calls and contacts, notifications, restore. */
val phoneSetup =
    SetupSpec(
        steps =
            listOfNotNull(
                SetupStep(
                    DesignR.drawable.ic_call,
                    R.string.setup_default_title,
                    R.string.setup_default_text,
                    SetupGrant.Role(RoleManager.ROLE_DIALER),
                    required = false,
                ),
                SetupStep(
                    DesignR.drawable.ic_contacts,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(
                        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
                    ),
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SetupStep(
                        DesignR.drawable.ic_notifications,
                        R.string.setup_notifications_title,
                        R.string.setup_notifications_text,
                        SetupGrant.Permissions(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                        required = false,
                    )
                } else {
                    null
                },
            ),
        offerRestore = true,
    )
