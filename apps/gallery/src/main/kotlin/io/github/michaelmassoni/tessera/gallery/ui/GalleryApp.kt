package io.github.michaelmassoni.tessera.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import io.github.michaelmassoni.tessera.core.design.TesseraSearchTopBar
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.gallery.Collection
import io.github.michaelmassoni.tessera.gallery.GalleryState
import io.github.michaelmassoni.tessera.gallery.R
import io.github.michaelmassoni.tessera.gallery.data.Thumbnail
import io.github.michaelmassoni.tessera.gallery.engine.Album
import io.github.michaelmassoni.tessera.gallery.engine.DaySection
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.engine.RelativeLabels
import io.github.michaelmassoni.tessera.gallery.engine.Timeline
import io.github.michaelmassoni.tessera.gallery.engine.TrashInfo
import java.time.Instant
import java.time.ZoneId
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Grid-level actions. */
interface GridActions {
    fun toggle(item: MediaItem)

    fun toggleDay(section: DaySection)

    fun clearSelection()

    fun shareSelection()

    fun favoriteSelection()

    fun trashSelection()

    fun restoreSelection()

    fun deleteSelectionForever()

    /** Moves the selection into the folder [relativePath] (asks the system for permission first). */
    fun moveSelection(relativePath: String)

    /** Copies the selection into the folder [relativePath]. */
    fun copySelection(relativePath: String)

    fun openSettings()

    /** Picking for another app: return [items]. */
    fun pick(items: List<MediaItem>)
}

private enum class Tab(
    val label: Int,
    val icon: Int,
) {
    Photos(R.string.tab_photos, DesignR.drawable.ic_photo_library),
    Albums(R.string.tab_albums, DesignR.drawable.ic_photo_album),
}

/**
 * Tabs (Photos, Albums), album screens and the full-screen viewer. Back closes the viewer, then
 * the selection, then the album.
 */
