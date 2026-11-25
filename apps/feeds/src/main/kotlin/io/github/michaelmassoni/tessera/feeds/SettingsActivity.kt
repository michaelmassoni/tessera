package io.github.michaelmassoni.tessera.feeds

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.core.dav.SyncInterval
import io.github.michaelmassoni.tessera.core.dav.SyncPrefs
import io.github.michaelmassoni.tessera.core.dav.SyncSettings
import io.github.michaelmassoni.tessera.core.dav.label
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.feeds.data.AccountStore
import io.github.michaelmassoni.tessera.feeds.data.FeedsPrefs
import io.github.michaelmassoni.tessera.feeds.data.Source
import io.github.michaelmassoni.tessera.feeds.data.Subscriptions
import io.github.michaelmassoni.tessera.feeds.sync.SyncWorker
import io.github.michaelmassoni.tessera.feeds.ui.YarrSignInDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon =
        LauncherIcon.of(
            glyph = R.drawable.ic_launcher_foreground,
            defaultBackground = R.color.ic_launcher_background,
            lightBackground = R.color.ic_launcher_light_background,
            lightForeground = R.color.ic_launcher_light_foreground,
            darkBackground = R.color.ic_launcher_dark_background,
            darkForeground = R.color.ic_launcher_dark_foreground,
        )

    @Composable
    override fun categories(): List<SettingsCategory> {
        val context = LocalContext.current
        val store = remember { AccountStore(context) }
        val subscriptions = remember { Subscriptions(context) }
        var source by remember { mutableStateOf(store.source) }
        var prefs by remember { mutableStateOf(SyncPrefs.load(context)) }
        var keep by remember { mutableIntStateOf(FeedsPrefs.keepReadDays(context)) }
        var scale by remember { mutableFloatStateOf(FeedsPrefs.textScale(context)) }
        val sourceLabel = stringResource(if (source is Source.Yarr) R.string.source_yarr else R.string.source_phone)
        val interval =
            when (prefs.interval) {
                SyncInterval.Manual -> stringResource(R.string.settings_sync_manual)
                SyncInterval.Day -> prefs.interval.label()
                else -> stringResource(R.string.settings_sync_every, prefs.interval.label())
            }
        return listOf(
            SettingsCategory(
                key = "sync",
                title = stringResource(R.string.settings_source_sync),
                icon = DesignR.drawable.ic_sync,
                summary = "$sourceLabel · $interval",
            ) {
                SourceAndSync(
                    store = store,
                    subscriptions = subscriptions,
                    source = source,
                    onSourceChange = { source = it },
                    prefs = prefs,
                    onPrefsChange = { prefs = it },
                )
            },
            SettingsCategory(
                key = "reading",
                title = stringResource(R.string.settings_reading),
                icon = DesignR.drawable.ic_article,
                summary = "${pluralStringResource(R.plurals.days, keep, keep)} · ${scaleLabel(scale)}",
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.keep_read),
                    options = FeedsPrefs.KEEP_CHOICES,
                    selected = keep,
                    label = { pluralStringResource(R.plurals.days, it, it) },
                    onSelect = {
                        keep = it
                        FeedsPrefs.setKeepReadDays(context, it)
                    },
                )
                ChoiceSetting(
                    title = stringResource(R.string.text_size),
                    options = FeedsPrefs.TEXT_SCALES,
                    selected = scale,
                    label = { scaleLabel(it) },
                    onSelect = {
                        scale = it
                        FeedsPrefs.setTextScale(context, it)
                    },
                )
            },
        )
    }

    @Composable
    private fun SourceAndSync(
        store: AccountStore,
        subscriptions: Subscriptions,
        source: Source,
        onSourceChange: (Source) -> Unit,
        prefs: SyncPrefs,
        onPrefsChange: (SyncPrefs) -> Unit,
    ) {
        val context = LocalContext.current
        var choosingSource by rememberSaveable { mutableStateOf(false) }
        var signingIn by rememberSaveable { mutableStateOf(false) }
        var confirmPhone by rememberSaveable { mutableStateOf(false) }
        val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncWorker.schedule.soonName) }
            .collectAsStateWithLifecycle(emptyList())
        val syncing = work.any { it.state == WorkInfo.State.RUNNING }

        Column {
            InfoSetting(
                title = stringResource(if (source is Source.Yarr) R.string.source_yarr else R.string.source_phone),
                summary =
                    when (source) {
                        is Source.Yarr -> {
                            source.username?.let { stringResource(R.string.source_yarr_user, it, source.address) }
                                ?: source.address
                        }

                        Source.Phone -> {
                            stringResource(R.string.source_phone_summary)
                        }
                    },
                onClick = { choosingSource = true },
                external = false,
            )
            SyncSettings(
                prefs = prefs,
                onChange = {
                    onPrefsChange(it)
                    SyncPrefs.save(context, it)
                    SyncWorker.schedule.apply(context)
                },
                lastSync = remember(work) { store.lastSync },
                lastProblem = remember(work) { store.lastProblem },
                syncing = syncing,
                onSyncNow = { SyncWorker.schedule.soon(context, delaySeconds = 0) },
            )
        }

        if (choosingSource) {
            AlertDialog(
                onDismissRequest = { choosingSource = false },
                title = { Text(stringResource(R.string.settings_source)) },
                text = {
                    Column {
                        RadioSetting(stringResource(R.string.source_yarr), selected = source is Source.Yarr, onSelect = {
                            choosingSource = false
                            signingIn = true
                        })
                        RadioSetting(stringResource(R.string.source_phone), selected = source == Source.Phone, onSelect = {
                            choosingSource = false
                            if (source != Source.Phone) confirmPhone = true
                        })
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { choosingSource = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (signingIn) {
            YarrSignInDialog(
                signIn = { subscriptions.useYarr(it.address, it.username, it.password) },
                onDismiss = { signingIn = false },
                onSignedIn = {
                    signingIn = false
                    onSourceChange(store.source)
                },
            )
        }
        if (confirmPhone) {
            AlertDialog(
                onDismissRequest = { confirmPhone = false },
                title = { Text(stringResource(R.string.use_phone_title)) },
                text = { Text(stringResource(R.string.use_phone_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPhone = false
                        subscriptions.usePhone()
                        onSourceChange(store.source)
                    }) { Text(stringResource(R.string.use_phone)) }
                },
                dismissButton = { TextButton(onClick = { confirmPhone = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    override val importExport: @Composable ColumnScope.() -> Unit = {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val subscriptions = remember { Subscriptions(context) }
        val importFailed = stringResource(R.string.import_failed)
        val exportFailed = stringResource(R.string.export_failed)
        val res = LocalResources.current

        val importer =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val text =
                        withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
                        }
                    val n = text?.let { subscriptions.importOpml(it) }
                    val message = if (n == null) importFailed else res.getQuantityString(R.plurals.imported, n, n)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        val exporter =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openOutputStream(uri)?.use { it.write(subscriptions.exportOpml().toByteArray()) }
                            }.isSuccess
                        }
                    if (!ok) Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show()
                }
            }

        InfoSetting(
            stringResource(R.string.import_opml),
            stringResource(R.string.import_opml_summary),
            onClick = { importer.launch(arrayOf("*/*")) },
            external = false,
        )
        InfoSetting(
            stringResource(R.string.export_opml),
            stringResource(R.string.export_opml_summary),
            onClick = { exporter.launch("feeds.opml") },
            external = false,
        )
    }

    @Composable
    private fun scaleLabel(scale: Float): String =
        stringResource(
            when {
                scale < 1f -> R.string.text_small
                scale == 1f -> R.string.text_default
                scale < 1.2f -> R.string.text_large
                else -> R.string.text_largest
            },
        )
}
