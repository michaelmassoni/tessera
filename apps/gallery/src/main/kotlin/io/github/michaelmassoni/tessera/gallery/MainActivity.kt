package io.github.michaelmassoni.tessera.gallery

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.TesseraSettings
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.gallery.data.EditorIO
import io.github.michaelmassoni.tessera.gallery.data.ExifDetails
import io.github.michaelmassoni.tessera.gallery.data.MediaRepository
import io.github.michaelmassoni.tessera.gallery.engine.AlbumTarget
import io.github.michaelmassoni.tessera.gallery.engine.DaySection
import io.github.michaelmassoni.tessera.gallery.engine.EditState
import io.github.michaelmassoni.tessera.gallery.engine.MediaItem
import io.github.michaelmassoni.tessera.gallery.ui.EditorActions
import io.github.michaelmassoni.tessera.gallery.ui.GalleryApp
import io.github.michaelmassoni.tessera.gallery.ui.GridActions
import io.github.michaelmassoni.tessera.gallery.ui.LimitedAccessBanner
import io.github.michaelmassoni.tessera.gallery.ui.PermissionScreen
import io.github.michaelmassoni.tessera.gallery.ui.Viewer
import io.github.michaelmassoni.tessera.gallery.ui.ViewerActions
import io.github.michaelmassoni.tessera.gallery.ui.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** MediaStore changes arrive one file at a time; reload once they settle. */
private const val CHANGE_DELAY_MS = 500L

class MainActivity : ComponentActivity() {
    private val repository by lazy { MediaRepository(contentResolver) }
    private val viewModel: GalleryViewModel by viewModels {
        viewModelFactory { initializer { GalleryViewModel(repository, Dispatchers.IO) } }
    }
    private var access by mutableStateOf(Access.None)
    private var denied by mutableStateOf(false)
    private var pickMode by mutableStateOf(false)
    private var pickMultiple by mutableStateOf(false)

    /** A single item opened by another app with ACTION_VIEW. */
    private var external by mutableStateOf<MediaItem?>(null)

