package io.github.michaelmassoni.tessera.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.sports.data.FollowStore
import io.github.michaelmassoni.tessera.sports.data.Follows
import io.github.michaelmassoni.tessera.sports.data.ScoreSource
import io.github.michaelmassoni.tessera.sports.data.followedGames
import io.github.michaelmassoni.tessera.sports.data.leagueGames
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameDetail
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Scores
import io.github.michaelmassoni.tessera.sports.engine.StandingsGroup
import io.github.michaelmassoni.tessera.sports.engine.Team
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Something that is loading, loaded or failed. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Ready<T>(
        val value: T,
    ) : Load<T>

    data object Failed : Load<Nothing>
}

data class SportsState(
    val loaded: Boolean = false,
    val follows: Follows = Follows(),
    /** Followed games (teams, or whole leagues with no team followed). */
    val games: List<Game> = emptyList(),
    val refreshing: Boolean = false,
    val fetchedAt: Long = 0,
    /** Every game in a league over the same days, for its tab. */
    val leagueGames: Map<League, Load<List<Game>>> = emptyMap(),
    val standings: Map<League, Load<List<StandingsGroup>>> = emptyMap(),
    /** Teams to pick from when following. */
    val teams: Map<League, Load<List<Team>>> = emptyMap(),
    val detail: Load<GameDetail>? = null,
    /** The game whose sheet is open (shown while its details load). */
    val opened: Game? = null,
)

sealed interface SportsEvent {
    data object UpdateFailed : SportsEvent

    data object FollowsChanged : SportsEvent
}

class SportsViewModel(
    private val source: ScoreSource,
    private val store: FollowStore,
    private val io: CoroutineDispatcher,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(SportsState())
    val state: StateFlow<SportsState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SportsEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var polling: Job? = null
    private var detailJob: Job? = null

    fun start() {
        viewModelScope.launch {
            val follows = withContext(io) { store.load() }
            _state.update { it.copy(loaded = true, follows = follows) }
            refresh()
        }
    }

    /**
     * While the app is visible: refresh every 30 s when something followed is live or about to
     * start, otherwise every few minutes.
     */
    fun startPolling() {
        polling?.cancel()
        polling =
            viewModelScope.launch {
                while (isActive) {
                    val next = Scores.nextRefresh(_state.value.games, clock())
                    delay((next - clock()).coerceAtLeast(5_000))
                    refresh(quiet = true)
                }
            }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun refresh(quiet: Boolean = false) {
        val follows = _state.value.follows
        if (follows.leagues.isEmpty()) return
        if (!quiet) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            runCatching { source.followedGames(follows, zone()) }
                .onSuccess { games -> _state.update { it.copy(games = games, refreshing = false, fetchedAt = clock()) } }
                .onFailure {
                    _state.update { it.copy(refreshing = false) }
                    if (!quiet) _events.tryEmit(SportsEvent.UpdateFailed)
                }
            // Keep an open game's detail current too.
            (_state.value.detail as? Load.Ready)?.value?.game?.let { open ->
                if (open.state == GameState.Live) loadDetail(open, quiet = true)
            }
        }
    }

    // ---- League tabs ----

    fun loadLeague(
        league: League,
        force: Boolean = false,
    ) {
        val s = _state.value
        if (force || s.leagueGames[league] !is Load.Ready) {
            _state.update { it.copy(leagueGames = it.leagueGames + (league to Load.Loading)) }
            viewModelScope.launch {
                val today = LocalDate.now(zone())
                val result = runCatching { source.leagueGames(league, today) }
                _state.update { it.copy(leagueGames = it.leagueGames + (league to result.fold({ g -> Load.Ready(g) }, { Load.Failed }))) }
            }
        }
        if (force || s.standings[league] !is Load.Ready) {
            _state.update { it.copy(standings = it.standings + (league to Load.Loading)) }
            viewModelScope.launch {
                val result = runCatching { source.standings(league) }
                _state.update { it.copy(standings = it.standings + (league to result.fold({ g -> Load.Ready(g) }, { Load.Failed }))) }
            }
        }
    }

    // ---- Game detail ----

    fun open(game: Game) = loadDetail(game, quiet = false)

    private fun loadDetail(
        game: Game,
        quiet: Boolean,
    ) {
        detailJob?.cancel()
        if (!quiet) _state.update { it.copy(detail = Load.Loading, opened = game) }
        detailJob =
            viewModelScope.launch {
                val result = runCatching { source.detail(game) }.getOrNull()
                _state.update {
                    if (it.detail == null) {
                        it
                    } else {
                        it.copy(detail = result?.let { d -> Load.Ready(d) } ?: if (quiet) it.detail else Load.Failed)
                    }
                }
            }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(detail = null, opened = null) }
    }

    // ---- Following ----

    fun loadTeams(league: League) {
        if (_state.value.teams[league] is Load.Ready) return
        _state.update { it.copy(teams = it.teams + (league to Load.Loading)) }
        viewModelScope.launch {
            val result = runCatching { source.teams(league) }
            _state.update { it.copy(teams = it.teams + (league to result.fold({ t -> Load.Ready(t) }, { Load.Failed }))) }
        }
    }

    fun setLeague(
        league: League,
        on: Boolean,
    ) = change { f ->
        if (on) {
            f.copy(leagues = (f.leagues + league.id).distinct())
        } else {
            f.copy(leagues = f.leagues - league.id, teams = f.teams.filter { it.league != league.id })
        }
    }

    fun setTeam(
        team: Team,
        on: Boolean,
    ) = change { f ->
        if (on) {
            f.copy(leagues = (f.leagues + team.league).distinct(), teams = (f.teams.filter { it.key != team.key } + team))
        } else {
            f.copy(teams = f.teams.filter { it.key != team.key })
        }
    }

    private fun change(edit: (Follows) -> Follows) {
        val follows = edit(_state.value.follows)
        _state.update { it.copy(follows = follows) }
        viewModelScope.launch(io) { store.save(follows) }
        _events.tryEmit(SportsEvent.FollowsChanged)
    }
}
