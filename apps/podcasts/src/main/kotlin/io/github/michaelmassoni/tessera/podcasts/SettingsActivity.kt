package io.github.michaelmassoni.tessera.podcasts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.podcasts.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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
    override fun ColumnScope.AppSettings() {
        val context = LocalContext.current
        val resources = LocalResources.current
        val scope = rememberCoroutineScope()
        var auto by remember { mutableStateOf(Prefs.autoDownload(context)) }
        var deletePlayed by remember { mutableStateOf(Prefs.deletePlayed(context)) }
        var mobile by remember { mutableStateOf(Prefs.mobileDownloads(context)) }
        var importing by remember { mutableStateOf(false) }

        val importer =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                importing = true
                scope.launch {
                    val added =
                        try {
                            context.contentResolver.openInputStream(uri)?.use { context.podcasts.importOpml(it) }
                        } catch (_: IOException) {
                            null
                        }
                    importing = false
                    val message =
                        if (added == null) {
                            resources.getString(R.string.import_failed)
                        } else {
                            resources.getQuantityString(R.plurals.imported, added, added)
                        }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        val exporter =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver
                                    .openOutputStream(
                                        uri,
                                    )?.use { it.write(context.podcasts.exportOpml().toByteArray()) } !=
                                    null
                            } catch (_: IOException) {
                                false
                            }
                        }
                    Toast.makeText(context, if (ok) R.string.exported else R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }

        SettingsHeader(stringResource(R.string.settings_downloads))
        SwitchSetting(
            title = stringResource(R.string.settings_auto_download),
            summary = stringResource(R.string.settings_auto_download_summary),
            checked = auto,
            onCheckedChange = {
                auto = it
                Prefs.setAutoDownload(context, it)
            },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_delete_played),
            summary = stringResource(R.string.settings_delete_played_summary),
            checked = deletePlayed,
            onCheckedChange = {
                deletePlayed = it
                Prefs.setDeletePlayed(context, it)
            },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_mobile_downloads),
            summary = stringResource(R.string.settings_mobile_downloads_summary),
            checked = mobile,
            onCheckedChange = {
                mobile = it
                Prefs.setMobileDownloads(context, it)
            },
        )

        SettingsHeader(stringResource(R.string.settings_subscriptions))
        InfoSetting(
            stringResource(R.string.settings_import),
            stringResource(if (importing) R.string.importing else R.string.settings_import_summary),
            onClick = { if (!importing) importer.launch(arrayOf("*/*")) },
            external = false,
        )
        InfoSetting(
            stringResource(R.string.settings_export),
            stringResource(R.string.settings_export_summary),
            onClick = { exporter.launch("podcasts.opml") },
            external = false,
        )
    }
}
