package io.github.michaelmassoni.tessera.files

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Files's first-run setup (see core:settings Setup). */
internal val filesSetup =
    SetupSpec(
        steps =
            listOf(
                SetupStep(
                    DesignR.drawable.ic_folder,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Special(
                        intent = { Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${it.packageName}".toUri()) },
                        granted = { Environment.isExternalStorageManager() },
                    ),
                ),
            ),
    )
