package io.github.michaelmassoni.tessera.sports.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.sports.Load
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.SportsState
import io.github.michaelmassoni.tessera.sports.TeamPage
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.Team
import java.time.ZoneId
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** A team: its record and standing, next games, results (newest first) and roster. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    team: Team,
    load: Load<TeamPage>?,
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val followed = team.key in state.follows.teamKeys
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(team.name) },
                navigationIcon = {
                    IconButton(onClick = actions::closeTeam) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { actions.setTeam(team, !followed) }) {
                        Icon(
                            painterResource(if (followed) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                            contentDescription = stringResource(if (followed) R.string.unfollow_team else R.string.follow_team),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                    TeamLogo(team, 88.dp)
                    val info = (load as? Load.Ready)?.value?.info
                    listOfNotNull(info?.record, info?.standing).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item(key = "body") {
                LoadView(load, onRetry = { actions.openTeam(team) }) { page ->
                    val upcoming = page.games.filter { it.state == GameState.Scheduled || it.state == GameState.Live }.sortedBy { it.start }
                    val results = page.games.filter { it.state == GameState.Final }.sortedByDescending { it.start }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (upcoming.isNotEmpty()) {
                            SectionHeading(stringResource(R.string.team_next))
                            upcoming.take(NEXT).forEach { game ->
                                val hidden = state.hideScores && game.id !in state.revealed
                                GameCard(
                                    game,
                                    now,
                                    zone,
                                    onClick = { if (hidden && game.state == GameState.Live) actions.reveal(game) else actions.open(game) },
                                    followed = state.follows.teamKeys,
                                    hidden = hidden,
                                )
                            }
                        }
                        if (results.isNotEmpty()) {
                            SectionHeading(stringResource(R.string.team_results))
                            results.take(RESULTS).forEach { game ->
                                val hidden = state.hideScores && game.id !in state.revealed
                                GameCard(
                                    game,
                                    now,
                                    zone,
                                    onClick = { if (hidden) actions.reveal(game) else actions.open(game) },
                                    followed = state.follows.teamKeys,
                                    hidden = hidden,
                                )
                            }
                        }
                        if (upcoming.isEmpty() && results.isEmpty()) Message(stringResource(R.string.team_no_games))
                        val roster = page.info?.roster.orEmpty()
                        if (roster.isNotEmpty()) {
                            SectionHeading(stringResource(R.string.team_roster))
                            SectionCard(null) {
                                roster.forEach { p ->
                                    ListItem(
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        leadingContent = {
                                            Text(
                                                p.jersey?.let { "#$it" }.orEmpty(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.widthIn(min = 32.dp),
                                            )
                                        },
                                        trailingContent = p.position?.let { { Text(it, style = MaterialTheme.typography.labelLarge) } },
                                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                                    ) { Text(p.name) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val NEXT = 5
private const val RESULTS = 10
