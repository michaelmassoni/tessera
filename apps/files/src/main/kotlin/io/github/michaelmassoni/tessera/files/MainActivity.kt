package io.github.michaelmassoni.tessera.files

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.michaelmassoni.tessera.files.data.ViewOptions
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
                )
            }
        }
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
}
