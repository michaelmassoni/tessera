package io.github.michaelmassoni.tessera.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Space between keys. */
internal val KeyGap = 8.dp

/** A grid of keys: every row has the same height and keys share the row width equally. */
@Composable
internal fun KeyGrid(
    rows: List<List<CalcKey>>,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = rows.maxOf { it.size },
    trailing: (@Composable (Modifier) -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(KeyGap),
            ) {
                row.forEach { key ->
                    KeyButton(key = key, onClick = { onKey(key) }, modifier = Modifier.weight(1f))
                }
                // The first row of the portrait scientific panel ends with the expand toggle.
                if (index == 0 && trailing != null) trailing(Modifier.weight(1f))
                repeat(columns - row.size - if (index == 0 && trailing != null) 1 else 0) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Fixed-height rows, for the collapsible scientific panel. */
@Composable
internal fun KeyRows(
    rows: List<List<CalcKey>>,
    rowHeight: Dp,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (Modifier) -> Unit)? = null,
) {
    KeyGrid(
        rows = rows,
        onKey = onKey,
        columns = 5,
        trailing = trailing,
        modifier = modifier.height(rowHeight * rows.size + KeyGap * (rows.size - 1)),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun KeyButton(
    key: CalcKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val click = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }
    val description = key.description?.let { stringResource(it) }
    val semanticsModifier =
        modifier.fillMaxSize().then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        )
    val shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.large)

    if (key.checked != null) {
        ToggleButton(
            checked = key.checked,
            onCheckedChange = { click() },
            modifier = semanticsModifier,
            shapes =
                ToggleButtonShapes(
                    shape = CircleShape,
                    pressedShape = MaterialTheme.shapes.large,
                    checkedShape = MaterialTheme.shapes.large,
                ),
            contentPadding = PaddingValues(4.dp),
        ) { KeyLabel(key) }
    } else {
        Button(
            onClick = click,
            shapes = shapes,
            colors = key.role.colors(),
            contentPadding = PaddingValues(4.dp),
            modifier = semanticsModifier,
        ) { KeyLabel(key) }
    }
}

@Composable
private fun KeyLabel(key: CalcKey) {
    if (key.icon != null) {
        // Decorative: the key's semantics carry the description.
        Icon(painterResource(key.icon), contentDescription = null, modifier = Modifier.size(28.dp))
        return
    }
    val text = key.text ?: stringResource(requireNotNull(key.label))
    val base =
        if (key.role ==
            KeyRole.Scientific
        ) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.headlineLarge
        }
    BasicText(
        text = text,
        style =
            LocalTextStyle.current
                .merge(
                    base,
                ).copy(
                    color = LocalContentColor.current,
                    textAlign = TextAlign.Center,
                    lineHeight = TextUnit.Unspecified,
                ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = base.fontSize, stepSize = 1.sp),
    )
}

@Composable
private fun KeyRole.colors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        KeyRole.Digit -> {
            ButtonDefaults.buttonColors(
                containerColor = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurface,
            )
        }

        KeyRole.Operator -> {
            ButtonDefaults.buttonColors(
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            )
        }

        KeyRole.Equals -> {
            ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
        }

        KeyRole.Clear -> {
            ButtonDefaults.buttonColors(
                containerColor = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
            )
        }

        KeyRole.Scientific -> {
            ButtonDefaults.buttonColors(
                containerColor = scheme.surfaceContainer,
                contentColor = scheme.onSurfaceVariant,
            )
        }
    }
}
