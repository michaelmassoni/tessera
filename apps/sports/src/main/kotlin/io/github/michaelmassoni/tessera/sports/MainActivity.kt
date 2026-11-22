package io.github.michaelmassoni.tessera.sports

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.sports.data.EspnSource
import io.github.michaelmassoni.tessera.sports.data.followStore
import io.github.michaelmassoni.tessera.sports.data.sportsSettings
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Team
import io.github.michaelmassoni.tessera.sports.live.LiveGames
import io.github.michaelmassoni.tessera.sports.ui.SportsActions
import io.github.michaelmassoni.tessera.sports.ui.SportsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val viewModel: SportsViewModel by viewModels {
        viewModelFactory {
            initializer { SportsViewModel(EspnSource(), followStore(), Dispatchers.IO, hideScores = { sportsSettings().hideScores }) }
        }
    }

    private val snackbar = SnackbarHostState()

    /** Live Updates need notifications; asked once teams are picked. */
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { LiveGames.kick(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start()
        if (savedInstanceState == null) {
            if (followStore().load().teams.isNotEmpty() && sportsSettings().liveUpdates) LiveGames.kick(applicationContext)
        }
        val actions = Actions()
        setTesseraContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    now = System.currentTimeMillis()
                }
            }
            val failed = stringResource(R.string.offline)
            val retry = stringResource(R.string.retry)
            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    when (event) {
                        SportsEvent.UpdateFailed -> {
                            if (snackbar.showSnackbar(failed, actionLabel = retry) == SnackbarResult.ActionPerformed) viewModel.refresh()
                        }

                        SportsEvent.FollowsChanged -> {
                            viewModel.refresh(quiet = true)
                        }
                    }
                }
            }
            SportsApp(state, actions, now, ZoneId.systemDefault(), snackbar)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.reloadSettings()
        viewModel.startPolling()
    }

    override fun onStop() {
        viewModel.stopPolling()
        super.onStop()
    }

    private inner class Actions : SportsActions {
        override fun refresh() = viewModel.refresh()

        override fun loadLeague(
            league: League,
            force: Boolean,
        ) = viewModel.loadLeague(league, force)

        override fun open(game: Game) = viewModel.open(game)

        override fun closeDetail() = viewModel.closeDetail()

        override fun loadTeams(league: League) = viewModel.loadTeams(league)

        override fun setLeague(
            league: League,
            on: Boolean,
        ) = viewModel.setLeague(league, on)

        override fun setTeam(
            team: Team,
            on: Boolean,
        ) = viewModel.setTeam(team, on)

        override fun doneFollowing() {
            viewModel.refresh()
            if (viewModel.state.value.follows.teams
                    .isEmpty() || !sportsSettings().liveUpdates
            ) {
                LiveGames.stop(applicationContext)
                return
            }
            // Android 12 doesn't ask for notifications; 13+ does.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                LiveGames.kick(applicationContext)
            } else {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun reveal(game: Game) = viewModel.reveal(game)

        override fun openTeam(team: Team) = viewModel.openTeam(team)

        override fun closeTeam() = viewModel.closeTeam()
    }
}
