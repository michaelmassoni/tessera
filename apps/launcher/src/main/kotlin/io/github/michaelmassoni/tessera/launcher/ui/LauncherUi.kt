package io.github.michaelmassoni.tessera.launcher.ui

import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.michaelmassoni.tessera.launcher.R
import io.github.michaelmassoni.tessera.launcher.engine.App
import io.github.michaelmassoni.tessera.launcher.engine.AppIndex
import io.github.michaelmassoni.tessera.launcher.engine.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Everything the home screen can do. */
interface LauncherActions {
    fun launch(app: App)

    /** Icon drawable and whether it is a monochrome layer to tint. */
    fun icon(app: App): Pair<Drawable?, Boolean>

    fun shortcuts(app: App): List<ShortcutInfo>

    fun shortcutIcon(shortcut: ShortcutInfo): Drawable?

    fun startShortcut(shortcut: ShortcutInfo)

    fun pin(app: App)

    fun unpin(app: App)

    fun move(
        app: App,
        delta: Int,
    )

    fun appInfo(app: App)

    fun uninstall(app: App)

    fun openClock()

    fun openCalendar()

    fun makeDefault()

    fun openSettings()
}

data class LauncherState(
    val apps: List<App> = emptyList(),
    val pinned: List<App> = emptyList(),
    val isDefault: Boolean = true,
    val darkText: Boolean = false,
)

/**
 * The home screen over the wallpaper, with the app drawer sliding over it. Text drawn on the
 * wallpaper uses the Material baseline on-surface colour of whichever scheme reads on it
 * (the wallpaper's own colour hints decide), so it stays legible on any wallpaper.
 */
@Composable
fun LauncherScreen(
    state: LauncherState,
    actions: LauncherActions,
    modifier: Modifier = Modifier,
    drawerOpenInitially: Boolean = false,
) {
    var drawer by rememberSaveable { mutableStateOf(drawerOpenInitially) }
    var searchFocus by remember { mutableStateOf(false) }
    // Home ignores back; the drawer closes on back.
    BackHandler(true) { drawer = false }
    val onWallpaper = if (state.darkText) lightColorScheme().onSurface else darkColorScheme().onSurface

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if (total <
                        -80
                    ) {
                        drawer = true
                    }
                }) { _, amount -> total += amount }
            },
    ) {
        val columns =
            if (maxWidth < 600.dp) {
                4
            } else if (maxWidth < 840.dp) {
                5
            } else {
                6
            }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Glance(onWallpaper, actions)
            if (!state.isDefault) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.not_default),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = actions::makeDefault) { Text(stringResource(R.string.make_default)) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.pinned.isEmpty()) {
                Text(
                    stringResource(R.string.pin_hint),
                    color = onWallpaper,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                AppGrid(state.pinned, columns, onWallpaper, pinned = true, state, actions, scrollable = false)
            }
            SearchPill(onClick = {
                searchFocus = true
                drawer = true
            })
        }
        AnimatedVisibility(drawer, enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
            Drawer(state, columns, actions, focusSearch = searchFocus, onClose = {
                drawer = false
                searchFocus = false
            })
        }
    }
}

