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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.michaelmassoni.tessera.calculator.engine.Symbols
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
    modifier: Modifier = Modifier,
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
    val spoken = spokenExpression(state.expression)

    Column(
        modifier =
            modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .pointerInput(copyText) { detectTapGestures(onLongPress = { copy() }) }
                .semantics { customActions = listOf(CustomAccessibilityAction(copyLabel) { copy().let { true } }) },
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
                BasicText(
                    text = formatter.format(state.expression),
                    style =
                        displayLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            lineHeight = TextUnit.Unspecified,
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 2.sp),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
                )
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
