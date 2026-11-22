package io.github.michaelmassoni.tessera.music

import android.Manifest
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Music's first-run setup (see core:settings Setup). */
internal val musicSetup =
    SetupSpec(
        steps =
            listOf(
                SetupStep(
                    DesignR.drawable.ic_music_note,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(
                        listOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            },
                        ),
                    ),
                ),
            ),
        offerRestore = true,
    )
