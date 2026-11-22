package io.github.michaelmassoni.tessera.clock

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Clock's first-run setup (see core:settings Setup). */
internal val clockSetup =
    SetupSpec(
        steps =
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SetupStep(
                        DesignR.drawable.ic_notifications,
                        R.string.setup_notifications_title,
                        R.string.setup_notifications_text,
                        SetupGrant.Permissions(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                    )
                } else {
                    null
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SetupStep(
                        DesignR.drawable.ic_alarm,
                        R.string.setup_full_screen_title,
                        R.string.setup_full_screen_text,
                        SetupGrant.Special(
                            intent = { Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${it.packageName}".toUri()) },
                            granted = { it.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
                        ),
                        required = false,
                    )
                } else {
                    null
                },
            ),
        offerRestore = true,
    )
