package io.github.michaelmassoni.tessera.music

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.music.data.LyricsRepository
import io.github.michaelmassoni.tessera.music.data.MusicSettings
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.server.CacheSize
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
    override fun ColumnScope.AppSettings() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val store = remember { ServerStore(context) }
        var source by remember { mutableStateOf(store.source) }
        var account by remember { mutableStateOf(store.account) }
        var signingIn by rememberSaveable { mutableStateOf(false) }

        SettingsHeader(stringResource(R.string.settings_source))
        Column(Modifier.selectableGroup()) {
            RadioSetting(stringResource(R.string.source_local), source == MusicSource.Local, onSelect = {
                store.source = MusicSource.Local
                source = MusicSource.Local
            })
            RadioSetting(stringResource(R.string.source_server), source == MusicSource.Server, onSelect = {
                // A server already signed in to is used again; otherwise sign in first.
                if (account != null && store.password() != null) {
                    store.source = MusicSource.Server
                    source = MusicSource.Server
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

            var quality by remember { mutableStateOf(store.quality) }
            SettingsHeader(stringResource(R.string.settings_quality))
            Column(Modifier.selectableGroup()) {
                StreamQuality.entries.forEach { q ->
                    RadioSetting(
                        title =
                            if (q ==
                                StreamQuality.Original
                            ) {
                                stringResource(R.string.quality_original)
                            } else {
                                stringResource(R.string.quality_kbps, q.kbps)
                            },
                        selected = q == quality,
                        onSelect = {
                            store.quality = q
                            quality = q
                        },
                    )
                }
            }

            var size by remember { mutableStateOf(store.cacheSize) }
            var used by remember { mutableLongStateOf(-1L) }
            LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { ServerPlayback.cacheBytes(context) } }
            SettingsHeader(stringResource(R.string.settings_cache))
            Column(Modifier.selectableGroup()) {
                CacheSize.entries.forEach { c ->
                    RadioSetting(
                        title = Formatter.formatShortFileSize(context, c.bytes),
                        selected = c == size,
                        onSelect = {
                            store.cacheSize = c
                            size = c
                        },
                    )
                }
            }
            InfoSetting(
                title = stringResource(R.string.settings_clear_cache),
                summary =
                    if (used < 0) {
                        ""
                    } else {
                        stringResource(R.string.settings_cache_used, Formatter.formatShortFileSize(context, used))
                    },
                onClick = {
                    scope.launch {
                        used =
                            withContext(Dispatchers.IO) {
                                ServerPlayback.clearCache(context)
                                ServerPlayback.cacheBytes(context)
                            }
                    }
                },
                external = false,
            )
        }

        SettingsHeader(stringResource(R.string.lyrics))
        val settings = remember { MusicSettings(context) }
        var online by remember { mutableStateOf(settings.onlineLyrics) }
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

        if (signingIn) {
            SignInDialog(
                initial = account?.let { SignInFields(it.address, it.username) } ?: SignInFields(),
                onDone = {
                    signingIn = false
                    account = store.account
                    source = store.source
                },
                onDismiss = { signingIn = false },
            )
        }
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

    fun connect() {
        if (working) return
        working = true
        problem = null
        scope.launch {
            problem = signIn(context, fields.address, fields.username, fields.password)
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