    private enum class Access { None, Partial, Full }

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = viewModel.refresh(after = CHANGE_DELAY_MS)
        }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            access = access()
            denied = access == Access.None && result.isNotEmpty()
            if (access != Access.None) viewModel.refresh()
        }

    /** Asked before the editor saves over an original; [pendingWrite] runs if the user allows it. */
    private var pendingWrite: (() -> Unit)? = null
    private var pendingWriteCancelled: (() -> Unit)? = null
    private val writeRequest =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val run = pendingWrite
            val cancel = pendingWriteCancelled
            pendingWrite = null
            pendingWriteCancelled = null
            if (result.resultCode == Activity.RESULT_OK) run?.invoke() else cancel?.invoke()
        }

    private val mediaRequest =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            viewModel.clearSelection()
            viewModel.refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        access = access()
        handleIntent(intent)
        val grid = Grid()
        val viewer = ViewerImpl()
        val editor = EditorImpl()
        setTesseraContent {
            SetupGate(gallerySetup(permissions()), skip = external != null) {
                val settings by TesseraSettings.get(this).shared.collectAsStateWithLifecycle()
                val single = external
                when {
                    single != null -> {
                        TesseraTheme(darkTheme = true, dynamicColor = settings.theme.dynamicColor, pureBlack = true) {
                            Viewer(
                                listOf(single),
                                0,
                                object : ViewerActions by viewer {
                                    override fun close() = finish()
                                },
                            )
                        }
                    }

                    access == Access.None -> {
                        PermissionScreen(
                            denied = denied,
                            onGrant = ::askForAccess,
                            onSettings = {
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
                                )
                            },
                        )
                    }

                    else -> {
                        val state by viewModel.state.collectAsStateWithLifecycle()
                        Column {
                            if (access ==
                                Access.Partial
                            ) {
                                LimitedAccessBanner(onManage = ::askForAccess, modifier = Modifier.statusBarsPadding())
                            }
                            GalleryApp(
                                state = state,
                                itemsOf = viewModel::items,
                                gridActions = grid,
                                viewerActions = viewer,
                                dynamicColor = settings.theme.dynamicColor,
                                editorActions = if (pickMode) null else editor,
                                pickMode = pickMode,
                                pickMultiple = pickMultiple,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val now = access()
        if (now != access) access = now
        if (access != Access.None) viewModel.refresh()
    }

    // Watched only while visible: any app writing a file would otherwise reload the whole library in
    // the background, and onResume reloads anyway.
    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(observer)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val type = intent.type ?: contentResolver.getType(uri).orEmpty()
                external =
                    MediaItem(
                        id = -1,
                        uri = uri.toString(),
                        isVideo = type.startsWith("video/"),
                        mimeType = type,
                        name = uri.lastPathSegment.orEmpty(),
                        date = System.currentTimeMillis(),
                        bucketId = 0,
                        bucketName = "",
                    )
            }

            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> {
                pickMode = true
                pickMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
        }
    }

    private fun permissions(): Array<String> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            }

            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun access(): Access =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO) -> Access.Full

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Partial

            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.Full

            else -> Access.None
        }

    private fun askForAccess() = requestPermissions.launch(permissions())

    private fun start(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /** Asks the system to let the app change [items] (one dialog for all), then runs [then]. */
    private fun withWriteAccess(
        items: List<MediaItem>,
        then: () -> Unit,
    ) {
        pendingWrite = then
        pendingWriteCancelled = null
        writeRequest.launch(
            IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, items.map { it.uri.toUri() }).intentSender).build(),
        )
    }

    private fun toastCount(
        plural: Int,
        count: Int,
    ) = Toast.makeText(this, resources.getQuantityString(plural, count, count), Toast.LENGTH_SHORT).show()

    private fun launch(request: PendingIntent) = mediaRequest.launch(IntentSenderRequest.Builder(request.intentSender).build())

    private fun share(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri.toUri() })
        val type =
            if (items.all { it.isVideo }) {
                "video/*"
            } else if (items.none { it.isVideo }) {
                "image/*"
            } else {
                "*/*"
            }
        val send =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private inner class Grid : GridActions {
        override fun toggle(item: MediaItem) = viewModel.toggleSelection(item.id)

        override fun toggleDay(section: DaySection) = viewModel.toggleGroup(section.items.map { it.id })

        override fun clearSelection() = viewModel.clearSelection()

        override fun shareSelection() = share(viewModel.selectedItems())

        override fun favoriteSelection() {
            val items = viewModel.selectedItems()
            if (items.isEmpty()) return
            launch(MediaStore.createFavoriteRequest(contentResolver, items.map { it.uri.toUri() }, !items.all { it.favorite }))
        }

        override fun trashSelection() = trash(viewModel.selectedItems(), true)

        override fun restoreSelection() = trash(viewModel.selectedItems(), false)

        override fun deleteSelectionForever() {
            val items = viewModel.selectedItems()
            if (items.isNotEmpty()) launch(MediaStore.createDeleteRequest(contentResolver, items.map { it.uri.toUri() }))
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun pick(items: List<MediaItem>) {
            if (items.isEmpty()) return
            val uris = items.map { it.uri.toUri() }
            val result = Intent().setData(uris[0]).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (uris.size > 1) {
                result.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        override fun moveSelection(relativePath: String) {
            val selected = viewModel.selectedItems()
            val items = AlbumTarget.toMove(selected, relativePath).filter { AlbumTarget.allowed(it, relativePath) }
            if (items.size < AlbumTarget.toMove(selected, relativePath).size) {
                Toast.makeText(this@MainActivity, R.string.move_not_allowed, Toast.LENGTH_LONG).show()
            }
            if (items.isEmpty()) return
            withWriteAccess(items) {
                lifecycleScope.launch {
                    val moved = withContext(Dispatchers.IO) { repository.move(items, relativePath) }
                    toastCount(R.plurals.moved_count, moved)
                    viewModel.clearSelection()
                    viewModel.refresh()
                }
            }
        }

        override fun copySelection(relativePath: String) {
            val selected = viewModel.selectedItems()
            val items = selected.filter { AlbumTarget.allowed(it, relativePath) }
            if (items.size < selected.size) Toast.makeText(this@MainActivity, R.string.move_not_allowed, Toast.LENGTH_LONG).show()
            if (items.isEmpty()) return
            lifecycleScope.launch {
                val copied = withContext(Dispatchers.IO) { repository.copy(items, relativePath) }
                toastCount(R.plurals.copied_count, copied)
                viewModel.clearSelection()
                viewModel.refresh()
            }
        }

        private fun trash(
            items: List<MediaItem>,
            trashed: Boolean,
        ) {
            if (items.isNotEmpty()) launch(MediaStore.createTrashRequest(contentResolver, items.map { it.uri.toUri() }, trashed))
        }
    }

    private inner class ViewerImpl : ViewerActions {
        override fun close() = Unit // Replaced by the caller.

        override fun share(item: MediaItem) = share(listOf(item))

        override fun toggleFavorite(item: MediaItem) {
            if (item.id < 0) return
            launch(MediaStore.createFavoriteRequest(contentResolver, listOf(item.uri.toUri()), !item.favorite))
        }

        // Read access only: Gallery can't write other apps' media, and granting write it doesn't
        // hold throws a SecurityException. Editors save their result as a new file.
        private fun editIntent(item: MediaItem) =
            Intent(Intent.ACTION_EDIT)
                .setDataAndType(item.uri.toUri(), item.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        override fun canEdit(item: MediaItem): Boolean = packageManager.queryIntentActivities(editIntent(item), 0).isNotEmpty()

        override fun edit(item: MediaItem) = start(Intent.createChooser(editIntent(item), getString(R.string.action_edit)))

        override fun setWallpaper(
            item: MediaItem,
            target: WallpaperTarget,
        ) {
            val flags =
                when (target) {
                    WallpaperTarget.Home -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.Lock -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.Both -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
            lifecycleScope.launch {
                val ok =
                    withContext(Dispatchers.IO) {
                        try {
                            contentResolver.openInputStream(item.uri.toUri())?.use { stream ->
                                WallpaperManager.getInstance(this@MainActivity).setStream(stream, null, true, flags)
                            } != null
                        } catch (_: IOException) {
                            false
                        } catch (_: SecurityException) {
                            false
                        }
                    }
                Toast.makeText(this@MainActivity, if (ok) R.string.wallpaper_set else R.string.wallpaper_failed, Toast.LENGTH_SHORT).show()
            }
        }

        override fun trash(item: MediaItem) {
            if (item.id < 0) return
            launch(MediaStore.createTrashRequest(contentResolver, listOf(item.uri.toUri()), true))
        }

        override fun rotate(item: MediaItem) {
            if (item.id < 0 || !AlbumTarget.canRotate(item)) return
            withWriteAccess(listOf(item)) {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { repository.rotate(item) }
                    if (!ok) Toast.makeText(this@MainActivity, R.string.rotate_failed, Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                }
            }
        }

        override fun exif(item: MediaItem): ExifDetails? = repository.exif(item.uri.toUri())
    }

    private inner class EditorImpl : EditorActions {
        private val io by lazy { EditorIO(contentResolver) }
        private val external = ViewerImpl()

        override fun preview(item: MediaItem) = io.preview(item.uri.toUri())

        override fun histogram(bitmap: android.graphics.Bitmap) = io.histogram(bitmap)

        override fun saveCopy(
            item: MediaItem,
            state: EditState,
            onDone: (Boolean) -> Unit,
        ) = save(onDone, R.string.edit_saved) { io.saveCopy(item, state) }

        override fun overwrite(
            item: MediaItem,
            state: EditState,
            onDone: (Boolean) -> Unit,
        ) {
            // Gallery can only write other apps' photos once the system has asked the user.
            pendingWrite = {
                save(onDone, R.string.edit_saved_over) { io.overwrite(item, state) }
            }
            pendingWriteCancelled = { onDone(false) }
            writeRequest.launch(
                IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, listOf(item.uri.toUri())).intentSender).build(),
            )
        }

        override fun canEditElsewhere(item: MediaItem) = external.canEdit(item)

        override fun editElsewhere(item: MediaItem) = external.edit(item)

        private fun save(
            onDone: (Boolean) -> Unit,
            message: Int,
            block: () -> Unit,
        ) {
            lifecycleScope.launch {
                val ok =
                    withContext(
                        Dispatchers.IO,
                    ) { runCatching(block).onFailure { android.util.Log.w("Gallery", "Saving an edit failed", it) }.isSuccess }
                Toast.makeText(this@MainActivity, if (ok) message else R.string.edit_failed, Toast.LENGTH_SHORT).show()
                viewModel.refresh()
                onDone(ok)
            }
        }
    }
}
