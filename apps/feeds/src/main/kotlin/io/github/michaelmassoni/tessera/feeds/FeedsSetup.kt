package io.github.michaelmassoni.tessera.feeds

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupIcon
import io.github.michaelmassoni.tessera.core.settings.SetupPage
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.feeds.data.Subscriptions
import io.github.michaelmassoni.tessera.feeds.ui.YarrFields
import io.github.michaelmassoni.tessera.feeds.ui.YarrFieldsSaver
import io.github.michaelmassoni.tessera.feeds.ui.YarrSignInForm
import io.github.michaelmassoni.tessera.feeds.ui.YarrSignInState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Feeds' first run: where the feeds come from, notifications, restore. */
internal val feedsSetup =
    SetupSpec(
        custom = listOf(SetupCustom { next -> SourcePage(next) }),
        steps =
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SetupStep(
                        DesignR.drawable.ic_notifications,
                        R.string.setup_notifications_title,
                        R.string.setup_notifications_text,
                        SetupGrant.Permissions(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                        required = false,
                    )
                } else {
                    null
                },
            ),
        offerRestore = true,
    )

private enum class Page { Choose, Yarr, Phone }

/** "Where are your feeds?": a yarr server (sign in), or this phone (import OPML, or start empty). */
@Composable
private fun SourcePage(next: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.Choose) }
    when (page) {
        Page.Choose -> {
            SetupPage(
                image = { SetupIcon(R.drawable.ic_feed) },
                title = stringResource(R.string.setup_source_title),
                text = stringResource(R.string.setup_source_text),
                body = {
                    Choice(DesignR.drawable.ic_dns, stringResource(R.string.source_yarr), stringResource(R.string.source_yarr_text)) {
                        page = Page.Yarr
                    }
                    Choice(
                        DesignR.drawable.ic_smartphone,
                        stringResource(R.string.source_phone),
                        stringResource(R.string.source_phone_text),
                    ) {
                        page = Page.Phone
                    }
                },
            ) {}
        }

        Page.Yarr -> {
            YarrPage(next, onBack = { page = Page.Choose })
        }

        Page.Phone -> {
            PhonePage(next, onBack = { page = Page.Choose })
        }
    }
}

@Composable
private fun YarrPage(
    next: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = YarrFieldsSaver) { mutableStateOf(YarrFields()) }
    val sign = remember { YarrSignInState { Subscriptions(context).useYarr(it.address, it.username, it.password) } }
    val connect = { scope.launch { sign.connect(fields, next) } }
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_dns) },
        title = stringResource(R.string.connect_yarr),
        text = stringResource(R.string.connect_yarr_text),
        body = { YarrSignInForm(fields, { fields = it }, sign.problem, enabled = !sign.working, onDone = { connect() }) },
    ) {
        Button(onClick = { connect() }, enabled = !sign.working && fields.address.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (sign.working) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.connect))
            }
        }
        TextButton(onClick = onBack, enabled = !sign.working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun PhonePage(
    next: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imported by remember { mutableStateOf<Int?>(null) }
    var failed by remember { mutableStateOf(false) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(
                        Dispatchers.IO,
                    ) { runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull() }
                val n = text?.let { Subscriptions(context).importOpml(it) }
                failed = n == null
                imported = n
            }
        }
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_smartphone) },
        title = stringResource(R.string.source_phone),
        text = stringResource(R.string.setup_phone_text),
        body = {
            imported?.let { Text(pluralStringResource(R.plurals.imported, it, it), style = MaterialTheme.typography.bodyLarge) }
            if (failed) Text(stringResource(R.string.import_failed), color = MaterialTheme.colorScheme.error)
        },
    ) {
        if (imported == null) {
            Button(
                onClick = { pick.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.import_opml)) }
            OutlinedButton(onClick = next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_empty)) }
        } else {
            Button(onClick = next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}

@Composable
private fun Choice(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
