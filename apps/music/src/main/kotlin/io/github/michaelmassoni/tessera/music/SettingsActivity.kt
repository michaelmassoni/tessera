package io.github.michaelmassoni.tessera.music

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.core.settings.rememberLocalNetworkAccess
import io.github.michaelmassoni.tessera.music.data.DownloadStore
import io.github.michaelmassoni.tessera.music.data.Downloads
import io.github.michaelmassoni.tessera.music.data.LyricsRepository
import io.github.michaelmassoni.tessera.music.data.MusicSettings
import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.ServerPlayback
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.StreamQuality
import io.github.michaelmassoni.tessera.music.server.signIn
import io.github.michaelmassoni.tessera.music.ui.ServerSignInFields
import io.github.michaelmassoni.tessera.music.ui.SignInFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon =
        LauncherIcon.of(
            glyph = R.drawable.ic_launcher_foreground,
            defaultBackground = R.color.ic_launcher_background,
            lightBackground = R.color.ic_launcher_light_background,
            lightForeground = R.color.ic_launcher_light_foreground,
            darkBackground = R.color.ic_launcher_dark_background,
            darkForeground = R.color.ic_launcher_dark_foreground,
        )

    @Composable
    override fun categories(): List<SettingsCategory> {
        val context = LocalContext.current
        val store = remember { ServerStore(context) }
        var source by remember { mutableStateOf(store.source) }
        var account by remember { mutableStateOf(store.account) }
        var quality by remember { mutableStateOf(store.quality) }
        val settings = remember { MusicSettings(context) }
        var online by remember { mutableStateOf(settings.onlineLyrics) }
        val sourceSummary =
            when (source) {
                MusicSource.Local -> {
                    stringResource(R.string.source_local)
                }

                MusicSource.Server -> {
                    val q = if (quality == StreamQuality.Original) stringResource(R.string.quality_original_summary) else quality.label()
                    listOfNotNull(account?.address, q).joinToString(" · ")
                }
            }
        return listOfNotNull(
            SettingsCategory(
                key = "source",
                title = stringResource(R.string.settings_source),
                icon = DesignR.drawable.ic_library_music,
                summary = sourceSummary,
            ) {
                SourcePage(
                    store = store,
                    source = source,
                    account = account,
                    quality = quality,
                    onSource = { source = it },
                    onAccount = { account = it },
                    onQuality = { quality = it },
                )
            },
            // Downloads for offline listening: only songs from a server can be downloaded.
            if (source == MusicSource.Server) {
                SettingsCategory(
                    key = "downloads",
                    title = stringResource(R.string.settings_downloads),
                    icon = DesignR.drawable.ic_download,
                    summary = stringResource(R.string.settings_downloads_summary),
                ) { DownloadsPage() }
            } else {
                null
            },
            SettingsCategory(
                key = "lyrics",
                title = stringResource(R.string.lyrics),
                icon = DesignR.drawable.ic_lyrics,
                summary = stringResource(if (online) R.string.lyrics_online_on else R.string.lyrics_online_off),
            ) {
                val scope = rememberCoroutineScope()
                SwitchSetting(
                    title = stringResource(R.string.look_up_lyrics),
                    checked = online,
                    onCheckedChange = { on ->
                        settings.onlineLyrics = on
                        online = on
                        // Songs found to have none are looked at again.
                        if (on) scope.launch(Dispatchers.IO) { LyricsRepository(context).forgetMisses() }
                    },
                    summary = stringResource(R.string.online_lyrics_summary),
                )
            },
        )
    }
}

@Composable
private fun StreamQuality.label(): String =
    if (this == StreamQuality.Original) stringResource(R.string.quality_original) else stringResource(R.string.quality_kbps, kbps)

