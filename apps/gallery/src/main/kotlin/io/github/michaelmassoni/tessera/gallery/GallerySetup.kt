package io.github.michaelmassoni.tessera.gallery

import android.content.pm.PackageManager
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Gallery's first run: photos and videos. Choosing "selected photos" in Android's dialog counts
 * as done; Gallery offers to change it later.
 */
internal fun gallerySetup(permissions: Array<String>) =
    SetupSpec(
        steps =
            listOf(
                SetupStep(
                    DesignR.drawable.ic_photo_library,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(permissions.toList()) { context ->
                        // Any of them: all photos, or the ones picked.
                        permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
                    },
                ),
            ),
    )
