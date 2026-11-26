package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * One row on the settings list and the page it opens. An app lists its own categories (see
 * [TesseraSettingsActivity.categories]); Appearance, Backup and About follow them in every app.
 *
 * Keep pages short and name categories the same way across apps: Sync, Downloads, Notifications,
 * Units, Playback…
 */
@Immutable
class SettingsCategory(
    /** Stable id, used to open the page directly (see [SettingsCategory.intent]). */
    val key: String,
    val title: String,
    @param:DrawableRes val icon: Int,
    /** The current values in a few words, shown under the title on the list. */
    val summary: String? = null,
    val content: @Composable ColumnScope.() -> Unit,
) {
    companion object {
        /** Intent extra naming the category to open (its [key]). */
        const val EXTRA = "io.github.michaelmassoni.tessera.settings.CATEGORY"

        const val APPEARANCE = "appearance"
        const val BACKUP = "backup"
        const val ABOUT = "about"

        /** Opens [activity] (an app's settings) straight on the category [key]. */
        fun intent(
            context: Context,
            activity: Class<*>,
            key: String,
        ): Intent = Intent(context, activity).putExtra(EXTRA, key)
    }
}

/**
 * A choice among a few options: one row showing the current value, which opens a dialog of
 * radio buttons. Use it instead of a column of [RadioSetting]s when there are more than three
 * options, or when a page has several choices.
 */
@Composable
fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    InfoSetting(title = title, summary = label(selected), onClick = { choosing = true }, modifier = modifier, external = false)
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(title) },
            text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    options.forEach { option ->
                        RadioSetting(
                            title = label(option),
                            selected = option == selected,
                            onSelect = {
                                choosing = false
                                if (option != selected) onSelect(option)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
