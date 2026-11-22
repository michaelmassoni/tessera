package io.github.michaelmassoni.tessera.core.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.launch
import java.io.File

/** How a setup step is granted. */
sealed interface SetupGrant {
    /** Runtime permissions, asked together. Done when all are granted. */
    data class Permissions(
        val permissions: List<String>,
        /** When that counts as granted, if not "all of them" (Gallery's "selected photos"). */
        val satisfied: ((Context) -> Boolean)? = null,
    ) : SetupGrant

    /** A role such as the default phone or SMS app. */
    data class Role(
        val role: String,
    ) : SetupGrant

    /** A special access granted in system Settings ([intent]); [granted] checks it. */
    class Special(
        val intent: (Context) -> Intent,
        val granted: (Context) -> Boolean,
    ) : SetupGrant
}

/** One thing the app asks for during setup, explained in a sentence. */
data class SetupStep(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val text: Int,
    val grant: SetupGrant,
    /** Optional steps can be skipped with "Not now"; required ones only by leaving the app. */
    val required: Boolean = true,
    /** Whether the step applies, decided when it's reached (Music asks for audio only for music on the phone). */
    val wanted: (Context) -> Boolean = { true },
)

/**
 * A page of the app's own, after the welcome and before the permission steps: a choice or a
 * sign-in. [content] calls its argument to move on.
 */
class SetupCustom(
    val wanted: (Context) -> Boolean = { true },
    val content: @Composable (next: () -> Unit) -> Unit,
)

/** What an app's first-run setup covers. */
data class SetupSpec(
    val custom: List<SetupCustom> = emptyList(),
    val steps: List<SetupStep> = emptyList(),
    /** Offer to restore from a backup (for apps with data of their own). */
    val offerRestore: Boolean = false,
)

/**
 * First-run setup, shared by the Tessera apps: a welcome, a step for each permission or role the
 * app needs that isn't granted yet (what it's for, then Allow), an offer to restore from a
 * backup, and done. It runs once, on a fresh install only: an app that was already set up before
 * setup existed (updated, not installed) skips it. Whether it has run is kept outside backups, so
 * restoring onto a new phone still asks for that phone's permissions.
 */
object Setup {
    private const val DONE = "tessera-setup-done"
    private const val RESTORED = "tessera-setup-restored"

    fun isDone(context: Context): Boolean {
        val marker = File(context.noBackupFilesDir, DONE)
        if (marker.exists()) return true
        // Installed before setup existed: it was set up the old way.
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (info.lastUpdateTime > info.firstInstallTime) {
            markDone(context)
            return true
        }
        return false
    }

    fun markDone(context: Context) {
        File(context.noBackupFilesDir, DONE).createNewFile()
    }

    /** Restored as part of "Restore all Tessera apps": its own setup doesn't offer restore again. */
    internal fun markRestored(context: Context) {
        File(context.noBackupFilesDir, RESTORED).createNewFile()
    }

    internal fun wasRestored(context: Context) = File(context.noBackupFilesDir, RESTORED).exists()

    fun granted(
        context: Context,
        grant: SetupGrant,
    ): Boolean =
        when (grant) {
            is SetupGrant.Permissions -> {
                grant.satisfied?.invoke(context)
                    ?: grant.permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            }

            is SetupGrant.Role -> {
                context.getSystemService(RoleManager::class.java)?.isRoleHeld(grant.role) == true
            }

            is SetupGrant.Special -> {
                grant.granted(context)
            }
        }

    /** Opens this app's page in system Settings, for a permission that was denied for good. */
    fun appSettings(context: Context) =
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/**
 * Shows [spec]'s setup the first time the app opens, then [content]. With nothing to ask and
 * nothing to restore, it goes straight to [content].
 */
@Composable
fun SetupGate(
    spec: SetupSpec,
    // Opened by another app for one thing (a photo to view): setup waits for a normal launch.
    skip: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Read from the marker each time, not restored from saved state: after a crash Android can
    // restore state saved before setup finished, which put setup back on screen.
    var done by remember { mutableStateOf(Setup.isDone(context)) }
    if (done || skip) {
        content()
    } else {
        SetupFlow(spec) {
            Setup.markDone(context)
            done = true
        }
    }
}

private sealed interface Page {
    data object Welcome : Page

    data class Custom(
        val custom: SetupCustom,
    ) : Page

