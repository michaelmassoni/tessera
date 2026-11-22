package io.github.michaelmassoni.tessera.gallery.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.gallery.R
import io.github.michaelmassoni.tessera.gallery.engine.Album
import io.github.michaelmassoni.tessera.gallery.engine.AlbumTarget
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Picks the album (folder) to move or copy to: New album first, then every album. [onPick] gets
 * the folder's relative path, such as "Pictures/Trip/".
 */
@Composable
fun AlbumPicker(
    albums: List<Album>,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        val name = rememberTextFieldState()
        val path = AlbumTarget.newAlbum(name.text.toString())
        AlertDialog(
            modifier = modifier,
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.new_album)) },
            text = {
                OutlinedTextField(
                    state = name,
                    label = { Text(stringResource(R.string.album_name)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { path?.let(onPick) },
                    enabled = path != null,
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "new") {
                    ListItem(
                        onClick = { naming = true },
                        colors = clear,
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null) },
                    ) { Text(stringResource(R.string.new_album)) }
                }
                // Not files at the top of storage: they have no album name.
                items(albums.filter { it.name.isNotBlank() && it.cover.relativePath.isNotBlank() }, key = { it.id }) { album ->
                    ListItem(
                        onClick = { onPick(album.cover.relativePath) },
                        colors = clear,
                        leadingContent = { Icon(painterResource(DesignR.drawable.ic_folder), contentDescription = null) },
                        supportingContent = { Text(pluralStringResource(R.plurals.item_count, album.count, album.count)) },
                    ) { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
