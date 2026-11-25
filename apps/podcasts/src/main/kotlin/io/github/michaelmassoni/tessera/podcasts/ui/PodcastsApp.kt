package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.github.michaelmassoni.tessera.podcasts.Progress
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.core.design.R as DesignR

private enum class Tab(
    val label: Int,
    val icon: Int,
) {
    Home(R.string.tab_home, R.drawable.ic_home),
    Library(R.string.tab_library, R.drawable.ic_subscriptions),
    Search(R.string.tab_search, DesignR.drawable.ic_search),
}

/** A page opened over a tab. */
sealed interface Page {
    data class ShowPage(
        val id: String,
    ) : Page

    data class EpisodePage(
        val id: String,
    ) : Page

    data object DownloadsPage : Page
}

/** Where the app is: a tab and the pages opened from it. */
data class Screen(
    val tab: Int = 0,
    val stack: List<Page> = emptyList(),
) {
    companion object {
        val Saver =
            listSaver<Screen, String>(
                save = { s ->
                    listOf(s.tab.toString()) +
                        s.stack.map { p ->
                            when (p) {
                                is Page.ShowPage -> "s:" + p.id
                                is Page.EpisodePage -> "e:" + p.id
                                Page.DownloadsPage -> "d:"
                            }
                        }
                },
                restore = { saved ->
                    Screen(
                        saved.first().toInt(),
                        saved.drop(1).map { s ->
                            val id = s.substring(2)
                            when (s.first()) {
                                's' -> Page.ShowPage(id)
                                'e' -> Page.EpisodePage(id)
                                else -> Page.DownloadsPage
                            }
                        },
                    )
                },
            )
    }
}

@Composable
fun PodcastsApp(
    state: PodcastsState,
    progress: () -> Progress,
    actions: PodcastsActions,
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen(),
    search: SearchModel = remember { SearchModel() },
) {
    var screen by rememberSaveable(stateSaver = Screen.Saver) { mutableStateOf(initialScreen) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = screen.stack.isNotEmpty()) { events ->
        try {
            events.collect { backProgress = it.progress }
            screen = screen.copy(stack = screen.stack.dropLast(1))
        } finally {
            backProgress = 0f
        }
    }

    fun open(page: Page) {
        if (screen.stack.lastOrNull() != page) screen = screen.copy(stack = screen.stack + page)
    }
    val back: () -> Unit = { screen = screen.copy(stack = screen.stack.dropLast(1)) }
    val openShow: (String) -> Unit = { open(Page.ShowPage(it)) }
    val openEpisode: (String) -> Unit = { open(Page.EpisodePage(it)) }
    val toSearch: () -> Unit = { screen = Screen(Tab.Search.ordinal) }

    PlayerSheet(
        hasMedia = state.player.episodeId != null,
        modifier = modifier,
        player = { onClose, sheetModifier ->
            FullPlayer(state, progress, actions, onClose = onClose, onOpenShow = openShow, modifier = sheetModifier)
        },
    ) { openPlayer, dragUp ->
        val adaptive = currentWindowAdaptiveInfoV2()
        val layout =
            if (adaptive.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptive)
            } else {
                NavigationSuiteType.NavigationRail
            }
        NavigationSuiteScaffold(
            layoutType = layout,
            navigationSuiteItems = {
                Tab.entries.forEach { t ->
                    item(
                        selected = screen.tab == t.ordinal && screen.stack.isEmpty(),
                        onClick = { screen = Screen(t.ordinal) },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val deeper = targetState.stack.size > initialState.stack.size
                        val shallower = targetState.stack.size < initialState.stack.size
                        when {
                            deeper -> (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150))
                            shallower -> fadeIn(tween(250)) togetherWith (slideOutHorizontally(tween(250)) { it / 5 } + fadeOut(tween(200)))
                            else -> fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90))
                        }
                    },
                    label = "pages",
                    modifier =
                        Modifier.weight(1f).graphicsLayer {
                            val p = backProgress
                            scaleX = 1f - 0.08f * p
                            scaleY = 1f - 0.08f * p
                            translationX = p * 32.dp.toPx()
                        },
                ) { s ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        when (val p = s.stack.lastOrNull()) {
                            is Page.ShowPage -> {
                                ShowScreen(p.id, state, actions, back, openEpisode)
                            }

                            is Page.EpisodePage -> {
                                EpisodeScreen(p.id, state, actions, back, openShow)
                            }

                            Page.DownloadsPage -> {
                                DownloadsScreen(state, actions, back, openEpisode, openShow)
                            }

                            null -> {
                                when (Tab.entries[s.tab]) {
                                    Tab.Home -> HomeScreen(state, actions, openEpisode, openShow, toSearch)
                                    Tab.Library -> LibraryScreen(state, actions, openShow, { open(Page.DownloadsPage) }, toSearch)
                                    Tab.Search -> SearchScreen(actions, openShow, model = search)
                                }
                            }
                        }
                    }
                }
                if (state.player.episodeId != null) MiniPlayer(state, progress, actions, onOpen = openPlayer, modifier = dragUp)
            }
        }
    }
}
