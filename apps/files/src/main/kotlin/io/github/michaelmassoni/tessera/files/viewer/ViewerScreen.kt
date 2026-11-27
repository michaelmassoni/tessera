package io.github.michaelmassoni.tessera.files.viewer

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.rememberPdfViewerState
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTextField
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.files.R
import io.github.michaelmassoni.tessera.files.engine.PageJump
import io.github.michaelmassoni.tessera.files.engine.VisiblePage
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface ViewerActions {
    fun onClose()

    fun onShare()

    fun onOpenWith()

    fun onPassword(password: String)

    fun onDarkPages(dark: Boolean)
}

/** The PDF viewer: the document, with its name and page in the top bar (docs/files.md, "PDF viewer"). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPdfApi::class)
@Composable
fun ViewerScreen(
    name: String,
    load: PdfLoad,
    darkPages: Boolean,
    actions: ViewerActions,
    modifier: Modifier = Modifier,
) {
    val viewer = rememberPdfViewerState()
    val scope = rememberCoroutineScope()
    val document = (load as? PdfLoad.Ready)?.document
    val pages = document?.pageCount ?: 0
    // Kept across rotation: the page to go back to once the document is shown again.
    var savedPage by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var jumping by rememberSaveable { mutableStateOf(false) }
    // The page across the middle of the view (VisiblePage). Derived, so scrolling recomposes the
    // screen only when the page number changes; reading the first page's offset makes it follow
    // scrolling within a page, not only changes of the first visible page.
    var viewHeight by remember { mutableIntStateOf(0) }
    val currentPage by remember(viewer) {
        derivedStateOf {
            viewer.firstVisiblePageOffset // read so scrolling within a page updates this too
            VisiblePage.current(
                viewer.firstVisiblePage,
                (viewer.firstVisiblePage until viewer.firstVisiblePage + viewer.visiblePagesCount).map {
                    viewer.getVisiblePageOffset(it)?.y ?: Float.POSITIVE_INFINITY
                },
                viewHeight / 2f,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pages > 0) {
                            Text(
                                stringResource(R.string.viewer_page_of, currentPage + 1, pages),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions::onClose) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_up))
                    }
                },
                actions = {
                    IconButton(onClick = actions::onShare) {
                        Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (pages > 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_go_to_page)) },
                                    onClick = {
                                        menu = false
                                        jumping = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_open_with)) },
                                onClick = {
                                    menu = false
                                    actions.onOpenWith()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_dark_pages)) },
                                trailingIcon = { Checkbox(checked = darkPages, onCheckedChange = null) },
                                onClick = {
                                    menu = false
                                    actions.onDarkPages(!darkPages)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (load) {
                PdfLoad.Loading -> {
                    CircularProgressIndicator()
                }

                PdfLoad.Failed -> {
                    Problem(stringResource(R.string.viewer_cant_open), actions::onOpenWith)
                }

                is PdfLoad.NeedsPassword -> {
                    Problem(stringResource(R.string.viewer_protected), null)
                    PasswordDialog(load.wrong, onDismiss = actions::onClose, onConfirm = actions::onPassword)
                }

                is PdfLoad.Ready -> {
                    // Dark pages: the rendered pages, colours inverted.
                    val invert =
                        remember {
                            RenderEffect
                                .createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix(INVERT)))
                                .asComposeRenderEffect()
                        }
                    PdfViewer(
                        pdfDocument = load.document,
                        state = viewer,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onSizeChanged { viewHeight = it.height }
                                .graphicsLayer { renderEffect = if (darkPages) invert else null },
                        onFirstContentLoad = {
                            if (savedPage > 0) scope.launch { viewer.scrollToPage(savedPage.coerceAtMost(pages - 1)) }
                        },
                    )
                    LaunchedEffect(currentPage) { savedPage = currentPage }
                }
            }
        }
    }

    if (jumping && pages > 1) {
        GoToPageDialog(
            pages,
            onDismiss = { jumping = false },
            onGo = { page ->
                jumping = false
                scope.launch { viewer.scrollToPage(page) }
            },
        )
    }
}

/** Why the document isn't shown, with Open with when another app might manage it. */
@Composable
private fun Problem(
    text: String,
    onOpenWith: (() -> Unit)?,
) {
    Column(
        Modifier.widthIn(max = 480.dp).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_description),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onOpenWith != null) {
            FilledTonalButton(onClick = onOpenWith) { Text(stringResource(R.string.viewer_open_with)) }
        }
    }
}

@Composable
private fun PasswordDialog(
    wrong: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val password = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.viewer_password_text))
                OutlinedSecureTextField(
                    state = password,
                    label = { Text(stringResource(R.string.viewer_password)) },
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.viewer_password_wrong)) }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.text.toString()) }, enabled = password.text.isNotEmpty()) {
                Text(stringResource(R.string.viewer_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun GoToPageDialog(
    pages: Int,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    val text = rememberTextFieldState()
    val page = PageJump.parse(text.text.toString(), pages)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_go_to_page)) },
        text = {
            TesseraTextField(
                state = text,
                label = { Text(stringResource(R.string.viewer_page_range, pages)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onGo) }, enabled = page != null) { Text(stringResource(R.string.viewer_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Inverts red, green and blue; keeps alpha. */
private val INVERT =
    floatArrayOf(
        -1f,
        0f,
        0f,
        0f,
        255f,
        0f,
        -1f,
        0f,
        0f,
        255f,
        0f,
        0f,
        -1f,
        0f,
        255f,
        0f,
        0f,
        0f,
        1f,
        0f,
    )

private object NoViewerActions : ViewerActions {
    override fun onClose() = Unit

    override fun onShare() = Unit

    override fun onOpenWith() = Unit

    override fun onPassword(password: String) = Unit

    override fun onDarkPages(dark: Boolean) = Unit
}

// A file that isn't a readable PDF.
@TesseraPreviews
@Composable
private fun ViewerFailedPreview() {
    TesseraTheme(dynamicColor = false) { ViewerScreen("Lease agreement.pdf", PdfLoad.Failed, false, NoViewerActions) }
}

// A password-protected PDF, after a wrong password.
@TesseraPreviews
@Composable
private fun ViewerPasswordPreview() {
    TesseraTheme(dynamicColor = false) { ViewerScreen("Payslip.pdf", PdfLoad.NeedsPassword(wrong = true), false, NoViewerActions) }
}
