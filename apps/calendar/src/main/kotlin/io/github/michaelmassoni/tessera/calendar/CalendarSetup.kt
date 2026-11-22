package io.github.michaelmassoni.tessera.calendar

import android.Manifest
import android.os.Build
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Calendar's first-run setup (see core:settings Setup). */
internal val calendarSetup =
    SetupSpec(
        steps =
            listOfNotNull(
                SetupStep(
                    DesignR.drawable.ic_calendar_month,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)),
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
    )
