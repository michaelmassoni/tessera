package io.github.michaelmassoni.tessera.calendar

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.calendar.sync.CalendarAccounts
import io.github.michaelmassoni.tessera.core.dav.DavSetupPage
import io.github.michaelmassoni.tessera.core.dav.DavSetupTexts
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Calendar's first-run setup (see core:settings Setup). */
internal val calendarSetup =
    SetupSpec(
        custom = listOf(SetupCustom(afterSteps = true) { next -> SyncPage(next) }),
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

/**
 * "Sync with a server?": use the phone's calendar accounts as they are, or sign in to a CalDAV
 * server and choose its calendars (docs/sync.md).
 */
@Composable
private fun SyncPage(next: () -> Unit) {
    val context = LocalContext.current
    val davx5 = stringResource(R.string.sync_also_davx5)
    val other = stringResource(R.string.sync_also_other)
    DavSetupPage(
        icon = DesignR.drawable.ic_calendar_month,
        texts =
            DavSetupTexts(
                intro = stringResource(R.string.setup_sync_text),
                keepLocal = stringResource(R.string.sync_local),
                servers = stringResource(R.string.sync_server_text),
                pickTitle = stringResource(R.string.setup_pick_title),
                pickText = stringResource(R.string.setup_pick_text),
                empty = stringResource(R.string.no_server_calendars),
                use = stringResource(R.string.use_calendars),
                notFound = stringResource(R.string.problem_not_caldav),
            ),
        signIn = { f -> CalendarAccounts.signIn(context, f.address, f.username, f.password).map { it.withNotes(context, davx5, other) } },
        onUse = { CalendarAccounts.useCalendars(context, it) },
        next = next,
    )
}
