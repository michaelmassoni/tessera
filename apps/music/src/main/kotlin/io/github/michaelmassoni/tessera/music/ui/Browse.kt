package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.Album
import io.github.michaelmassoni.tessera.music.engine.Artist
import io.github.michaelmassoni.tessera.music.engine.SortOrder
import io.github.michaelmassoni.tessera.core.design.R as DesignR

// The shared-transition scope and the page's animation, so album art can fly between pages. Locals,
// as the shared-element docs recommend: otherwise both scopes would pass through every list and page.
@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("ComposeCompositionLocalUsage")
internal val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

@Suppress("ComposeCompositionLocalUsage")
internal val LocalPageScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Art that moves from where it was tapped (a grid or list) to the page it opens, and back. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedArt(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    return with(shared) { this@sharedArt.sharedElement(rememberSharedContentState(key), page) }
}

@Composable
private fun SortOrder.label(): String =
    stringResource(
        when (this) {
            SortOrder.Name -> R.string.sort_name
            SortOrder.Artist -> R.string.sort_artist
            SortOrder.Album -> R.string.sort_album
            SortOrder.Year -> R.string.sort_year
            SortOrder.RecentlyAdded -> R.string.sort_added
            SortOrder.RecentlyPlayed -> R.string.sort_played
        },
    )

/**
 * Above a library page: how it's sorted (a chip with a menu), anything else the page offers
 * ([extra]), and, where there's a choice, grid or list.
 */
@Composable
internal fun SortBar(
    orders: List<SortOrder>,
    order: SortOrder,
    onOrder: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
    grid: Boolean? = null,
    onGrid: (Boolean) -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            AssistChip(
                onClick = { menu = true },
                label = { Text(order.label()) },
                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_sort), contentDescription = stringResource(R.string.sort_by)) },
                trailingIcon = { Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = null) },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                orders.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label()) },
                        onClick = {
                            menu = false
                            onOrder(o)
                        },
                        trailingIcon =
                            if (o ==
                                order
                            ) {
                                ({ Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) })
                            } else {
                                null
                            },
                    )
                }
            }
        }
        extra()
        Spacer(Modifier.weight(1f))
        if (grid != null) {
            IconButton(onClick = { onGrid(!grid) }) {
                Icon(
                    painterResource(if (grid) DesignR.drawable.ic_view_list else DesignR.drawable.ic_grid_view),
                    contentDescription = stringResource(if (grid) R.string.show_as_list else R.string.show_as_grid),
                )
            }
        }
    }
}

/** "Album artists" on the Artists page: each album under the artist it's credited to. */
@Composable
internal fun AlbumArtistsChip(
    selected: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(stringResource(R.string.album_artists)) },
        leadingIcon = if (selected) ({ Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null) }) else null,
    )
}

@Composable
internal fun AlbumGrid(
    albums: List<Album>,
    onAlbum: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Surface(onClick = { onAlbum(album.id) }, shape = MaterialTheme.shapes.medium) {
                Column {
                    AlbumArt(album.id, size = 160.dp, modifier = Modifier.fillMaxWidth().aspectRatio(1f).sharedArt("album-${album.id}"))
                    // Inset from the card's rounded corners, which would otherwise clip the last line's first letter.
                    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            album.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            album.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumList(
    albums: List<Album>,
    onAlbum: (Long) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(albums, key = { it.id }) { album ->
            ListItem(
                onClick = { onAlbum(album.id) },
                leadingContent = { AlbumArt(album.id, size = 56.dp, modifier = Modifier.sharedArt("album-${album.id}")) },
                supportingContent = {
                    Text(
                        listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun artistSummary(artist: Artist) =
    pluralStringResource(R.plurals.album_count, artist.albums.size, artist.albums.size) + " · " +
        pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size)

@Composable
internal fun ArtistList(
    artists: List<Artist>,
    onArtist: (Long) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                onClick = { onArtist(artist.id) },
                leadingContent = {
                    AlbumArt(
                        artist.albums.firstOrNull()?.id ?: 0,
                        modifier = Modifier.clip(CircleShape).sharedArt("artist-${artist.id}"),
                    )
                },
                supportingContent = { Text(artistSummary(artist)) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(artist.name.ifBlank { stringResource(R.string.unknown_artist) }) }
        }
    }
}

@Composable
internal fun ArtistGrid(
    artists: List<Artist>,
    onArtist: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Surface(onClick = { onArtist(artist.id) }, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(
                        artist.albums.firstOrNull()?.id ?: 0,
                        size = 140.dp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .sharedArt("artist-${artist.id}"),
                    )
                    Text(
                        artist.name.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        artistSummary(artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
