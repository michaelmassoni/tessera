package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.michaelmassoni.tessera.core.cast.CastDevice
import io.github.michaelmassoni.tessera.core.cast.CastIconButton
import io.github.michaelmassoni.tessera.core.cast.CastState
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.podcasts.Progress
import io.github.michaelmassoni.tessera.podcasts.SleepTimer
import io.github.michaelmassoni.tessera.podcasts.engine.Chapter
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.Library
import io.github.michaelmassoni.tessera.podcasts.engine.SearchResult
import io.github.michaelmassoni.tessera.podcasts.engine.Show
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.ZoneId

private fun at(
    day: Int,
    hour: Int = 6,
) = LocalDateTime
    .of(2026, 11, day, hour, 0)
    .atZone(ZoneId.of("UTC"))
    .toInstant()
    .toEpochMilli()

private val shows =
    listOf(
        Show(
            "tide",
            "https://tide.example/feed",
            "The Morning Tide",
            "Harbour Audio",
            "A short, calm look at the day's news, every weekday morning. Hosted by Ana Ruiz and friends, with the " +
                "stories that matter and none of the shouting.",
            followed = true,
            latestAt = at(25),
        ),
        Show("garden", "https://garden.example/rss", "Small Garden Club", "Ivy Moss", followed = true, latestAt = at(24)),
        Show("deep", "https://deep.example/rss", "Deep Field", "Observatory Pod", followed = true, latestAt = at(20)),
        Show("chess", "https://chess.example/rss", "Endgame Notes", "Two Rooks", followed = true, latestAt = at(12)),
    )

private fun ep(
    show: String,
    n: Int,
    title: String,
    day: Int,
    minutes: Int,
    summary: String,
) = Episode("$show:$n", show, title, summary, at(day), minutes * 60_000L, "https://$show.example/$n.mp3")

private val episodes =
    mapOf(
        "tide" to
            listOf(
                ep(
                    "tide",
                    3,
                    "Wednesday: rates, rivers and a record harvest",
                    25,
                    22,
                    "Interest rates hold, flooding eases in the north, and growers count a record wheat crop.",
                ),
                ep(
                    "tide",
                    2,
                    "Tuesday: the long weekend roads",
                    24,
                    19,
                    "Holiday traffic, a new rail link and the weather for the weekend.",
                ),
                ep("tide", 1, "Monday: a week of elections", 23, 24, "What to watch in three elections this week."),
            ),
        "garden" to
            listOf(
                ep("garden", 7, "Tomatoes in pots: what actually works", 24, 41, "Ivy tries six varieties on a balcony and reports back."),
            ),
        "deep" to
            listOf(
                ep("deep", 12, "How we weigh a galaxy", 20, 58, "Dark matter, rotation curves and a very patient telescope."),
            ),
        "chess" to
            listOf(
                ep("chess", 40, "Rook endings you need to know", 12, 73, "Lucena, Philidor and the ones in between."),
            ),
    )

private val library =
    Library(
        shows = shows,
        upNext = listOf("deep:12", "garden:7", "tide:2"),
    ).withState("deep:12") { it.copy(positionMs = 31 * 60_000L) }
        .withState("garden:7") { it.copy(download = "garden-7.mp3") }
        .withState("tide:1") { it.copy(played = true) }
        .withState("tide:3") { it.copy(download = "tide-3.mp3") }

private val state =
    PodcastsState(
        library = library,
        episodes = episodes,
        downloading = mapOf("tide:2" to 40),
        player =
            PlayerUi(
                episodeId = "deep:12",
                isPlaying = true,
                speed = 1.25f,
                queue = library.upNext,
                sleep = SleepTimer.Sleep.EndOfEpisode,
                chapters =
                    listOf(
                        Chapter(0, "Intro"),
                        Chapter(540_000, "Why galaxies spin too fast"),
                        Chapter(1_380_000, "Weighing with light"),
                        Chapter(2_820_000, "Listener questions"),
                    ),
            ),
        now = at(25, 12),
    )

private object NoActions : PodcastsActions {
    override fun play(episode: Episode) = Unit

    override fun togglePlay() = Unit

    override fun playNext(episode: Episode) = Unit

    override fun addToQueue(episode: Episode) = Unit

