package io.github.michaelmassoni.tessera.core.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** An app icon drawn from its glyph and two colours, as the launcher would show it. */
@Composable
fun IconPreview(
    @DrawableRes glyph: Int,
    foreground: Int,
    background: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    // User- or app-chosen icon colours, not theme colours: the one place raw colours are drawn.
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(background))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(glyph), contentDescription = null, tint = Color(foreground), modifier = Modifier.size(size * 1.5f))
    }
}

/**
 * Picks the background and symbol colours of a custom icon: hue, saturation and brightness
 * sliders plus a hex field, with a live preview and a warning when contrast is too low.
 */
@Composable
fun IconColorDialog(
    @DrawableRes glyph: Int,
    initial: IconSettings,
    onApply: (foreground: Int, background: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var foreground by rememberSaveable { mutableIntStateOf(initial.foreground) }
    var background by rememberSaveable { mutableIntStateOf(initial.background) }
    var editingBackground by rememberSaveable { mutableStateOf(true) }
    val current = if (editingBackground) background else foreground
    val setCurrent: (Int) -> Unit = { if (editingBackground) background = it else foreground = it }
    val previewLabel = stringResource(R.string.color_picker_preview)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconPreview(
                    glyph,
                    foreground,
                    background,
                    size = 96.dp,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { contentDescription = previewLabel },
                )
                if (ColorMath.contrast(foreground, background) < MIN_CONTRAST) {
                    Text(
                        stringResource(R.string.color_picker_low_contrast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = editingBackground,
                        onClick = { editingBackground = true },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.color_picker_background)) }
                    SegmentedButton(
                        selected = !editingBackground,
                        onClick = { editingBackground = false },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.color_picker_symbol)) }
                }
                HsvSliders(current, setCurrent)
                HexField(current, setCurrent)
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(foreground, background) }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun HsvSliders(
    color: Int,
    onChange: (Int) -> Unit,
) {
    // Keep HSV as the source of truth while dragging: converting through RGB loses the hue of
    // greys and blacks, which would make the hue slider jump.
    var hsv by remember { mutableStateOf(ColorMath.toHsv(color)) }
    if (ColorMath.fromHsv(hsv) != color) hsv = ColorMath.toHsv(color)
    val update: (Hsv) -> Unit = {
        hsv = it
        onChange(ColorMath.fromHsv(it))
    }
    Column {
        LabeledSlider(stringResource(R.string.color_picker_hue), hsv.hue, 0f..360f) { update(hsv.copy(hue = it)) }
        LabeledSlider(stringResource(R.string.color_picker_saturation), hsv.saturation, 0f..1f) { update(hsv.copy(saturation = it)) }
        LabeledSlider(stringResource(R.string.color_picker_brightness), hsv.value, 0f..1f) { update(hsv.copy(value = it)) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        val state = remember(range) { SliderState(value, trackRange = range) }
        state.value = value
        Slider(
            state = state,
            onValueChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun HexField(
    color: Int,
    onChange: (Int) -> Unit,
) {
    var text by remember(color) { mutableStateOf(ColorMath.toHex(color)) }
    val parsed = ColorMath.parseHex(text)
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            // Apply only complete colours, so typing "#fff…" isn't expanded mid-word.
            if (it.trim().removePrefix("#").length == 6) ColorMath.parseHex(it)?.let(onChange)
        },
        label = { Text(stringResource(R.string.color_picker_hex)) },
        singleLine = true,
        isError = parsed == null,
        supportingText = if (parsed == null) ({ Text(stringResource(R.string.color_picker_hex_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

private const val MIN_CONTRAST = 3.0
