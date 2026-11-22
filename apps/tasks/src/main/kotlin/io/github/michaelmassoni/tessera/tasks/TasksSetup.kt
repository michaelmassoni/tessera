package io.github.michaelmassoni.tessera.tasks

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.core.dav.DavSetupPage
import io.github.michaelmassoni.tessera.core.dav.DavSetupTexts
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.tasks.data.Accounts
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Tasks' first run: notifications and exact alarms for reminders, sync, restore. */
internal val tasksSetup =
    SetupSpec(
        custom = listOf(SetupCustom { next -> SyncPage(next) }),
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
                SetupStep(
                    DesignR.drawable.ic_alarm,
                    R.string.setup_alarms_title,
                    R.string.setup_alarms_text,
                    SetupGrant.Special(
                        intent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${it.packageName}".toUri()) },
                        granted = { it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
                    ),
                    required = false,
                ),
            ),
        offerRestore = true,
    )

/** "Sync with a server?": keep tasks on the phone, or sign in and choose the server's lists. */
@Composable
private fun SyncPage(next: () -> Unit) {
    val context = LocalContext.current
    DavSetupPage(
        icon = DesignR.drawable.ic_checklist,
        texts =
            DavSetupTexts(
                intro = stringResource(R.string.setup_sync_text),
                keepLocal = stringResource(R.string.sync_local),
                servers = stringResource(R.string.sync_server_text),
                pickTitle = stringResource(R.string.setup_pick_title),
                pickText = stringResource(R.string.setup_pick_text),
                empty = stringResource(R.string.no_server_lists),
                use = stringResource(R.string.use_lists),
                notFound = stringResource(R.string.problem_not_caldav),
            ),
        signIn = { Accounts.signIn(context, it.address, it.username, it.password) },
        onUse = { Accounts.useLists(context, it) },
        next = next,
    )
}
