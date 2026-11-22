package io.github.michaelmassoni.tessera.core.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Everything the settings screen can do; implemented by [TesseraSettingsActivity]. */
interface SettingsActions {
    fun setTheme(theme: ThemeSettings)

    fun setSyncAll(enabled: Boolean)

    fun setIconStyle(style: IconStyle)

    fun setCustomIcon(
        foreground: Int,
        background: Int,
    )

    fun pinCustomIcon()

    fun openSource()

    /** Saves this app's data to a file (the system file picker). */
    fun backUp()

    fun restore()

    /** One file with every Tessera app's data. */
    fun backUpAll()

    fun restoreAll()
}

/** Version and other facts shown in the About section. */
data class AboutInfo(
    val versionName: String,
)

/**
 * The settings screen shared by every Tessera app: the app's own sections first, then
 * Appearance, App icon and About. Stateless, so it is previewed and screenshot tested.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    shared: SharedState,
    icon: IconSettings,
    launcherIcon: LauncherIcon,
    customPinned: Boolean,
    about: AboutInfo,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    appSettings: @Composable ColumnScope.() -> Unit = {},
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                appSettings()
                AppearanceSection(shared, actions)
                AppIconSection(icon, launcherIcon, customPinned, actions)
                BackupSection(actions)
                AboutSection(about, actions)
            }
        }
    }
}

/** A section heading in the primary colour, like system Settings. */
@Composable
fun SettingsHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** A row with a title, optional summary and a switch; the whole row toggles. */
@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    ListItem(
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
    ) { Text(title) }
}

/** One option in a list of choices; use inside a `Column(Modifier.selectableGroup())`. */
@Composable
fun RadioSetting(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) { Text(title) }
}

/** A row that shows a value and does something when tapped (or nothing, when [onClick] is null). */
@Composable
fun InfoSetting(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Shows an "opens elsewhere" icon (a browser, another app); off for actions in place. */
    external: Boolean = true,
) {
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            supportingContent = { Text(summary) },
            trailingContent =
                if (external) {
                    (
                        {
                            Icon(
                                painterResource(DesignR.drawable.ic_open_in_new),
                                contentDescription = null,
                            )
                        }
                    )
                } else {
                    null
                },
            modifier = modifier,
        ) { Text(title) }
    } else {
        ListItem(supportingContent = { Text(summary) }, modifier = modifier) { Text(title) }
    }
}

@Composable
private fun AppearanceSection(
    shared: SharedState,
    actions: SettingsActions,
) {
    Column {
        val theme = shared.theme
        SettingsHeader(stringResource(R.string.settings_appearance))
        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val modes =
            listOf(
                Triple(ThemeMode.System, R.string.settings_theme_system, DesignR.drawable.ic_brightness_auto),
                Triple(ThemeMode.Light, R.string.settings_theme_light, DesignR.drawable.ic_light_mode),
                Triple(ThemeMode.Dark, R.string.settings_theme_dark, DesignR.drawable.ic_dark_mode),
            )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            modes.forEachIndexed { i, (mode, label, iconRes) ->
                SegmentedButton(
                    selected = theme.mode == mode,
                    onClick = { actions.setTheme(theme.copy(mode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    icon = { Icon(painterResource(iconRes), contentDescription = null) },
                ) { Text(stringResource(label)) }
            }
        }
        SwitchSetting(
            title = stringResource(R.string.settings_dynamic_color),
            summary = stringResource(R.string.settings_dynamic_color_summary),
            checked = theme.dynamicColor,
            onCheckedChange = { actions.setTheme(theme.copy(dynamicColor = it)) },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_pure_black),
            summary = stringResource(R.string.settings_pure_black_summary),
            checked = theme.pureBlack,
            onCheckedChange = { actions.setTheme(theme.copy(pureBlack = it)) },
        )
        SwitchSetting(
            title = stringResource(R.string.settings_sync_all),
            summary = stringResource(R.string.settings_sync_all_summary),
            checked = shared.syncAll,
            onCheckedChange = actions::setSyncAll,
        )
    }
}

@Composable
private fun AppIconSection(
    icon: IconSettings,
    launcherIcon: LauncherIcon,
    customPinned: Boolean,
    actions: SettingsActions,
) {
    Column {
        var picking by rememberSaveable { mutableStateOf(false) }
        SettingsHeader(stringResource(R.string.settings_app_icon))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconStyle.entries.forEach { style ->
                val (fg, bg) =
                    if (style == IconStyle.Custom) {
                        icon.foreground to icon.background
                    } else {
                        val (bgRes, fgRes) = launcherIcon.colors.getValue(style)
                        colorResource(fgRes).toArgb() to colorResource(bgRes).toArgb()
                    }
                IconChoice(
                    label = stringResource(style.label()),
                    selected = icon.style == style,
                    onSelect = { actions.setIconStyle(style) },
                ) { IconPreview(launcherIcon.glyph, fg, bg) }
            }
        }
        Text(
            stringResource(if (icon.style == IconStyle.Custom) R.string.settings_icon_custom_note else R.string.settings_icon_builtin_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (icon.style == IconStyle.Custom) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.settings_icon_choose_colours)) }
                FilledTonalButton(onClick = actions::pinCustomIcon) {
                    Text(stringResource(if (customPinned) R.string.settings_icon_update else R.string.settings_icon_add))
                }
            }
        }
        if (picking) {
            IconColorDialog(
                glyph = launcherIcon.glyph,
                initial = icon,
                onApply = { fg, bg ->
                    picking = false
                    actions.setCustomIcon(fg, bg)
                },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
private fun IconChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        preview()
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        RadioButton(selected = selected, onClick = null)
    }
}

