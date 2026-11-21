package io.github.michaelmassoni.tessera.sports.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.sports.Load
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.SportsState
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameDetail
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Scores
import io.github.michaelmassoni.tessera.sports.engine.Section
import io.github.michaelmassoni.tessera.sports.engine.StandingsGroup
import io.github.michaelmassoni.tessera.sports.engine.Team
import java.time.ZoneId
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface SportsActions {
    fun refresh()

    fun loadLeague(
        league: League,
        force: Boolean = false,
    )

    fun open(game: Game)

    fun closeDetail()

    fun loadTeams(league: League)

    fun setLeague(
        league: League,
        on: Boolean,
    )

    fun setTeam(
        team: Team,
        on: Boolean,
    )

    /** Leaving the team picker (asks for notifications if Live Updates can be used). */
    fun doneFollowing()

    fun openSettings()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SportsApp(
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // The picker stays open until Done (on first run too, so following a league doesn't close it).
    var editing by rememberSaveable(state.loaded) { mutableStateOf(state.loaded && state.follows.leagues.isEmpty()) }
    when {
        !state.loaded -> {
            Box(
                modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator() }
        }

        editing || state.follows.leagues.isEmpty() -> {
            FollowScreen(
                state = state,
                actions = actions,
                onDone = {
                    editing = false
                    actions.doneFollowing()
                },
                canGoBack = state.follows.leagues.isNotEmpty(),
                modifier = modifier,
            )
        }

        else -> {
            MainScreen(state, actions, now, zone, snackbar, onEdit = { editing = true }, modifier = modifier)
        }
    }
    when (val detail = state.detail) {
        null -> Unit
        else -> GameSheet(detail, state, actions, now, zone)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainScreen(
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
    snackbar: SnackbarHostState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leagues = state.follows.leagues()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val league = leagues.getOrNull(tab - 1)
    LaunchedEffect(league) { league?.let { actions.loadLeague(it) } }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onEdit) {
                            Icon(painterResource(DesignR.drawable.ic_edit), contentDescription = stringResource(R.string.edit_teams))
                        }
                        IconButton(onClick = actions::openSettings) {
                            Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab.coerceAtMost(leagues.size), edgePadding = 16.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_scores)) })
                    leagues.forEachIndexed { i, l ->
                        Tab(selected = tab == i + 1, onClick = { tab = i + 1 }, text = { Text(stringResource(l.label())) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (league == null) {
                ScoresTab(state, actions, now, zone)
            } else {
                LeagueTab(league, state, actions, now, zone)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoresTab(
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
) {
    val sections = Scores.sections(state.games, now, zone)
    val followed = state.follows.teamKeys
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (sections.isEmpty() && state.fetchedAt > 0) {
                item(key = "empty") { Message(stringResource(R.string.no_games)) }
            }
            sections.forEach { section ->
                item(key = "h${section.section}") { SectionHeading(stringResource(section.section.label())) }
                items(section.games, key = { "${it.league.id}:${it.id}" }) { game ->
                    GameCard(game, now, zone, onClick = { actions.open(game) }, followed = followed)
                }
            }
            item(key = "credit") {
                Text(
                    stringResource(R.string.credit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun Section.label(): Int =
    when (this) {
        Section.Live -> R.string.section_live
        Section.Today -> R.string.section_today
        Section.Upcoming -> R.string.section_upcoming
        Section.Recent -> R.string.section_recent
    }

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(32.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> LoadView(
    load: Load<T>?,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (load) {
        null, Load.Loading -> {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }

        Load.Failed -> {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.load_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }

        is Load.Ready -> {
            content(load.value)
        }
    }
}

@Composable
private fun LeagueTab(
    league: League,
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
) {
    var standings by rememberSaveable(league) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "switch") {
            SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                SegmentedButton(selected = !standings, onClick = { standings = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text(stringResource(R.string.games))
                }
                SegmentedButton(selected = standings, onClick = { standings = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text(stringResource(R.string.standings))
                }
            }
        }
        if (standings) {
            item(key = "standings") {
                LoadView(state.standings[league], onRetry = { actions.loadLeague(league, force = true) }) { groups ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        groups.forEach { StandingsTable(league, it, state.follows.teamKeys) }
                    }
                }
            }
        } else {
            item(key = "games") {
                LoadView(state.leagueGames[league], onRetry = { actions.loadLeague(league, force = true) }) { games ->
                    val sections = Scores.sections(games, now, zone).filter { it.section != Section.Recent }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (sections.isEmpty()) Message(stringResource(R.string.no_league_games))
                        sections.forEach { section ->
                            SectionHeading(stringResource(section.section.label()))
                            section.games.forEach { game ->
                                GameCard(game, now, zone, onClick = { actions.open(game) }, followed = state.follows.teamKeys)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A conference, division or whole league: position, team and the league's columns. */
@Composable
private fun StandingsTable(
    league: League,
    group: StandingsGroup,
    followed: Set<String>,
) {
    SectionCard(group.name.ifBlank { null }) {
        val scroll = rememberScrollState()
        Column(Modifier.horizontalScroll(scroll)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(24.dp + 28.dp))
                Text(
                    stringResource(R.string.team_column),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(100.dp),
                )
                league.columns.forEach {
                    Text(
                        stringResource(it.header.label()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
            }
            group.rows.forEach { row ->
                val mine = row.team.key in followed
                Row(
                    Modifier
                        .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.position.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
                    TeamLogo(row.team, 20.dp, Modifier.padding(end = 8.dp))
                    Text(
                        row.team.shortName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(100.dp),
                    )
                    row.values.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
    }
}

/** Pick leagues, and teams within them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowScreen(
    state: SportsState,
    actions: SportsActions,
    onDone: () -> Unit,
    canGoBack: Boolean,
    modifier: Modifier = Modifier,
) {
    if (canGoBack) BackHandler(onBack = onDone)
    val follows = state.follows
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.follow_title)) },
                actions = {
                    Button(onClick = onDone, enabled = follows.leagues.isNotEmpty(), modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.done))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.follow_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            items(League.entries, key = { it.id }) { league ->
                val on = league.id in follows.leagueSet
                LaunchedEffect(on) { if (on) actions.loadTeams(league) }
                Surface(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(value = on, role = Role.Switch, onValueChange = { actions.setLeague(league, it) })
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(league.sport.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(league.label()),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(start = 16.dp),
                            )
                            Switch(checked = on, onCheckedChange = null)
                        }
                        if (on) {
                            when (val teams = state.teams[league]) {
                                null, Load.Loading -> {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { LoadingIndicator(Modifier.size(32.dp)) }
                                }

                                Load.Failed -> {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.teams_failed),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(onClick = { actions.loadTeams(league) }) { Text(stringResource(R.string.retry)) }
                                    }
                                }

                                is Load.Ready -> {
                                    FlowRow(
                                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        teams.value.forEach { team ->
                                            val picked = team.key in follows.teamKeys
                                            FilterChip(
                                                selected = picked,
                                                onClick = { actions.setTeam(team, !picked) },
                                                label = { Text(team.shortName) },
                                                leadingIcon = { TeamLogo(team, 18.dp) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A game: the score, line score by period and the leaders. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameSheet(
    load: Load<GameDetail>,
    state: SportsState,
    actions: SportsActions,
    now: Long,
    zone: ZoneId,
) {
    ModalBottomSheet(onDismissRequest = actions::closeDetail) {
        when (load) {
            Load.Loading -> {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }

            Load.Failed -> {
                Message(stringResource(R.string.load_failed))
            }

            is Load.Ready -> {
                val detail = load.value
                val game = detail.game
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameCard(game, now, zone, onClick = {}, followed = state.follows.teamKeys)
                    val periods = detail.lines.maxOfOrNull { it.periods.size } ?: 0
                    if (periods > 0) {
                        SectionCard(null) {
                            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                                Row {
                                    Spacer(Modifier.width(64.dp))
                                    (1..periods).forEach {
                                        Text(
                                            it.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(40.dp),
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.total),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(48.dp),
                                    )
                                }
                                detail.lines.forEach { line ->
                                    Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            line.team.abbreviation,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.width(64.dp),
                                        )
                                        (0 until periods).forEach { i ->
                                            Text(
                                                line.periods.getOrNull(i).orEmpty(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.width(40.dp),
                                            )
                                        }
                                        Text(
                                            line.total.orEmpty(),
                                            style = MaterialTheme.typography.titleSmall,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.width(48.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    val sides = listOf(game.away.team, game.home.team)
                    if (detail.leaders.values.any { it.isNotEmpty() }) {
                        SectionCard(stringResource(R.string.leaders)) {
                            sides.forEach { team ->
                                val leaders = detail.leaders[team.id].orEmpty()
                                if (leaders.isNotEmpty()) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        TeamLogo(team, 24.dp)
                                        Text(
                                            team.shortName,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                    leaders.forEach { l ->
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                                            Text(
                                                l.category,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(110.dp),
                                            )
                                            Text(
                                                l.player,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(l.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    listOfNotNull(game.venue, game.broadcast).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
