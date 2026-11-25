package io.github.michaelmassoni.tessera.podcasts

import android.Manifest
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Podcasts' first run: notifications (download progress; optional), restore. */
internal val podcastsSetup =
    SetupSpec(
        steps =
            listOfNotNull(
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
