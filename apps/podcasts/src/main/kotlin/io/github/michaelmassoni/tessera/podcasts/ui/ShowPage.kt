package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A show: cover, title, description, Follow, and its episodes newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(
    showId: String,
    state: PodcastsState,
    actions: PodcastsActions,
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = state.show(showId)
    val episodes = state.episodes[showId].orEmpty()
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmUnfollow by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (scroll.state.contentOffset < -200f) Text(show?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        if (show == null) return@Scaffold
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Cover(show.artworkUrl, 180.dp)
                    Text(
                        show.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                    )
                    if (show.author.isNotBlank()) {
                        Text(show.author, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (show.followed) {
                            FilledTonalButton(onClick = { confirmUnfollow = true }) {
                                Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null)
                                Text(stringResource(R.string.following), Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Button(onClick = { actions.follow(showId) }) {
                                Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null)
                                Text(stringResource(R.string.follow), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    if (show.description.isNotBlank()) {
                        Text(
                            show.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clickable(onClickLabel = stringResource(if (expanded) R.string.show_less else R.string.show_more)) {
                                        expanded = !expanded
                                    },
                        )
                    }
                    Text(
                        episodeCount(episodes.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
            }
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    state = state.state(episode.id),
                    podcasts = state,
                    actions = actions,
                    onOpen = { onOpenEpisode(episode.id) },
                )
            }
        }
    }
    if (confirmUnfollow && show != null) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text(stringResource(R.string.unfollow_title, show.title)) },
            text = { Text(stringResource(R.string.unfollow_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfollow = false
                    actions.unfollow(showId)
                }) { Text(stringResource(R.string.unfollow)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
