package io.github.michaelmassoni.tessera.music

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupIcon
import io.github.michaelmassoni.tessera.core.settings.SetupPage
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.core.settings.rememberLocalNetworkAccess
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.server.MusicSource
import io.github.michaelmassoni.tessera.music.server.ServerStore
import io.github.michaelmassoni.tessera.music.server.signIn
import io.github.michaelmassoni.tessera.music.ui.ServerSignInFields
import io.github.michaelmassoni.tessera.music.ui.SignInFields
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Music's first run: where the music is (this phone or a server), then audio access for music
 * on the phone, or the server's sign-in; then restore.
 */
internal val musicSetup =
    SetupSpec(
        custom =
            listOf(
                SetupCustom { next -> SourcePage(next) },
                SetupCustom(wanted = { ServerStore(it).source == MusicSource.Server }) { next -> SignInPage(next) },
            ),
        steps =
            listOf(
                SetupStep(
                    DesignR.drawable.ic_music_note,
                    R.string.setup_access_title,
                    R.string.setup_access_text,
                    SetupGrant.Permissions(
                        listOf(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            },
                        ),
                    ),
                    wanted = { ServerStore(it).source == MusicSource.Local },
                ),
            ),
        offerRestore = true,
    )

@Composable
private fun SourcePage(next: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ServerStore(context) }
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_library_music) },
        title = stringResource(R.string.setup_source_title),
        text = stringResource(R.string.setup_source_text),
        body = {
            SourceChoice(DesignR.drawable.ic_smartphone, R.string.source_local, R.string.source_local_text) {
                store.source = MusicSource.Local
                next()
            }
            SourceChoice(DesignR.drawable.ic_dns, R.string.source_server, R.string.source_server_text) {
                store.source = MusicSource.Server
                next()
            }
        },
    ) {}
}

@Composable
private fun SourceChoice(
    icon: Int,
    title: Int,
    text: Int,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignInPage(next: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = SignInSaver) { mutableStateOf(SignInFields()) }
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
            if (problem == null) next()
        }
    }
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_dns) },
        title = stringResource(R.string.setup_server_title),
        text = stringResource(R.string.setup_server_text),
        body = { ServerSignInFields(fields, { fields = it }, problem, enabled = !working, onDone = ::connect) },
    ) {
        Button(
            onClick = ::connect,
            enabled = !working && fields.address.isNotBlank() && fields.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.connect))
            }
        }
        TextButton(onClick = {
            ServerStore(context).source = MusicSource.Local
            next()
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.use_local_instead)) }
    }
}

/** Keeps what was typed across rotation; the password too, since it never leaves the process. */
internal val SignInSaver =
    androidx.compose.runtime.saveable.listSaver<SignInFields, String>(
        save = { listOf(it.address, it.username, it.password) },
        restore = { SignInFields(it[0], it[1], it[2]) },
    )

@TesseraPreviews
@Composable
private fun SourcePagePreview() {
    TesseraTheme(dynamicColor = false) { Surface { SourcePage {} } }
}

@TesseraPreviews
@Composable
private fun SignInPagePreview() {
    TesseraTheme(dynamicColor = false) { Surface { SignInPage {} } }
}