private fun IconStyle.label() =
    when (this) {
        IconStyle.Default -> R.string.settings_icon_default
        IconStyle.Light -> R.string.settings_icon_light
        IconStyle.Dark -> R.string.settings_icon_dark
        IconStyle.Custom -> R.string.settings_icon_custom
    }

@Composable
private fun BackupSection(actions: SettingsActions) {
    Column {
        SettingsHeader(stringResource(R.string.settings_backup))
        InfoSetting(
            stringResource(R.string.backup_this),
            stringResource(R.string.backup_this_summary),
            onClick = actions::backUp,
            external = false,
        )
        InfoSetting(
            stringResource(R.string.restore_this),
            stringResource(R.string.restore_this_summary),
            onClick = actions::restore,
            external = false,
        )
        InfoSetting(
            stringResource(R.string.backup_all),
            stringResource(R.string.backup_all_summary),
            onClick = actions::backUpAll,
            external = false,
        )
        InfoSetting(
            stringResource(R.string.restore_all),
            stringResource(R.string.restore_all_summary),
            onClick = actions::restoreAll,
            external = false,
        )
    }
}

@Composable
private fun AboutSection(
    about: AboutInfo,
    actions: SettingsActions,
) {
    Column {
        SettingsHeader(stringResource(R.string.settings_about))
        InfoSetting(stringResource(R.string.settings_version), about.versionName)
        InfoSetting(
            stringResource(R.string.settings_source),
            stringResource(R.string.settings_source_summary),
            onClick = actions::openSource,
        )
        InfoSetting(stringResource(R.string.settings_licence), stringResource(R.string.settings_licence_summary))
    }
}

private object PreviewActions : SettingsActions {
    override fun backUp() = Unit

    override fun restore() = Unit

    override fun backUpAll() = Unit

    override fun restoreAll() = Unit

    override fun setTheme(theme: ThemeSettings) = Unit

    override fun setSyncAll(enabled: Boolean) = Unit

    override fun setIconStyle(style: IconStyle) = Unit

    override fun setCustomIcon(
        foreground: Int,
        background: Int,
    ) = Unit

    override fun pinCustomIcon() = Unit

    override fun openSource() = Unit
}

private val previewIcon =
    LauncherIcon.of(
        glyph = DesignR.drawable.ic_palette,
        defaultBackground = android.R.color.holo_blue_dark,
        lightBackground = android.R.color.white,
        lightForeground = android.R.color.holo_blue_dark,
        darkBackground = android.R.color.black,
        darkForeground = android.R.color.holo_blue_light,
    )

@TesseraPreviews
@Composable
private fun SettingsScreenPreview() {
    TesseraTheme(dynamicColor = false) {
        SettingsScreen(
            shared = SharedState(syncAll = true),
            icon = IconSettings(style = IconStyle.Custom, foreground = 0xFF1B1B1F.toInt(), background = 0xFFFFD8A8.toInt()),
            launcherIcon = previewIcon,
            customPinned = false,
            about = AboutInfo("0.2.0"),
            actions = PreviewActions,
            onBack = {},
        ) {
            SettingsHeader("App")
            SwitchSetting("An app setting", checked = true, onCheckedChange = {}, summary = "What it does")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IconColorDialogPreview() {
    TesseraTheme(dynamicColor = false) {
        IconColorDialog(glyph = DesignR.drawable.ic_palette, initial = IconSettings(), onApply = { _, _ -> }, onDismiss = {})
    }
}
