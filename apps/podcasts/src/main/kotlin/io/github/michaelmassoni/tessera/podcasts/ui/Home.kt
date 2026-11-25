package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.engine.Episodes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Home: Up Next (reorderable), then new episodes of followed shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: PodcastsState,
    actions: PodcastsActions,
    onOpenEpisode: (String) -> Unit,
    onOpenShow: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    // The queue after the episode loaded; moves show at once and reach the player on release.
    val queued = state.player.queue.drop(1)
    var order by remember { mutableStateOf(queued) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    LaunchedEffect(queued) { if (dragFrom < 0) order = queued }
    val newEpisodes = remember(state.library, state.episodes, state.now) { Episodes.new(state.library, state.episodes, state.now) }
    val listState = rememberLazyListState()
    val reorder =
        rememberReorderableLazyListState(listState) { from, to ->
            val f = order.indexOf(from.key)
            val t = order.indexOf(to.key)
            if (f >= 0 && t >= 0) order = order.toMutableList().apply { add(t, removeAt(f)) }
        }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_home)) },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (state.library.followed.isEmpty() && queued.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyMessage(
                        stringResource(R.string.home_empty_title),
                        stringResource(R.string.home_empty_text),
                        action = {
                            Button(
                                onClick = onSearch,
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(stringResource(R.string.find_podcasts)) }
                        },
                    )
                }
                return@PullToRefreshBox
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (order.isNotEmpty()) {
                    item(key = "up-next") { SectionTitle(stringResource(R.string.up_next)) }
                    items(order, key = { it }) { id ->
                        val episode = state.episode(id) ?: return@items
                        val show = state.show(episode.showId)
                        val i = order.indexOf(id)
                        ReorderableItem(reorder, key = id) {
                            EpisodeRow(
                                episode = episode,
                                state = state.state(id),
                                podcasts = state,
                                actions = actions,
                                onOpen = { onOpenEpisode(id) },
                                showTitle = show?.title.orEmpty(),
                                artworkUrl = episode.artworkUrl ?: show?.artworkUrl,
                                onGoToShow = { onOpenShow(episode.showId) },
                                showQueued = false,
                                modifier =
                                    Modifier.semantics {
                                        customActions =
                                            listOfNotNull(
                                                if (i > 0) {
                                                    CustomAccessibilityAction(moveUp) {
                                                        actions.moveInQueue(i + 1, i)
                                                        true
                                                    }
                                                } else {
                                                    null
                                                },
                                                if (i < order.lastIndex) {
                                                    CustomAccessibilityAction(moveDown) {
                                                        actions.moveInQueue(i + 1, i + 2)
                                                        true
                                                    }
                                                } else {
                                                    null
                                                },
                                            )
                                    },
                                trailing = {
                                    IconButton(
                                        onClick = {},
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStarted = { dragFrom = order.indexOf(id) },
                                                onDragStopped = {
                                                    val from = dragFrom
                                                    val to = order.indexOf(id)
                                                    dragFrom = -1
                                                    // +1: the queue's first entry is the episode loaded.
                                                    if (from >= 0 && to >= 0 && from != to) actions.moveInQueue(from + 1, to + 1)
                                                },
                                            ),
                                    ) {
                                        Icon(
                                            painterResource(DesignR.drawable.ic_drag_indicator),
                                            contentDescription = stringResource(R.string.reorder),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    item(key = "divider") { HorizontalDivider(Modifier.padding(top = 8.dp)) }
                }
                item(key = "new") { SectionTitle(stringResource(R.string.new_episodes)) }
                if (newEpisodes.isEmpty()) {
                    item(key = "no-new") {
                        Text(
                            stringResource(R.string.no_new_episodes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(newEpisodes, key = { "new-" + it.id }) { episode ->
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
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier =
            modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                .semantics { heading() },
    )
}