    data class Step(
        val step: SetupStep,
    ) : Page

    data object Restore : Page
}

@Composable
private fun SetupFlow(
    spec: SetupSpec,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // Steps already granted (some come with the phone, or with a role) aren't shown.
    val pages =
        remember {
            listOf(Page.Welcome) +
                spec.custom.map { Page.Custom(it) } +
                spec.steps.filterNot { Setup.granted(context, it.grant) }.map { Page.Step(it) } +
                if (spec.offerRestore && !Setup.wasRestored(context)) listOf(Page.Restore) else emptyList()
        }
    var index by rememberSaveable { mutableIntStateOf(0) }
    // A permission asked twice and denied can only be granted in Settings: say so.
    var denied by rememberSaveable { mutableStateOf(false) }
    // Refused once: Android will still ask again, and a required step can now be passed by.
    var declined by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current

    fun next() {
        denied = false
        declined = false
        // Skip steps granted along the way (a default-app role brings its permissions with it).
        var i = index + 1
        while (i < pages.size && skipped(context, pages[i])) i++
        if (i >= pages.size) onDone() else index = i
    }
    val page = pages[index]
    val step = (page as? Page.Step)?.step
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                step == null -> {}

                Setup.granted(context, step.grant) -> {
                    next()
                }

                // Android shows a rationale when it will still ask; otherwise only Settings can grant it.
                (step.grant as SetupGrant.Permissions).permissions.any { activity?.shouldShowRequestPermissionRationale(it) == true } -> {
                    declined = true
                }

                else -> {
                    denied = true
                }
            }
        }
    val forResult =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (step != null && Setup.granted(context, step.grant)) {
                next()
            } else if (step?.required == true) {
                denied = true
            }
        }
    // Back from system Settings with the access granted: move on.
    LifecycleResumeEffect(index) {
        if (step != null && denied && Setup.granted(context, step.grant)) next()
        onPauseOrDispose { }
    }

    fun ask(step: SetupStep) {
        when (val g = step.grant) {
            is SetupGrant.Permissions -> {
                permissions.launch(g.permissions.toTypedArray())
            }

            is SetupGrant.Role -> {
                val manager = context.getSystemService(RoleManager::class.java)
                if (manager != null &&
                    manager.isRoleAvailable(g.role)
                ) {
                    forResult.launch(manager.createRequestRoleIntent(g.role))
                } else {
                    next()
                }
            }

            is SetupGrant.Special -> {
                forResult.launch(g.intent(context))
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pages.size > 1) {
                LinearProgressIndicator(
                    progress = { (index + 1f) / pages.size },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).widthIn(max = 560.dp),
                )
            }
            AnimatedContent(page, label = "setup", modifier = Modifier.weight(1f)) { p ->
                when (p) {
                    Page.Welcome -> {
                        SetupPage(
                            image = { AppIconImage() },
                            title = stringResource(R.string.setup_welcome, appLabel(context)),
                            text = stringResource(if (pages.size > 1) R.string.setup_welcome_text else R.string.setup_welcome_ready),
                        ) {
                            Button(onClick = ::next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_start)) }
                        }
                    }

                    is Page.Step -> {
                        SetupPage(
                            image = { SetupIcon(p.step.icon) },
                            title = stringResource(p.step.title),
                            text =
                                stringResource(p.step.text) +
                                    if (denied) "\n\n" + stringResource(R.string.setup_denied) else "",
                        ) {
                            Button(
                                onClick = { if (denied) context.startActivity(Setup.appSettings(context)) else ask(p.step) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(if (denied) R.string.setup_open_settings else R.string.setup_allow)) }
                            if (!p.step.required || denied || declined) {
                                TextButton(onClick = ::next, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(if (p.step.required) R.string.setup_continue_without else R.string.setup_not_now))
                                }
                            }
                        }
                    }

                    Page.Restore -> {
                        RestorePage(onDone = ::next)
                    }

                    is Page.Custom -> {
                        p.custom.content(::next)
                    }
                }
            }
        }
    }
    // Back goes to the previous page; from the welcome it leaves the app, and setup runs again next time.
    androidx.activity.compose.BackHandler(index > 0) {
        var i = index - 1
        while (i > 0 && skipped(context, pages[i])) i--
        index = i
    }
}