@Composable
private fun Glance(
    color: Color,
    actions: LauncherActions,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text(
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(Date(now)),
            color = color,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            modifier = Modifier.clickable(onClick = actions::openClock),
        )
        Text(
            DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)),
            color = color,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(onClick = actions::openCalendar),
        )
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null)
            Text(
                stringResource(R.string.search_apps),
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Drawer(
    state: LauncherState,
    columns: Int,
    actions: LauncherActions,
    focusSearch: Boolean,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(state.apps, query) { AppIndex.search(state.apps, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) focus.requestFocus() }
    val grid = rememberLazyGridState()
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                        if (total > 120 && grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0) onClose()
                    }) { _, amount -> total += amount }
                },
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(actions::launch) }),
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            )
            AppGrid(
                results,
                columns,
                MaterialTheme.colorScheme.onSurface,
                pinned = false,
                state,
                actions,
                scrollable = true,
                gridState = grid,
            )
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<App>,
    columns: Int,
    labelColor: Color,
    pinned: Boolean,
    state: LauncherState,
    actions: LauncherActions,
    scrollable: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    if (scrollable) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.key.toString() }) { app -> AppCell(app, labelColor, pinned, state, actions) }
        }
    } else {
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            apps.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app -> Box(Modifier.weight(1f)) { AppCell(app, labelColor, pinned, state, actions) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: App,
    labelColor: Color,
    pinned: Boolean,
    state: LauncherState,
    actions: LauncherActions,
) {
    var menu by remember { mutableStateOf(false) }
    val description = if (app.work) stringResource(R.string.work_app, app.label) else app.label
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {
                    actions.launch(app)
                }, onLongClick = { menu = true }, onLongClickLabel = stringResource(R.string.app_options))
                .padding(vertical = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, actions)
            Text(
                app.label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
            )
        }
        AppMenu(menu, app, pinned, isPinned = state.pinned.any { it.key == app.key }, actions) { menu = false }
    }
}

@Composable
private fun AppIcon(
    app: App,
    actions: LauncherActions,
) {
    val size = 56.dp
    val px = with(LocalDensity.current) { size.roundToPx() }
    val loaded by produceState<Pair<ImageBitmap?, Boolean>>(null to false, app.key) {
        value =
            withContext(Dispatchers.IO) {
                val (drawable, mono) = actions.icon(app)
                drawable?.toBitmap(px, px)?.asImageBitmap() to mono
            }
    }
    val (bitmap, mono) = loaded
    if (bitmap == null) {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape))
    } else if (mono) {
        // Themed icon: the monochrome layer in theme colours, like the system's themed icons.
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Image(
                bitmap,
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                modifier =
                    Modifier.size(size * 1.5f),
            )
        }
    } else {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun AppMenu(
    open: Boolean,
    app: App,
    onHome: Boolean,
    isPinned: Boolean,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val shortcuts by produceState(emptyList<ShortcutInfo>(), app.key) { value = withContext(Dispatchers.IO) { actions.shortcuts(app) } }
    DropdownMenu(true, onDismissRequest = onDismiss) {
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = { Text((shortcut.shortLabel ?: shortcut.longLabel ?: "").toString()) },
                leadingIcon = { ShortcutIcon(shortcut, actions) },
                onClick = {
                    onDismiss()
                    actions.startShortcut(shortcut)
                },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        val item: @Composable (Int, Int, () -> Unit) -> Unit = { label, icon, onClick ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                onClick = {
                    onDismiss()
                    onClick()
                },
            )
        }
        if (isPinned) {
            item(R.string.unpin, DesignR.drawable.ic_close) { actions.unpin(app) }
            if (onHome) {
                item(R.string.move_left, DesignR.drawable.ic_chevron_left) { actions.move(app, -1) }
                item(R.string.move_right, DesignR.drawable.ic_chevron_right) { actions.move(app, 1) }
            }
        } else {
            item(R.string.pin, DesignR.drawable.ic_push_pin) { actions.pin(app) }
        }
        item(R.string.app_info, DesignR.drawable.ic_info) { actions.appInfo(app) }
        if (!app.work) item(R.string.uninstall, DesignR.drawable.ic_delete) { actions.uninstall(app) }
    }
}

@Composable
private fun ShortcutIcon(
    shortcut: ShortcutInfo,
    actions: LauncherActions,
) {
    val px = with(LocalDensity.current) { 24.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, shortcut.id) {
        value = withContext(Dispatchers.IO) { actions.shortcutIcon(shortcut)?.toBitmap(px, px)?.asImageBitmap() }
    }
    icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) } ?: Spacer(Modifier.width(24.dp))
}
