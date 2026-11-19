package io.github.michaelmassoni.tessera.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.core.design.R as DesignR

@Composable
internal fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    group: Boolean = false,
) {
    Box(modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        if (group) {
            Icon(painterResource(DesignR.drawable.ic_group), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            // A name's first letter; a person icon for a number with no contact (not its first digit).
            val letter = name.firstOrNull { it.isLetterOrDigit() }?.takeIf { it.isLetter() }
            if (letter != null) {
                Text(letter.uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                Icon(
                    painterResource(DesignR.drawable.ic_person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** A tinted card with a message and one action, above the conversation list. */
@Composable
internal fun Notice(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

/** Shown for actions Android only allows the default SMS app. */
@Composable
internal fun NeedsDefaultDialog(
    onDismiss: () -> Unit,
    onMakeDefault: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.needs_default_title)) },
        text = { Text(stringResource(R.string.needs_default_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onMakeDefault()
            }) { Text(stringResource(R.string.set_default)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String?,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
