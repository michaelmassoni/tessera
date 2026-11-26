package io.github.michaelmassoni.tessera.feeds.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.feeds.R
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Library
import io.github.michaelmassoni.tessera.feeds.engine.Selection
import io.github.michaelmassoni.tessera.feeds.engine.SwipeAction
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Feeds' main screen: the drawer, the article list and, on wide screens, the article beside it. */
@Composable
fun FeedsApp(
    state: FeedsState,
    actions: FeedsActions,
    modifier: Modifier = Modifier,
    openArticle: String? = null,
    onOpenedArticle: () -> Unit = {},
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    // Articles read while this list is shown stay in "Unread" until it changes, so they don't jump away.
    var keep by remember(state.selection) { mutableStateOf(emptySet<String>()) }
    // The list keeps its scroll position while an article or Manage feeds covers it; a closed article forgets its own.
    val saveable = rememberSaveableStateHolder()

    fun show(id: String?) {
        openId?.let { if (it != id) saveable.removeState(articleKey(it)) }
        openId = id
    }

    fun open(id: String) {
        show(id)
        keep = keep + id
        actions.setState(setOf(id), ArticleState.Read)
    }
    LaunchedEffect(openArticle) {
        if (openArticle != null) {
            managing = false
            open(openArticle)
            onOpenedArticle()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE
        if (managing) {
            BackHandler { managing = false }
            ManageFeeds(state, actions, onBack = { managing = false })
            return@BoxWithConstraints
        }
        val article = openId?.let(state.data::article)
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val drawerContent: @Composable () -> Unit = {
            Drawer(
                state,
                onSelect = {
                    actions.select(it)
                    if (!wide) show(null)
                    scope.launch { drawer.close() }
                },
                onManage = {
                    managing = true
                    scope.launch { drawer.close() }
                },
                onSettings = actions::openSettings,
            )
        }
        // The drawer stays open beside the panes only when there's room for all three.
        val permanent = maxWidth >= PERMANENT_DRAWER
        val openDrawer: (() -> Unit)? = if (permanent) null else ({ scope.launch { drawer.open() } })
        val panes: @Composable () -> Unit = {
            when {
                wide -> {
                    Row(Modifier.fillMaxSize()) {
                        saveable.SaveableStateProvider(LIST_KEY) {
                            ArticleList(
                                state,
                                actions,
                                keep,
                                onOpen = ::open,
                                onKeep = { keep = keep + it },
                                openDrawer = openDrawer,
                                modifier = Modifier.width(LIST_WIDTH),
                            )
                        }
                        VerticalDivider()
                        if (article != null) {
                            saveable.SaveableStateProvider(articleKey(article.id)) {
                                ArticleScreen(article, state, actions, onBack = null, modifier = Modifier.weight(1f))
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.pick_article), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                article != null -> {
                    BackHandler { show(null) }
                    saveable.SaveableStateProvider(articleKey(article.id)) {
                        ArticleScreen(article, state, actions, onBack = { show(null) })
                    }
                }

                else -> {
                    saveable.SaveableStateProvider(LIST_KEY) {
                        ArticleList(state, actions, keep, onOpen = ::open, onKeep = { keep = keep + it }, openDrawer = openDrawer)
                    }
                }
            }
        }
        if (permanent) {
            PermanentNavigationDrawer(drawerContent = { PermanentDrawerSheet(Modifier.width(300.dp)) { drawerContent() } }) { panes() }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = wide || article == null,
                drawerContent = { ModalDrawerSheet { drawerContent() } },
            ) { panes() }
            BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
        }
    }
}

private val WIDE = 840.dp
private val PERMANENT_DRAWER = 1200.dp
private val LIST_WIDTH = 400.dp

// Saved-state keys: the article list, and each article opened over it.
private const val LIST_KEY = "list"

private fun articleKey(id: String) = "article:$id"

private fun selectionKey(selection: Selection) = Json.encodeToString(Selection.serializer(), selection)

@Composable
private fun Drawer(
    state: FeedsState,
    onSelect: (Selection) -> Unit,
    onManage: () -> Unit,
    onSettings: () -> Unit,
) {
    val data = state.data
    val byFeed = remember(data) { Library.unreadByFeed(data) }
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        val entry: @Composable (Selection, String, Int, Int) -> Unit = { selection, label, icon, count ->
            NavigationDrawerItem(
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(painterResource(icon), contentDescription = null) },
                badge = { if (count > 0) Text(count.toString()) },
                selected = state.selection == selection,
                onClick = { onSelect(selection) },
            )
        }
        entry(Selection.Unread, stringResource(R.string.unread), DesignR.drawable.ic_view_agenda, data.articles.count { it.unread })
        entry(Selection.Starred, stringResource(R.string.starred), DesignR.drawable.ic_star, 0)
        entry(Selection.All, stringResource(R.string.all_articles), DesignR.drawable.ic_view_list, 0)
        if (data.feeds.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.feeds),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        data.folders.sortedBy { it.title.lowercase() }.forEach { folder ->
            val open = folder.id in expanded
            val feeds = data.feeds.filter { it.folderId == folder.id }.sortedBy { it.title.lowercase() }
            NavigationDrawerItem(
                label = { Text(folder.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(painterResource(DesignR.drawable.ic_folder), contentDescription = null) },
                badge = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val n = Library.unreadInFolder(data, folder.id, byFeed)
                        if (n > 0) Text(n.toString())
                        // Shows the folder's feeds under it, without choosing the folder.
                        IconButton(onClick = { expanded = if (open) expanded - folder.id else expanded + folder.id }) {
                            Icon(
                                painterResource(if (open) DesignR.drawable.ic_expand_less else DesignR.drawable.ic_expand_more),
                                contentDescription = stringResource(if (open) R.string.collapse else R.string.expand),
                            )
                        }
                    }
                },
                selected = state.selection == Selection.InFolder(folder.id),
                onClick = { onSelect(Selection.InFolder(folder.id)) },
            )
            if (open) {
                feeds.forEach { feed ->
                    NavigationDrawerItem(
                        label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { FeedIcon(feed.title, feed.id, state.icons[feed.id], size = 24.dp) },
                        badge = { byFeed[feed.id]?.let { Text(it.toString()) } },
                        selected = state.selection == Selection.OfFeed(feed.id),
                        onClick = { onSelect(Selection.OfFeed(feed.id)) },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }
        val folderIds = data.folders.map { it.id }.toSet()
        data.feeds.filter { it.folderId == null || it.folderId !in folderIds }.sortedBy { it.title.lowercase() }.forEach { feed ->
            NavigationDrawerItem(
                label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { FeedIcon(feed.title, feed.id, state.icons[feed.id], size = 24.dp) },
                badge = { byFeed[feed.id]?.let { Text(it.toString()) } },
                selected = state.selection == Selection.OfFeed(feed.id),
                onClick = { onSelect(Selection.OfFeed(feed.id)) },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.manage_feeds)) },
            icon = { Icon(painterResource(DesignR.drawable.ic_tune), contentDescription = null) },
            selected = false,
            onClick = onManage,
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.settings)) },
            icon = { Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = null) },
            selected = false,
            onClick = onSettings,
        )
        Spacer(Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleList(
    state: FeedsState,
    actions: FeedsActions,
    keep: Set<String>,
    onOpen: (String) -> Unit,
    onKeep: (Set<String>) -> Unit,
    openDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val articles = remember(data, state.selection, query, keep) { Library.articles(data, state.selection, query, also = keep) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Another feed or folder starts at the top, but coming back to the same one (or rotating) keeps the place.
    var listed by rememberSaveable { mutableStateOf(selectionKey(state.selection)) }
    LaunchedEffect(state.selection) {
        val now = selectionKey(state.selection)
        if (now != listed) {
            listed = now
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it) } }
    val markedText = LocalResources.current
    val undo = stringResource(R.string.undo)
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            query,
                            { query = it },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Text(selectionTitle(state), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    when {
                        searching -> {
                            BackHandler {
                                searching = false
                                query = ""
                            }
                            IconButton(onClick = {
                                searching = false
                                query = ""
                            }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.close_search),
                                )
                            }
                        }

                        openDrawer != null -> {
                            IconButton(onClick = openDrawer) {
                                Icon(painterResource(DesignR.drawable.ic_menu), contentDescription = stringResource(R.string.open_drawer))
                            }
                        }
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(painterResource(DesignR.drawable.ic_search), contentDescription = stringResource(R.string.search))
                        }
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mark_all_read)) },
                            enabled = articles.any { it.unread },
                            onClick = {
                                menu = false
                                val ids = Library.unreadIds(articles)
                                onKeep(ids)
                                actions.setState(ids, ArticleState.Read)
                                scope.launch {
                                    val result =
                                        snackbar.showSnackbar(
                                            markedText.getQuantityString(R.plurals.marked_read, ids.size, ids.size),
                                            actionLabel = undo,
                                            withDismissAction = true,
                                        )
                                    if (result == SnackbarResult.ActionPerformed) actions.setState(ids, ArticleState.Unread)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh)) },
                            onClick = {
                                menu = false
                                actions.refresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = {
                                menu = false
                                actions.openSettings()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = actions::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (articles.isEmpty()) {
                EmptyList(state, query.isNotBlank())
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(articles, key = { it.id }) { a ->
                        ArticleRow(
                            a,
                            state,
                            onOpen = { onOpen(a.id) },
                            onToggleRead = {
                                onKeep(setOf(a.id))
                                actions.setState(setOf(a.id), if (a.unread) ArticleState.Read else ArticleState.Unread)
                            },
                            onToggleStar = {
                                // Starring marks it read too (the states are exclusive): keep it in view in Unread.
                                onKeep(setOf(a.id))
                                actions.setState(setOf(a.id), if (a.starred) ArticleState.Read else ArticleState.Starred)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun selectionTitle(state: FeedsState): String =
    when (val s = state.selection) {
        Selection.Unread -> stringResource(R.string.unread)
        Selection.Starred -> stringResource(R.string.starred)
        Selection.All -> stringResource(R.string.all_articles)
        is Selection.InFolder -> state.data.folder(s.folderId)?.title ?: stringResource(R.string.app_name)
        is Selection.OfFeed -> state.data.feed(s.feedId)?.title ?: stringResource(R.string.app_name)
    }

@Composable
private fun EmptyList(
    state: FeedsState,
    searching: Boolean,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_feed),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(
                when {
                    searching -> R.string.empty_search
                    state.data.feeds.isEmpty() -> R.string.empty_no_feeds
                    state.selection == Selection.Unread -> R.string.all_caught_up
                    state.selection == Selection.Starred -> R.string.empty_starred
                    else -> R.string.empty_articles
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleRow(
    a: Article,
    state: FeedsState,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed = state.data.feed(a.feedId)
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val readLabel = stringResource(if (a.unread) R.string.mark_read else R.string.mark_unread)
    val starLabel = stringResource(if (a.starred) R.string.unstar else R.string.star)
    val age = relativeTime(a.date.takeIf { it > 0 } ?: a.added)
    val description =
        listOfNotNull(
            if (a.unread) stringResource(R.string.unread) else null,
            if (a.starred) stringResource(R.string.starred) else null,
            feed?.title,
            a.title,
            age,
        ).joinToString(". ")
    val actionFor = { direction: SwipeToDismissBoxValue ->
        if (direction == SwipeToDismissBoxValue.StartToEnd) state.swipeRight else state.swipeLeft
    }
    val run by rememberUpdatedState { direction: SwipeToDismissBoxValue ->
        when (actionFor(direction)) {
            SwipeAction.ToggleRead -> onToggleRead()
            SwipeAction.ToggleStar -> onToggleStar()
            SwipeAction.None -> Unit
        }
    }
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = state.swipeRight != SwipeAction.None,
        enableDismissFromEndToStart = state.swipeLeft != SwipeAction.None,
        // SwipeToDismissBox restarts its effect whenever onDismiss changes, and calls it again while
        // the row is still swiped away: a new lambda on each recomposition toggled the article back
        // and forth and left the row stuck off-screen. Keep it the same lambda for the row's life.
        onDismiss =
            remember(swipe, scope) {
                { direction ->
                    run(direction)
                    scope.launch { swipe.reset() }
                }
            },
        backgroundContent = {
            val direction = swipe.dismissDirection
            val action = actionFor(direction)
            if (direction != SwipeToDismissBoxValue.Settled && action != SwipeAction.None) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        if (direction == SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                ) {
                    val icon =
                        when (action) {
                            SwipeAction.ToggleStar -> {
                                if (a.starred) DesignR.drawable.ic_star else DesignR.drawable.ic_star_filled
                            }

                            else -> {
                                if (a.unread) DesignR.drawable.ic_check else DesignR.drawable.ic_radio_button_unchecked
                            }
                        }
                    Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                    .clearAndSetSemantics {
                        contentDescription = description
                        onClick { onOpen().let { true } }
                        customActions =
                            listOf(
                                CustomAccessibilityAction(readLabel) { onToggleRead().let { true } },
                                CustomAccessibilityAction(starLabel) { onToggleStar().let { true } },
                            )
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedIcon(feed?.title.orEmpty(), a.feedId, state.icons[a.feedId], size = 18.dp)
                        Text(
                            listOfNotNull(feed?.title, age).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (a.starred) {
                            Icon(
                                painterResource(DesignR.drawable.ic_star_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        a.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (a.unread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (a.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (a.snippet.isNotBlank()) {
                        Text(
                            a.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                a.image?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium),
                    )
                }
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(readLabel) }, onClick = {
                    menu = false
                    onToggleRead()
                })
                DropdownMenuItem(text = { Text(starLabel) }, onClick = {
                    menu = false
                    onToggleStar()
                })
            }
        }
    }
}

/** "2 hours ago". */
@Composable
internal fun relativeTime(time: Long): String {
    val now = remember { System.currentTimeMillis() }
    return remember(time, now) {
        android.text.format.DateUtils
            .getRelativeTimeSpanString(time, now, android.text.format.DateUtils.MINUTE_IN_MILLIS)
            .toString()
    }
}
