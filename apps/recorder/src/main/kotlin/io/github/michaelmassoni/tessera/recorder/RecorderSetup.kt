package io.github.michaelmassoni.tessera.recorder

import android.Manifest
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Recorder's first run: the microphone (required), notifications, restore. */
internal val recorderSetup =
    SetupSpec(
        steps =
            listOfNotNull(
                SetupStep(
                    DesignR.drawable.ic_mic,
                    R.string.setup_microphone_title,
                    R.string.setup_microphone_text,
                    SetupGrant.Permissions(listOf(Manifest.permission.RECORD_AUDIO)),
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
