package io.github.michaelmassoni.tessera.notes

import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.notes.engine.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened from system Settings, the notes may not be loaded yet.
        lifecycleScope.launch(Dispatchers.IO) { if (!notes.loaded.value) notes.load() }
    }

    @Composable
    override fun categories(): List<SettingsCategory> {
        var checked by remember { mutableStateOf(checkedLast) }
        return listOf(
            SettingsCategory(
                key = "editing",
                title = stringResource(R.string.settings_editing),
                icon = DesignR.drawable.ic_edit,
                summary = stringResource(if (checked) R.string.settings_checked_last_on else R.string.settings_checked_last_off),
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_checked_last),
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        checkedLast = it
                    },
                )
            },
        )
    }

    override val importExport: @Composable ColumnScope.() -> Unit = {
        val scope = rememberCoroutineScope()
        val export =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch { toast(writeExport(uri)) }
            }
        val import =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch { toast(readImport(uri)) }
            }
        InfoSetting(
            title = stringResource(R.string.settings_export),
            summary = stringResource(R.string.settings_export_summary),
            onClick = { export.launch("tessera-notes-${LocalDate.now()}.json") },
            external = false,
        )
        InfoSetting(
            title = stringResource(R.string.settings_import),
            summary = stringResource(R.string.settings_import_summary),
            onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            external = false,
        )
    }

    private suspend fun writeExport(uri: android.net.Uri): String =
        withContext(Dispatchers.IO) {
            try {
                val text =
                    Backup.encode(
                        notes.notes.value.values
                            .filterNot { it.isTrashed },
                    )
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw IOException()
                getString(R.string.exported)
            } catch (_: IOException) {
                getString(R.string.export_failed)
            }
        }

    private suspend fun readImport(uri: android.net.Uri): String =
        withContext(Dispatchers.IO) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: throw IOException()
                val imported = Backup.decode(text, notes::newId)
                notes.saveAll(imported)
                resources.getQuantityString(R.plurals.imported, imported.size, imported.size)
            } catch (_: IOException) {
                getString(R.string.import_failed)
            } catch (_: IllegalArgumentException) {
                getString(R.string.import_failed)
            }
        }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