@Composable
private fun SourcePage(
    store: ServerStore,
    source: MusicSource,
    account: ServerAccount?,
    quality: StreamQuality,
    onSource: (MusicSource) -> Unit,
    onAccount: (ServerAccount?) -> Unit,
    onQuality: (StreamQuality) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signingIn by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.selectableGroup()) {
        RadioSetting(stringResource(R.string.source_local), source == MusicSource.Local, onSelect = {
            store.source = MusicSource.Local
            onSource(MusicSource.Local)
        })
        RadioSetting(stringResource(R.string.source_server), source == MusicSource.Server, onSelect = {
            // A server already signed in to is used again; otherwise sign in first.
            if (account != null && store.password() != null) {
                store.source = MusicSource.Server
                onSource(MusicSource.Server)
            } else {
                signingIn = true
            }
        })
    }
    if (source == MusicSource.Server) {
        InfoSetting(
            title = stringResource(R.string.settings_server),
            summary = account?.let { stringResource(R.string.settings_server_summary, it.username, it.address) }.orEmpty(),
            onClick = { signingIn = true },
            external = false,
        )
        ChoiceSetting(
            title = stringResource(R.string.settings_quality),
            options = StreamQuality.entries,
            selected = quality,
            label = { it.label() },
            onSelect = {
                store.quality = it
                onQuality(it)
            },
        )
    }

    if (signingIn) {
        SignInDialog(
            initial = account?.let { SignInFields(it.address, it.username) } ?: SignInFields(),
            onDone = {
                signingIn = false
                onAccount(store.account)
                onSource(store.source)
            },
            onDismiss = { signingIn = false },
        )
    }
}

@Composable
private fun SignInDialog(
    initial: SignInFields,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = SignInSaver) { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<ServerProblem?>(null) }
    var working by remember { mutableStateOf(false) }
    val network = rememberLocalNetworkAccess()

    fun connect() {
        if (working) return
        working = true
        problem = null
        scope.launch {
            problem =
                if (network.allow(fields.address)) {
                    signIn(context, fields.address, fields.username, fields.password)
                } else {
                    ServerProblem.LocalNetwork
                }
            working = false
            if (problem == null) onDone()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.setup_server_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ServerSignInFields(fields, { fields = it }, problem, enabled = !working, onDone = ::connect)
            }
        },
        confirmButton = {
            TextButton(onClick = ::connect, enabled = !working && fields.address.isNotBlank() && fields.username.isNotBlank()) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.connect))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Settings > Downloads: how and when songs download, the space they take, and the play cache. */
@Composable
private fun DownloadsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DownloadStore(context) }
    var wifiOnly by remember { mutableStateOf(store.wifiOnly) }
    var quality by remember { mutableStateOf(store.quality) }
    var downloads by remember { mutableLongStateOf(-1L) }
    var cache by remember { mutableLongStateOf(-1L) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            downloads = Downloads.bytes(context)
            cache = ServerPlayback.cacheBytes(context)
        }
    }
    SwitchSetting(
        title = stringResource(R.string.settings_wifi_only),
        checked = wifiOnly,
        onCheckedChange = {
            store.wifiOnly = it
            wifiOnly = it
            Downloads.refresh(context)
            Downloads.sync(context)
        },
        summary = stringResource(R.string.settings_wifi_only_summary),
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_download_quality),
        options = StreamQuality.entries,
        selected = quality,
        label = { it.label() },
        onSelect = {
            store.quality = it
            quality = it
        },
    )
    InfoSetting(
        title = stringResource(R.string.settings_remove_downloads),
        summary =
            if (downloads < 0) "" else stringResource(R.string.settings_downloads_used, Formatter.formatShortFileSize(context, downloads)),
        onClick = { confirmRemove = true },
        external = false,
    )
    InfoSetting(
        title = stringResource(R.string.settings_clear_cache),
        summary = if (cache < 0) "" else stringResource(R.string.settings_cache_used, Formatter.formatShortFileSize(context, cache)),
        onClick = {
            scope.launch {
                cache =
                    withContext(Dispatchers.IO) {
                        ServerPlayback.clearCache(context)
                        ServerPlayback.cacheBytes(context)
                    }
            }
        },
        external = false,
    )
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.settings_remove_downloads)) },
            text = { Text(stringResource(R.string.remove_downloads_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        downloads =
                            withContext(Dispatchers.IO) {
                                Downloads.removeAll(context)
                                0L
                            }
                    }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