/**
 * The layout every setup page shares: a picture, a title, a sentence or two, anything else the
 * page needs ([body]: fields, choices), and its buttons at the bottom.
 */
@Composable
fun SetupPage(
    image: @Composable () -> Unit,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    body: (@Composable ColumnScope.() -> Unit)? = null,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        image()
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp).padding(top = 24.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 560.dp).padding(top = 16.dp),
        )
        if (body != null) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body,
            )
        }
        Spacer(Modifier.weight(1f).height(32.dp))
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { buttons() }
    }
}

/** A setup page's picture: [icon] in a tonal circle. */
@Composable
fun SetupIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(96.dp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

/** The app's launcher icon, as installed. */
@Composable
private fun AppIconImage() {
    val context = LocalContext.current
    val bitmap =
        remember {
            runCatching {
                context.packageManager
                    .getApplicationIcon(context.packageName)
                    .toBitmap(256, 256)
                    .asImageBitmap()
            }.getOrNull()
        }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(96.dp).clip(CircleShape))
}

private fun appLabel(context: Context) = context.applicationInfo.loadLabel(context.packageManager).toString()

/** Restore this app, or every Tessera app, from a backup file, or skip. */
@Composable
private fun RestorePage(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flows = remember { BackupFlows(context) }
    var pending by remember { mutableStateOf<PendingRestore?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                runCatching { flows.inspect(uri) }.onSuccess { pending = it }.onFailure { error = flows.message(it) }
            }
        }
    SetupPage(
        image = { SetupIcon(io.github.michaelmassoni.tessera.core.design.R.drawable.ic_history) },
        title = stringResource(R.string.setup_restore_title),
        text = stringResource(R.string.setup_restore_text) + (error?.let { "\n\n$it" } ?: ""),
    ) {
        Button(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_restore_pick))
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_restore_skip)) }
    }
    pending?.let { p ->
        RestoreDialog(p, appLabel(context), onConfirm = {
            pending = null
            working = true
            scope.launch {
                when (p) {
                    is PendingRestore.One -> {
                        runCatching { flows.restore(p.uri) }
                            .onSuccess {
                                Setup.markDone(context)
                                AppBackup.relaunch(context)
                            }.onFailure {
                                working = false
                                error = flows.message(it)
                            }
                    }

                    is PendingRestore.All -> {
                        val (_, self) = flows.restoreAll(p.uri)
                        Setup.markDone(context)
                        if (self) AppBackup.relaunch(context) else onDone()
                    }
                }
            }
        }, onDismiss = { pending = null })
    }
    if (working) WorkingDialog()
}

/**
 * Shown instead of an app that can't work without an access you haven't given (after setup):
 * what it needs and why, Allow, and the app's settings for when Android won't ask again.
 */
@Composable
fun NeedsAccessScreen(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding()) {
            SetupPage(image = { SetupIcon(icon) }, title = title, text = text) {
                Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_allow)) }
                TextButton(onClick = { context.startActivity(Setup.appSettings(context)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_open_settings))
                }
            }
        }
    }
}

@TesseraPreviews
@Composable
private fun SetupStepPreview() {
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                LinearProgressIndicator(
                    progress = { 2f / 4 },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                )
                SetupPage(
                    image = { SetupIcon(io.github.michaelmassoni.tessera.core.design.R.drawable.ic_contacts) },
                    title = "Calls and contacts",
                    text = "To make calls, show your recent calls and show your contacts' names. Nothing leaves this phone.",
                ) {
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_allow)) }
                    TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_not_now)) }
                }
            }
        }
    }
}

@TesseraPreviews
@Composable
private fun SetupRestorePreview() {
    TesseraTheme(dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            SetupPage(
                image = { SetupIcon(io.github.michaelmassoni.tessera.core.design.R.drawable.ic_history) },
                title = stringResource(R.string.setup_restore_title),
                text = stringResource(R.string.setup_restore_text),
            ) {
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_restore_pick)) }
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_restore_skip)) }
            }
        }
    }
}

/** Pages passed over: steps granted along the way or not wanted, and custom pages not wanted. */
private fun skipped(
    context: Context,
    page: Page,
): Boolean =
    when (page) {
        is Page.Step -> !page.step.wanted(context) || Setup.granted(context, page.step.grant)
        is Page.Custom -> !page.custom.wanted(context)
        else -> false
    }
