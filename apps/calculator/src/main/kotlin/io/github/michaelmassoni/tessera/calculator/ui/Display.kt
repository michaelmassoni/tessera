package io.github.michaelmassoni.tessera.calculator.ui

import android.content.ClipData
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.michaelmassoni.tessera.calculator.CalculatorUiState
import io.github.michaelmassoni.tessera.calculator.R
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.calculator.engine.CalcError
import io.github.michaelmassoni.tessera.calculator.engine.DisplayFormatter
import io.github.michaelmassoni.tessera.calculator.engine.ResultFormatter
import io.github.michaelmassoni.tessera.calculator.engine.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * The expression and the live result. Long-press copies the result (or the expression).
 */
@Composable
internal fun CalculatorDisplay(
    state: CalculatorUiState,
    formatter: DisplayFormatter,
    decimalSeparator: Char,
    onShowHistory: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onCursor: (Int?) -> Unit = {},
    onPaste: (String) -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyText = state.preview ?: state.expression
    val copy: () -> Unit = {
        if (copyText.isNotEmpty()) {
            val plain = copyText.replace(Symbols.MINUS, '-').replace('.', decimalSeparator)
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, plain))) }
        }
    }
    val copyLabel = stringResource(R.string.copy_result)
    val pasteLabel = stringResource(R.string.paste)
    val paste: () -> Unit = {
        scope.launch {
            // Read only when asked: Android shows "pasted from your clipboard" on every read.
            val text =
                clipboard
                    .getClipEntry()
                    ?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
            if (!text.isNullOrBlank()) onPaste(text)
        }
    }
    var menu by remember { mutableStateOf(false) }
    val spoken = spokenExpression(state.expression)

    Column(
        modifier =
            modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { menu = true }) }
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(copyLabel) { copy().let { true } },
                            CustomAccessibilityAction(pasteLabel) { paste().let { true } },
                        )
                },
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        if (state.angleUnit ==
                            AngleUnit.DEGREES
                        ) {
                            R.string.angle_degrees
                        } else {
                            R.string.angle_radians
                        },
                    ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (onShowHistory != null) {
                IconButton(onClick = onShowHistory) {
                    Icon(
                        painterResource(DesignR.drawable.ic_history),
                        contentDescription = stringResource(R.string.cd_history),
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painterResource(DesignR.drawable.ic_settings),
                    contentDescription = stringResource(R.string.cd_settings),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            val displayLarge = MaterialTheme.typography.displayLarge
            // Takes the remaining height. Auto-size handles width; the height cap is applied here
            // so short windows (phone landscape) get smaller text instead of clipped text.
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f, fill = false),
                contentAlignment = Alignment.BottomEnd,
            ) {
                val density = LocalDensity.current
                val heightCap = with(density) { (maxHeight * 0.8f).toSp() }
                val maxSize = if (heightCap.value < displayLarge.fontSize.value) heightCap else displayLarge.fontSize
                val minSize = if (maxSize.value < 20f) maxSize else 20.sp
                val raw = if (state.justEvaluated) ResultFormatter.shorten(state.expression) else state.expression
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val cursorColor = MaterialTheme.colorScheme.primary
                // Blinks on a timer (not an animation, so "Remove animations" doesn't hide it), and shows
                // again at once after each edit, as text cursors do.
                var cursorOn by remember(state.cursor, state.expression) { mutableStateOf(true) }
                LaunchedEffect(state.cursor, state.expression) {
                    if (state.cursor == null) return@LaunchedEffect
                    while (true) {
                        delay(CURSOR_BLINK_MS.toLong())
                        cursorOn = !cursorOn
                    }
                }
                BasicText(
                    text = formatter.format(raw),
                    style =
                        displayLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            lineHeight = TextUnit.Unspecified,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 2.sp),
                    onTextLayout = { layout = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = spoken }
                            // Tap inside the expression to edit there; past the end goes back to the end.
                            .pointerInput(raw) {
                                detectTapGestures(
                                    onTap = { position ->
                                        val l = layout ?: return@detectTapGestures
                                        val index = formatter.rawOffset(raw, l.getOffsetForPosition(position))
                                        onCursor(index.takeIf { it < raw.length && raw.isNotEmpty() })
                                    },
                                    onLongPress = { menu = true },
                                )
                            }.drawWithContent {
                                drawContent()
                                val cursor = state.cursor ?: return@drawWithContent
                                val l = layout ?: return@drawWithContent
                                val offsets = formatter.offsets(raw)
                                if (cursor >= offsets.size) return@drawWithContent
                                val rect = l.getCursorRect(offsets[cursor])
                                if (cursorOn) {
                                    drawLine(
                                        cursorColor,
                                        start = Offset(rect.left, rect.top),
                                        end = Offset(rect.left, rect.bottom),
                                        strokeWidth = CURSOR_WIDTH.toPx(),
                                    )
                                }
                            },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(copyLabel) },
                        enabled = copyText.isNotEmpty(),
                        onClick = {
                            menu = false
                            copy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(pasteLabel) },
                        onClick = {
                            menu = false
                            paste()
                        },
                    )
                }
            }
            val error = state.error
            val previewText =
                when {
                    error != null -> stringResource(error.message())
                    state.preview != null -> formatter.format(state.preview)
                    else -> ""
                }
            val spokenPreview =
                if (error == null &&
                    state.preview != null
                ) {
                    stringResource(R.string.spoken_equals, spokenExpression(state.preview))
                } else {
                    previewText
                }
            Text(
                text = previewText,
                style = MaterialTheme.typography.headlineMedium,
                color =
                    if (error !=
                        null
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                textAlign = TextAlign.End,
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics {
                        contentDescription = spokenPreview
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

internal fun CalcError.message(): Int =
    when (this) {
        CalcError.DivideByZero -> R.string.error_divide_by_zero
        CalcError.Domain -> R.string.error_domain
        CalcError.Overflow -> R.string.error_overflow
        CalcError.Syntax -> R.string.error_syntax
    }

/** Expression with operators spelled out for TalkBack ("12 multiply 3"). */
@Composable
private fun spokenExpression(expression: String): String {
    val words =
        mapOf(
            '+' to stringResource(R.string.cd_plus),
            Symbols.MINUS to stringResource(R.string.cd_minus),
            Symbols.TIMES to stringResource(R.string.cd_multiply),
            Symbols.DIVIDE to stringResource(R.string.cd_divide),
            Symbols.POWER to stringResource(R.string.cd_power),
            Symbols.PERCENT to stringResource(R.string.cd_percent),
            Symbols.FACTORIAL to stringResource(R.string.cd_factorial),
            Symbols.SQRT to stringResource(R.string.cd_sqrt),
            Symbols.PI to stringResource(R.string.cd_pi),
            '(' to stringResource(R.string.cd_open_paren),
            ')' to stringResource(R.string.cd_close_paren),
        )
    return remember(expression, words) {
        buildString {
            expression.forEach { c ->
                val word = words[c]
                if (word != null) append(' ').append(word).append(' ') else append(c)
            }
        }.trim().replace(Regex(" +"), " ")
    }
}

private const val CURSOR_BLINK_MS = 500
private val CURSOR_WIDTH = 2.dp
