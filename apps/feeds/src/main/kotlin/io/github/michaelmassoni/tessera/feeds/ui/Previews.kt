package io.github.michaelmassoni.tessera.feeds.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.feeds.data.Adding
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Blocks
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import io.github.michaelmassoni.tessera.feeds.engine.Selection

// Ages relative to now, so "2 hours ago" reads the same whenever the screenshots are taken. Keep
// them under a day: from a day on, DateUtils counts calendar days ("Yesterday", "2 days ago"),
// which depends on the time of day the screenshots run.
private val now = System.currentTimeMillis()
private const val HOUR = 60 * 60 * 1000L

private val sample =
    FeedData(
        folders = listOf(Folder("tech", "Tech"), Folder("news", "News")),
        feeds =
            listOf(
                Feed("lwn", "Kernel Weekly", "https://kernel.example/rss", "https://kernel.example/", folderId = "tech"),
                Feed("android", "Compose Notes", "https://compose.example/atom.xml", folderId = "tech", notify = true),
                Feed("abc", "Morning News", "https://news.example/rss.xml", folderId = "news"),
                Feed("xkcd", "Daily Comic", "https://comic.example/atom.xml", error = "HTTP 503"),
            ),
        articles =
            listOf(
                Article(
                    "1",
                    "lwn",
                    "A look at the 7.2 merge window",
                    snippet =
                        "The 7.2 merge window closed with 13,000 non-merge changesets pulled into the mainline, " +
                            "including a new scheduler class.",
                    date = now - 2 * HOUR,
                ),
                Article(
                    "2",
                    "android",
                    "What's new in Jetpack Compose",
                    snippet = "Autofill, text styling and performance improvements headline this release.",
                    date = now - 5 * HOUR,
                    state = ArticleState.Starred,
                ),
                Article(
                    "3",
                    "abc",
                    "Heatwave conditions expected across the south-east this weekend",
                    snippet = "Temperatures are forecast to reach the low 40s in parts of New South Wales and Victoria.",
                    date = now - 9 * HOUR,
                ),
                Article(
                    "4",
                    "xkcd",
                    "Orbital Mechanics",
                    snippet = "Strip about a rocket and a very long ladder.",
                    date = now - 20 * HOUR,
                    state = ArticleState.Read,
                ),
            ),
    )

private val article =
    Article(
        "a",
        "lwn",
        "A look at the 7.2 merge window",
        link = "https://kernel.example/articles/1/",
        author = "J. Writer",
        // A fixed date: the header shows it in full.
        date = 1_795_600_000_000L,
    )

private val articleHtml =
    """
    <p>The 7.2 merge window closed with <b>13,000</b> non-merge changesets pulled into the mainline.
    Some of the <a href="https://kernel.example/">most interesting changes</a> are:</p>
    <ul><li>A new scheduler class for latency-sensitive tasks.</li><li>Rust support for more driver subsystems.</li></ul>
    <h3>Filesystems</h3>
    <blockquote><p>We finally have a story for online repair that I'm happy with.</p></blockquote>
    <pre><code>${'$'} uname -r
    7.2.0-rc1</code></pre>
    <p>The <code>-rc</code> releases continue for the next seven weeks.</p>
    """.trimIndent()

private object NoActions : FeedsActions {
    override fun select(selection: Selection) = Unit

    override fun setState(
        ids: Set<String>,
        state: ArticleState,
    ) = Unit

    override fun unstar(ids: Set<String>) = Unit

    override fun refresh() = Unit

    override fun openSettings() = Unit

    override fun share(article: Article) = Unit

    override fun openLink(url: String) = Unit

    override suspend fun content(id: String): String? = null

    override suspend fun fullArticle(article: Article): String? = null

    override suspend fun addFeed(
        address: String,
        folderId: String?,
    ) = Adding.NotFound

    override suspend fun renameFeed(
        feed: Feed,
        title: String,
    ) = true

    override suspend fun moveFeed(
        feed: Feed,
        folderId: String?,
    ) = true

    override suspend fun unsubscribe(feed: Feed) = true

    override fun setNotify(
        feedId: String,
        on: Boolean,
    ) = Unit

    override suspend fun newFolder(title: String): String? = null

    override suspend fun renameFolder(
        folder: Folder,
        title: String,
    ) = true

    override suspend fun deleteFolder(folder: Folder) = true
}

@TesseraPreviews
@Composable
private fun ArticlesPreview() {
    TesseraTheme(dynamicColor = false) { FeedsApp(FeedsState(sample, Selection.All), NoActions) }
}

@TesseraPreviews
@Composable
private fun UnreadEmptyPreview() {
    TesseraTheme(dynamicColor = false) {
        FeedsApp(FeedsState(sample.copy(articles = emptyList()), Selection.Unread), NoActions)
    }
}

@TesseraPreviews
@Composable
private fun ArticlePreview() {
    TesseraTheme(dynamicColor = false) {
        ArticleScreen(
            article,
            FeedsState(sample, onServer = true),
            NoActions,
            onBack = {},
            initialBlocks = Blocks.of(articleHtml, article.link),
        )
    }
}

@TesseraPreviews
@Composable
private fun ManageFeedsPreview() {
    TesseraTheme(dynamicColor = false) { ManageFeeds(FeedsState(sample), NoActions, onBack = {}) }
}
