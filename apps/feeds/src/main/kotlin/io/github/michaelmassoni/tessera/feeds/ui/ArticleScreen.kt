package io.github.michaelmassoni.tessera.feeds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.feeds.R
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Block
import io.github.michaelmassoni.tessera.feeds.engine.Blocks
import io.github.michaelmassoni.tessera.feeds.engine.RichText
import io.github.michaelmassoni.tessera.feeds.engine.Style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.DateFormat
import java.util.Date
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** One article, shown natively: header, then its blocks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    article: Article,
    state: FeedsState,
    actions: FeedsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    initialBlocks: List<Block>? = null,
) {
    val feed = state.data.feed(article.feedId)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var full by remember(article.id) { mutableStateOf<String?>(null) }
    var loadingFull by remember(article.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.full_article_failed)
    val blocks by produceState(initialBlocks, article.id, full) {
        if (initialBlocks != null && full == null) return@produceState
        val html = full ?: actions.content(article.id)
        value =
            withContext(Dispatchers.Default) {
                if (html.isNullOrBlank()) {
                    listOfNotNull(article.snippet.takeIf { it.isNotBlank() }?.let { Block.Paragraph(RichText(it)) })
                } else {
                    Blocks.of(html, article.link.ifEmpty { feed?.siteUrl.orEmpty() })
                }
            }
    }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {},
                scrollBehavior = scroll,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        actions.setState(setOf(article.id), if (article.starred) ArticleState.Read else ArticleState.Starred)
                    }) {
                        Icon(
                            painterResource(if (article.starred) DesignR.drawable.ic_star_filled else DesignR.drawable.ic_star),
                            contentDescription = stringResource(if (article.starred) R.string.unstar else R.string.star),
                        )
                    }
                    IconButton(onClick = { actions.setState(setOf(article.id), ArticleState.Unread) }, enabled = !article.unread) {
                        Icon(
                            painterResource(DesignR.drawable.ic_radio_button_unchecked),
                            contentDescription = stringResource(R.string.mark_unread),
                        )
                    }
                    IconButton(onClick = { actions.share(article) }, enabled = article.link.isNotEmpty()) {
                        Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.share))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_in_browser)) },
                            enabled = article.link.isNotEmpty(),
                            onClick = {
                                menu = false
                                actions.openLink(article.link)
                            },
                        )
                        if (state.onServer) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (full == null) R.string.full_article else R.string.feed_text)) },
                                enabled = article.link.isNotEmpty() && !loadingFull,
                                onClick = {
                                    menu = false
                                    if (full != null) {
                                        full = null
                                        return@DropdownMenuItem
                                    }
                                    loadingFull = true
                                    scope.launch {
                                        val text = actions.fullArticle(article)
                                        loadingFull = false
                                        if (text.isNullOrBlank()) snackbar.showSnackbar(failed) else full = text
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.textScale)) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") { Header(article, feed?.title.orEmpty(), state.icons[article.feedId]) }
                    if (loadingFull) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                    val list = blocks
                    if (list == null) {
                        item(key = "wait") { Box(Modifier.fillMaxWidth().height(96.dp)) }
                    } else {
                        items(list) { BlockView(it, actions::openLink) }
                    }
                    if (article.link.isNotEmpty()) {
                        item(key = "open") {
                            OutlinedButton(onClick = { actions.openLink(article.link) }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_open_in_new),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(stringResource(R.string.open_in_browser), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    article: Article,
    feedTitle: String,
    icon: String?,
) {
    val date =
        remember(article.date) {
            article.date.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedIcon(feedTitle, article.feedId, icon, size = 20.dp)
            Text(feedTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(article.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        val byline = listOfNotNull(article.author.takeIf { it.isNotBlank() }, date).joinToString(" · ")
        if (byline.isNotEmpty()) {
            Text(byline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun BlockView(
    block: Block,
    onLink: (String) -> Unit,
) {
    val body = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f)
    when (block) {
        is Block.Paragraph -> {
            SelectionContainer { Text(block.text.annotated(), style = body) }
        }

        is Block.Heading -> {
            Text(
                block.text.annotated(),
                style =
                    when (block.level) {
                        1, 2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                modifier = Modifier.semantics { heading() },
            )
        }

        is Block.Image -> {
            AsyncImage(
                model = block.url,
                contentDescription = block.alt.ifEmpty { null },
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .then(if (block.link != null) Modifier.clickable { onLink(block.link) } else Modifier),
            )
        }

        is Block.Caption -> {
            Text(block.text.annotated(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is Block.Quote -> {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    block.blocks.forEach { BlockView(it, onLink) }
                }
            }
        }

        is Block.Bullets -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.items.forEachIndexed { i, item ->
                    Row {
                        Text(if (block.ordered) "${i + 1}." else "•", style = body, modifier = Modifier.width(28.dp))
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) { item.forEach { BlockView(it, onLink) } }
                    }
                }
            }
        }

        is Block.Code -> {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                SelectionContainer {
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        }

        is Block.Embed -> {
            val host = remember(block.url) { runCatching { URI(block.url).host }.getOrNull().orEmpty().removePrefix("www.") }
            OutlinedButton(onClick = { onLink(block.url) }) {
                Icon(painterResource(DesignR.drawable.ic_play_circle), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.open_embed, host), modifier = Modifier.padding(start = 8.dp))
            }
        }

        Block.Rule -> {
            HorizontalDivider()
        }
    }
}

@Composable
private fun RichText.annotated(): AnnotatedString {
    val link = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(this, link, codeBackground) {
        buildAnnotatedString {
            append(text)
            spans.forEach { s ->
                when (val style = s.style) {
                    Style.Bold -> {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), s.start, s.end)
                    }

                    Style.Italic -> {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), s.start, s.end)
                    }

                    Style.Code -> {
                        addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground), s.start, s.end)
                    }

                    Style.Strike -> {
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s.start, s.end)
                    }

                    is Style.Link -> {
                        addLink(
                            LinkAnnotation.Url(
                                style.url,
                                TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)),
                            ),
                            s.start,
                            s.end,
                        )
                    }
                }
            }
        }
    }
}
