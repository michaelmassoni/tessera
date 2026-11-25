package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Library: a Downloaded row, then followed shows as covers, latest episode first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: PodcastsState,
    actions: PodcastsActions,
    onOpenShow: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val shows = state.library.followed.sortedByDescending { it.latestAt }
    val downloads = state.library.states.count { it.value.download != null }
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_library)) },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "downloads", span = { GridItemSpan(maxLineSpan) }) {
                ListItem(
                    supportingContent = { Text(episodeCount(downloads)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_download_done), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenDownloads, role = Role.Button),
                ) { Text(stringResource(R.string.downloaded)) }
            }
            if (shows.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyMessage(
                        stringResource(R.string.library_empty_title),
                        stringResource(R.string.library_empty_text),
                        action = {
                            androidx.compose.material3.Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.find_podcasts))
                            }
                        },
                    )
                }
            }
            items(shows, key = { it.id }) { show ->
                Column(
                    Modifier
                        .clickable(role = Role.Button) { onOpenShow(show.id) },
                ) {
                    Cover(show.artworkUrl, size = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(
                        show.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (show.author.isNotBlank()) {
                        Text(
                            show.author,
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

/** Downloaded episodes, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: PodcastsState,
    actions: PodcastsActions,
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes =
        state.library.states
            .filterValues { it.download != null }
            .keys
            .mapNotNull(state::episode)
            .sortedByDescending { it.publishedAt }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloaded)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        if (episodes.isEmpty()) {
            EmptyMessage(
                stringResource(R.string.downloads_empty_title),
                stringResource(R.string.downloads_empty_text),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            items(episodes, key = { it.id }) { episode ->
                val show = state.show(episode.showId)
                EpisodeRow(
                    episode = episode,
                    state = state.state(episode.id),
                    podcasts = state,
                    actions = actions,
                    onOpen = { onOpenEpisode(episode.id) },
                    showTitle = show?.title.orEmpty(),
                    artworkUrl = episode.artworkUrl ?: show?.artworkUrl,
                    onGoToShow = { onOpenShow(episode.showId) },
                )
            }
        }
    }
}

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
    }
}
