package io.github.michaelmassoni.tessera.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.files.data.AndroidFreeSpace
import io.github.michaelmassoni.tessera.files.data.FileIntents
import io.github.michaelmassoni.tessera.files.data.SharedPreferencesFilesSettings
import io.github.michaelmassoni.tessera.files.data.StorageVolumes
import io.github.michaelmassoni.tessera.files.data.Transfers
import io.github.michaelmassoni.tessera.files.data.ViewOptions
import io.github.michaelmassoni.tessera.files.engine.Archives
import io.github.michaelmassoni.tessera.files.engine.CompressOptions
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Trash
import io.github.michaelmassoni.tessera.files.ui.FilesActions
import io.github.michaelmassoni.tessera.files.ui.FilesScreen
import io.github.michaelmassoni.tessera.files.ui.PermissionScreen
import kotlinx.coroutines.Dispatchers
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: FilesViewModel by viewModels {
        viewModelFactory {
            initializer {
                FilesViewModel(
                    settings = filesSettings(),
                    volumes = { StorageVolumes.list(applicationContext) },
                    io = Dispatchers.IO,
                    ops = FileOperations(freeSpace = AndroidFreeSpace(applicationContext)),
                    trash = Trash(File(filesDir, "trash.json")),
                    // Copies, moves and zips run in a foreground service, so they finish after the app is left.
                    startTransfer = { kind, files, target, options ->
                        askForNotifications()
                        Transfers.start(applicationContext, kind, files, target, options)
                    },
                )
            }
        }
    }

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** The first time a transfer starts: ask to show its progress (Android 13 and later), once. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit { putBoolean("asked_notifications", true) }
        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = Actions(this, viewModel)
        setTesseraContent {
            SetupGate(filesSetup) {
                var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
                LifecycleResumeEffect(Unit) {
                    granted = Environment.isExternalStorageManager()
                    if (granted) viewModel.refresh()
                    onPauseOrDispose { }
                }
                if (!granted) {
                    PermissionScreen(onGrant = {
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()))
                    })
                } else {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    FilesScreen(state = state, events = viewModel.events, actions = actions)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadOptions()
    }
}

internal fun Context.filesSettings() = SharedPreferencesFilesSettings(getSharedPreferences("settings", Context.MODE_PRIVATE))

private class Actions(
    private val activity: ComponentActivity,
    private val vm: FilesViewModel,
) : FilesActions {
    override fun open(dir: File?) = vm.open(dir)

    override fun back(): Boolean = vm.back()

    override fun refresh() = vm.refresh()

    override fun openFile(file: File) {
        if (!FileIntents.open(activity, file)) vm.reportNoApp()
    }

    override fun share(files: List<File>) = FileIntents.share(activity, files, activity.getString(R.string.action_share))

    override fun setOptions(options: ViewOptions) = vm.setOptions(options)

    override fun toggleSelection(file: File) = vm.toggleSelection(file)

    override fun selectAll() = vm.selectAll()

    override fun clearSelection() = vm.clearSelection()

    override fun copySelection() = vm.copySelection()

    override fun moveSelection() = vm.moveSelection()

    override fun paste() = vm.paste()

    override fun cancelClipboard() = vm.cancelClipboard()

    override fun cancelOperation() = vm.cancelRunningOperation()

    override fun delete(files: Collection<File>) = vm.delete(files)

    override fun openTrash() = vm.openTrash()

    override fun toggleTrashSelection(id: String) = vm.toggleTrashSelection(id)

    override fun selectAllTrash() = vm.selectAllTrash()

    override fun restore(ids: Collection<String>) = vm.restore(ids)

    override fun deleteForever(ids: Collection<String>?) = vm.deleteForever(ids)

    override fun rename(
        file: File,
        name: String,
    ): NameProblem? = vm.rename(file, name)

    override fun createFolder(name: String): NameProblem? = vm.createFolder(name)

    override fun openSearch() = vm.openSearch()

    override fun search(query: String) = vm.search(query)

    override fun closeSearch() = vm.closeSearch()

    override fun openSettings() = activity.startActivity(Intent(activity, SettingsActivity::class.java))

    override fun compressSelection(options: CompressOptions) = vm.compressSelection(options)

    override fun extract(archive: File) = vm.extract(archive)

    override fun archiveSummary(archive: File) = runCatching { Archives.summary(archive) }.getOrNull()

    override fun toggleBookmark(dir: File) = vm.toggleBookmark(dir)
}
