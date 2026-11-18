package io.github.michaelmassoni.tessera.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.contacts.data.ContactsRepository
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.ui.accountName
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val accounts by produceState(emptyList<AccountRef>()) {
            value =
                withContext(Dispatchers.IO) {
                    // Without contacts access (opened from system settings first) there is nothing to choose.
                    runCatching { ContactsRepository(contentResolver).accounts() }.getOrDefault(emptyList())
                }
        }
        if (accounts.size < 2) return
        val resources = LocalResources.current
        var selected by remember { mutableStateOf(defaultAccount ?: AccountRef.Device) }
        SettingsHeader(stringResource(R.string.settings_default_account))
        Column(Modifier.selectableGroup()) {
            accounts.forEach { account ->
                RadioSetting(
                    title = accountName(account, resources),
                    selected = account == selected,
                    onSelect = {
                        selected = account
                        defaultAccount = account
                    },
                )
            }
        }
    }
}