@Composable
fun GalleryApp(
    state: GalleryState,
    itemsOf: (Collection) -> List<MediaItem>,
    gridActions: GridActions,
    viewerActions: ViewerActions,
    dynamicColor: Boolean,
    modifier: Modifier = Modifier,
    editorActions: EditorActions? = null,
    pickMode: Boolean = false,
    pickMultiple: Boolean = false,
    now: () -> Long = System::currentTimeMillis,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Saveable, so rotating or a wallpaper change (which recreates the activity) keeps your place.
    var album by rememberSaveable(stateSaver = CollectionSaver) { mutableStateOf<Collection?>(null) }
    var viewing by rememberSaveable(stateSaver = ViewingSaver) { mutableStateOf<Pair<Collection, Long>?>(null) }
    // The photo open in the editor, by id.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    // Each tab and the open album keep their scroll position while another is shown; a closed album forgets its own.
    val saveable = rememberSaveableStateHolder()

    fun closeAlbum() {
        album?.let { saveable.removeState(it.stateKey()) }
        album = null
    }

    BackHandler(viewing == null && state.selection.isNotEmpty()) { gridActions.clearSelection() }
    BackHandler(viewing == null && state.selection.isEmpty() && album != null) { closeAlbum() }

    val open: (Collection, MediaItem) -> Unit = { c, item ->
        if (pickMode) {
            if (pickMultiple) gridActions.toggle(item) else gridActions.pick(listOf(item))
        } else {
            viewing = c to item.id
        }
    }

    Box(modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                Tab.entries.forEach { t ->
                    item(
                        selected = tab == t.ordinal && album == null,
                        onClick = {
                            tab = t.ordinal
                            closeAlbum()
                        },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            },
        ) {
            val current = album
            saveable.SaveableStateProvider(current?.stateKey() ?: "tab:$tab") {
                when {
                    current != null -> {
                        CollectionScreen(
                            state,
                            current,
                            itemsOf(current),
                            gridActions,
                            ::closeAlbum,
                            { open(current, it) },
                            now,
                            pickMode,
                            onQuery = if (current is Collection.Search) ({ q -> album = Collection.Search(q) }) else null,
                        )
                    }

                    tab == 0 -> {
                        CollectionScreen(
                            state,
                            Collection.All,
                            state.items,
                            gridActions,
                            null,
                            { open(Collection.All, it) },
                            now,
                            pickMode,
                            onOpenSearch = if (pickMode) null else ({ album = Collection.Search("") }),
                        )
                    }

                    else -> {
                        AlbumsScreen(state, itemsOf, { album = it }, gridActions)
                    }
                }
            }
        }
        if (pickMode && pickMultiple && state.selection.isNotEmpty()) {
            Button(
                onClick = { gridActions.pick(state.items.filter { it.id in state.selection }) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).padding(bottom = 80.dp),
            ) { Text(stringResource(R.string.action_done)) }
        }
        viewing?.let { (collection, id) ->
            val items = itemsOf(collection)
            val index = items.indexOfFirst { it.id == id }
            BackHandler { viewing = null }
            TesseraTheme(darkTheme = true, dynamicColor = dynamicColor, pureBlack = true) {
                Viewer(
                    items = items,
                    startIndex = if (index < 0) 0 else index,
                    trashMode = collection == Collection.Trash,
                    onEditPhoto = { if (editorActions != null) editing = it.id },
                    actions =
                        object : ViewerActions by viewerActions {
                            override fun close() {
                                viewing = null
                            }
                        },
                )
                val edited = editing?.let { id -> items.firstOrNull { it.id == id } }
                if (edited != null && editorActions != null) {
                    Editor(edited, editorActions, onClose = { editing = null })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScreen(
    state: GalleryState,
    collection: Collection,
    items: List<MediaItem>,
    actions: GridActions,
    onBack: (() -> Unit)?,
    onOpen: (MediaItem) -> Unit,
    now: () -> Long,
    pickMode: Boolean,
    // The Photos tab: opens search. A search screen: the query changed.
    onOpenSearch: (() -> Unit)? = null,
    onQuery: ((String) -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val labels = RelativeLabels(stringResource(R.string.today), stringResource(R.string.yesterday))
    val grouped = collection == Collection.All || collection is Collection.Folder
    val sections =
        remember(items, locale, labels) {
            if (grouped) {
                val zone = ZoneId.systemDefault()
                Timeline.sections(items, Instant.ofEpochMilli(now()).atZone(zone).toLocalDate(), zone, locale, labels)
            } else {
                null
            }
        }
    val title =
        when (collection) {
            Collection.All -> stringResource(R.string.tab_photos)
            is Collection.Folder -> collection.name
            Collection.Favorites -> stringResource(R.string.favorites)
            Collection.Videos -> stringResource(R.string.videos)
            Collection.Trash -> stringResource(R.string.trash)
            is Collection.Search -> ""
        }
    Scaffold(
        topBar = {
            if (state.selection.isNotEmpty() && !pickMode) {
                SelectionBar(state.selection.size, collection == Collection.Trash, state.albums, actions)
            } else if (collection is Collection.Search && onQuery != null) {
                SearchTopBar(collection.query, onQuery, onBack ?: {})
            } else {
                TopAppBar(
                    title = { Text(if (pickMode) stringResource(R.string.pick_title) else title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (onBack == null && !pickMode) {
                            onOpenSearch?.let {
                                IconButton(onClick = it) {
                                    Icon(painterResource(DesignR.drawable.ic_search), contentDescription = stringResource(R.string.search))
                                }
                            }
                            IconButton(onClick = actions::openSettings) {
                                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (collection == Collection.Trash && items.isNotEmpty()) {
                Text(
                    stringResource(R.string.trash_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (collection is Collection.Search && collection.query.isBlank()) {
                // Nothing typed yet: nothing to say.
            } else if (state.loaded && items.isEmpty()) {
                EmptyState(
                    when (collection) {
                        Collection.All -> R.string.empty_photos
                        Collection.Trash -> R.string.empty_trash
                        is Collection.Search -> R.string.no_matching_photos
                        else -> R.string.empty_album
                    },
                )
            } else {
                val trash = collection == Collection.Trash
                val resources = LocalResources.current
                val captions =
                    if (trash) {
                        items.associate {
                            it.id to
                                TrashInfo.daysLeft(it.expires, now()).let { d -> resources.getQuantityString(R.plurals.days_left, d, d) }
                        }
                    } else {
                        emptyMap()
                    }
                MediaGrid(
                    sections = sections,
                    items = items,
                    selection = state.selection,
                    onOpen = onOpen,
                    onToggle = actions::toggle,
                    onToggleDay = if (pickMode) null else actions::toggleDay,
                    caption = if (trash) ({ captions[it.id] }) else null,
                    contentPadding = PaddingValues(bottom = 16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    trash: Boolean,
    albums: List<Album>,
    actions: GridActions,
) {
    var menu by remember { mutableStateOf(false) }
    // true = move, false = copy, while the album picker is open.
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = actions::clearSelection) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        actions = {
            val action: @Composable (Int, Int, () -> Unit) -> Unit = { icon, label, onClick ->
                IconButton(onClick = onClick) { Icon(painterResource(icon), contentDescription = stringResource(label)) }
            }
            if (trash) {
                action(DesignR.drawable.ic_restore_from_trash, R.string.action_restore, actions::restoreSelection)
                action(DesignR.drawable.ic_delete_forever, R.string.action_delete_forever, actions::deleteSelectionForever)
            } else {
                action(DesignR.drawable.ic_share, R.string.action_share, actions::shareSelection)
                action(DesignR.drawable.ic_favorite, R.string.action_favorite, actions::favoriteSelection)
                action(DesignR.drawable.ic_delete, R.string.action_trash, actions::trashSelection)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_move_to_album)) }, onClick = {
                            menu = false
                            picking = true
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_copy_to_album)) }, onClick = {
                            menu = false
                            picking = false
                        })
                    }
                }
            }
        },
    )
    picking?.let { move ->
        AlbumPicker(
            albums,
            title = stringResource(if (move) R.string.action_move_to_album else R.string.action_copy_to_album),
            onDismiss = { picking = null },
        ) { path ->
            picking = null
            if (move) actions.moveSelection(path) else actions.copySelection(path)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsScreen(
    state: GalleryState,
    itemsOf: (Collection) -> List<MediaItem>,
    onOpen: (Collection) -> Unit,
    actions: GridActions,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_albums)) },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(R.string.collections)) }
            val collections =
                listOf(
                    Triple(Collection.Favorites, R.string.favorites, DesignR.drawable.ic_favorite),
                    Triple(Collection.Videos, R.string.videos, DesignR.drawable.ic_video_library),
                    Triple(Collection.Trash, R.string.trash, DesignR.drawable.ic_delete),
                )
            items(collections, key = { it.second }) { (collection, label, icon) ->
                val list = itemsOf(collection)
                AlbumTile(stringResource(label), list.size, list.firstOrNull(), icon) { onOpen(collection) }
            }
            if (state.albums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Heading(stringResource(R.string.folders)) }
                items(state.albums, key = { it.id }) { album: Album ->
                    AlbumTile(
                        album.name,
                        album.count,
                        album.cover,
                        DesignR.drawable.ic_photo_album,
                    ) { onOpen(Collection.Folder(album.id, album.name)) }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun AlbumTile(
    name: String,
    count: Int,
    cover: MediaItem?,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        // The text sits inside the corner rounding (which shapes the ripple), not under it.
        Column(Modifier.padding(bottom = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        Thumbnail(cover.uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.item_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(text: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(DesignR.drawable.ic_image),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Shown until media access is granted; also the "limited access" banner content. */
@Composable
fun PermissionScreen(
    denied: Boolean,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_photo_library),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(if (denied) R.string.permission_denied else R.string.permission_body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = if (denied) onSettings else onGrant) {
                    Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_grant))
                }
            }
        }
    }
}

/** Shown above the grid when the user granted access to only some photos. */
@Composable
fun LimitedAccessBanner(
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.limited_access), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onManage) { Text(stringResource(R.string.limited_manage)) }
        }
    }
}

private fun Collection.encode(): List<Any> =
    when (this) {
        Collection.All -> listOf("all")
        is Collection.Folder -> listOf("folder", id, name)
        Collection.Favorites -> listOf("favorites")
        Collection.Videos -> listOf("videos")
        Collection.Trash -> listOf("trash")
        is Collection.Search -> listOf("search", query)
    }

/** The saved-state key of an album's grid (one for search, whatever is typed). */
private fun Collection.stateKey(): String = if (this is Collection.Search) "album:search" else "album:" + encode().joinToString("/")

private fun decodeCollection(v: List<Any>): Collection =
    when (v[0]) {
        "folder" -> Collection.Folder(v[1] as Long, v[2] as String)
        "favorites" -> Collection.Favorites
        "videos" -> Collection.Videos
        "trash" -> Collection.Trash
        "search" -> Collection.Search(v.getOrNull(1) as? String ?: "")
        else -> Collection.All
    }

private val CollectionSaver: Saver<Collection?, Any> =
    Saver(save = { it?.encode() ?: emptyList<Any>() }, restore = { v ->
        (v as List<*>).filterNotNull().takeIf { it.isNotEmpty() }?.let(::decodeCollection)
    })

private val ViewingSaver: Saver<Pair<Collection, Long>?, Any> =
    Saver(
        save = { it?.let { (c, id) -> listOf(id) + c.encode() } ?: emptyList<Any>() },
        restore = { v ->
            val list = (v as List<*>).filterNotNull()
            if (list.isEmpty()) null else decodeCollection(list.drop(1)) to (list[0] as Long)
        },
    )

/** Search's top bar: back, and the search field (focused when it opens). */
@Composable
private fun SearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
) {
    TesseraSearchTopBar(
        query = query,
        onQuery = onQuery,
        placeholder = stringResource(R.string.search_hint),
        onBack = onBack,
        focusOnOpen = true,
    )
}
