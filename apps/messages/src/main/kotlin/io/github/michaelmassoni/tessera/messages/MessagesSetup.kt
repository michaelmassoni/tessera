package io.github.michaelmassoni.tessera.messages

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What Messages can't work without: reading and sending texts, and contacts' names. */
internal val messagesRequired = listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)

/** Messages' first run: the default SMS app, texts and contacts, notifications, restore. */
val messagesSetup =
    SetupSpec(
        steps =
            listOfNotNull(
                SetupStep(
                    DesignR.drawable.ic_chat,
                    R.string.setup_default_title,
                    R.string.setup_default_text,
                    SetupGrant.Role(RoleManager.ROLE_SMS),
                    required = false,
                ),
                SetupStep(
                    DesignR.drawable.ic_contacts,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(messagesRequired),
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
