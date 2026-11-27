package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme

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
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            label = label,
            onSelect = {
                choosing = false
                if (it != selected) onSelect(it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** [ChoiceSetting]'s dialog: radio rows; choosing one applies it and closes. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                // Plain rows on the dialog's own surface (not settings-page cards).
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.large)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            ).padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

// A four-option choice, as Music's streaming quality: plain rows on the dialog, in both themes.
@TesseraPreviews
@Composable
private fun ChoiceDialogPreview() {
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            ChoiceDialog(
                title = "Streaming quality",
                options = listOf("Original", "Up to 320 kbps", "Up to 192 kbps", "Up to 128 kbps"),
                selected = "Original",
                label = { it },
                onSelect = {},
                onDismiss = {},
            )
        }
    }
}