    override fun removeFromQueue(id: String) = Unit

    override fun moveInQueue(
        from: Int,
        to: Int,
    ) = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun back() = Unit

    override fun forward() = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun setSleep(sleep: SleepTimer.Sleep) = Unit

    override fun markPlayed(
        id: String,
        played: Boolean,
    ) = Unit

    override fun download(id: String) = Unit

    override fun cancelDownload(id: String) = Unit

    override fun deleteDownload(id: String) = Unit

    override fun follow(showId: String) = Unit

    override fun unfollow(showId: String) = Unit

    override fun refresh() = Unit

    override fun openSettings() = Unit

    override suspend fun search(terms: String) = emptyList<SearchResult>()

    override suspend fun openFeed(feedUrl: String) = ""

    override suspend fun openAddress(input: String) = ""

    override suspend fun notes(episodeId: String) = ""
}

private val progress = Progress(31 * 60_000L, 58 * 60_000L)

@TesseraPreviews
@Composable
private fun HomePreview() {
    TesseraTheme(dynamicColor = false) { PodcastsApp(state, { progress }, NoActions) }
}

@TesseraPreviews
@Composable
private fun HomeEmptyPreview() {
    TesseraTheme(dynamicColor = false) { PodcastsApp(PodcastsState(now = at(25)), { Progress() }, NoActions) }
}

@TesseraPreviews
@Composable
private fun LibraryPreview() {
    TesseraTheme(dynamicColor = false) { PodcastsApp(state, { progress }, NoActions, initialScreen = Screen(tab = 1)) }
}

@TesseraPreviews
@Composable
private fun ShowPreview() {
    TesseraTheme(dynamicColor = false) {
        PodcastsApp(state, { progress }, NoActions, initialScreen = Screen(tab = 1, stack = listOf(Page.ShowPage("tide"))))
    }
}

@TesseraPreviews
@Composable
private fun EpisodePreview() {
    TesseraTheme(dynamicColor = false) {
        EpisodeScreen(
            "tide:3",
            state,
            NoActions,
            onBack = {},
            onOpenShow = {},
            initialNotes =
                "<p>Interest rates hold, flooding eases in the north, and growers count a record wheat crop.</p>" +
                    "<p><b>In this episode</b></p><ul><li>The rate decision</li><li>River levels</li><li>The harvest</li></ul>" +
                    "<p>Read more at <a href=\"https://tide.example\">tide.example</a>.</p>",
        )
    }
}

@TesseraPreviews
@Composable
private fun SearchPreview() {
    TesseraTheme(dynamicColor = false) {
        val model =
            remember {
                SearchModel(
                    "garden",
                    SearchPhase.Results(
                        listOf(
                            SearchResult("https://garden.example/rss", "Small Garden Club", "Ivy Moss"),
                            SearchResult("https://veg.example/rss", "The Vegetable Patch", "Allotment Radio"),
                            SearchResult("https://bees.example/rss", "Bees, Mostly", "Hive Mind Media"),
                        ),
                    ),
                )
            }
        PodcastsApp(state, { progress }, NoActions, initialScreen = Screen(tab = 2), search = model)
    }
}

@TesseraPreviews
@Composable
private fun PlayerPreview() {
    TesseraTheme(dynamicColor = false) { FullPlayer(state, { progress }, NoActions, onClose = {}, onOpenShow = {}) }
}

@TesseraPreviews
@Composable
private fun DownloadsPreview() {
    TesseraTheme(dynamicColor = false) {
        PodcastsApp(state, { progress }, NoActions, initialScreen = Screen(tab = 1, stack = listOf(Page.DownloadsPage)))
    }
}

// The player while casting: Cast filled in beside speed and sleep, and "Casting to ..." under them.
@TesseraPreviews
@Composable
private fun CastingPlayerPreview() {
    val tv = CastDevice("1", "Living Room TV", "Chromecast", InetAddress.getLoopbackAddress(), 8009, video = true)
    TesseraTheme(dynamicColor = false) {
        FullPlayer(
            state,
            { progress },
            NoActions,
            onClose = {},
            onOpenShow = {},
            castButton = { CastIconButton(CastState.Connected(tv, 0.4f, false)) {} },
            castingTo = tv.name,
        )
    }
}
