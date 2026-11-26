package io.github.michaelmassoni.tessera.calculator.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.michaelmassoni.tessera.calculator.CalculatorUiState
import io.github.michaelmassoni.tessera.calculator.R
import io.github.michaelmassoni.tessera.calculator.data.HistoryEntry
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.calculator.engine.DisplayFormatter
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Callbacks from the screen; implemented by the ViewModel. */
interface CalculatorActions {
    fun onInput(input: Input)

    fun onEquals()

    fun onToggleInverse()

    fun onToggleAngleUnit()

    fun onToggleScientific(currentlyExpanded: Boolean)

    fun onInsertFromHistory(
        entry: HistoryEntry,
        expression: Boolean,
    )

    fun onClearHistory()

    fun onOpenSettings()

    /** A tap inside the expression: input goes at [index] (stored notation), or the end for null. */
    fun onCursor(index: Int?) {}

    /** Pastes [text] at the cursor; false when it isn't a number or expression. */
    fun onPaste(
        text: String,
        decimalSeparator: Char,
        groupingSeparator: Char,
    ): Boolean = false
}

private val ExpandedWidth = 840.dp
private val MediumWidth = 600.dp
private val CompactHeight = 480.dp

/**
 * The calculator. The layout comes from the space available (see docs/calculator.md):
 * portrait, side-by-side for short windows, and a permanent history pane when wide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    actions: CalculatorActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val symbols = remember(locale) { DecimalFormatSymbols.getInstance(locale) }
    val formatter = remember(symbols) { DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator) }
    val keys =
        remember(
            symbols,
            state.inverse,
            state.angleUnit,
        ) { KeySet(symbols.decimalSeparator, state.inverse, state.angleUnit) }
    val onKey: (CalcKey) -> Unit = { key ->
        when (val action = key.action) {
            is KeyAction.Send -> actions.onInput(action.input)
            KeyAction.Equals -> actions.onEquals()
            KeyAction.ToggleInverse -> actions.onToggleInverse()
            KeyAction.ToggleAngleUnit -> actions.onToggleAngleUnit()
        }
    }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cantPaste = stringResource(R.string.cant_paste)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event -> HardwareKeys.handle(event, symbols.decimalSeparator, actions) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val short = maxHeight < CompactHeight
            // Phone landscape is wide but short: no room for a history pane there.
            val wide = maxWidth >= ExpandedWidth && !short
            val display: @Composable (Modifier) -> Unit = { displayModifier ->
                CalculatorDisplay(
                    state = state,
                    formatter = formatter,
                    decimalSeparator = symbols.decimalSeparator,
                    onCursor = actions::onCursor,
                    onPaste = { text ->
                        val pasted = actions.onPaste(text, symbols.decimalSeparator, symbols.groupingSeparator)
                        if (!pasted) scope.launch { snackbar.showSnackbar(cantPaste) }
                    },
                    onShowHistory = if (wide) null else ({ showHistorySheet = true }),
                    onOpenSettings = actions::onOpenSettings,
                    modifier = displayModifier,
                )
            }
            when {
                wide -> {
                    Row(Modifier.fillMaxSize()) {
                        SideBySideLayout(display, keys, onKey, Modifier.weight(1.6f).fillMaxHeight())
                        VerticalDivider()
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            HistoryPane(state.history, formatter, actions::onInsertFromHistory, actions::onClearHistory)
                        }
                    }
                }

                short -> {
                    SideBySideLayout(display, keys, onKey, Modifier.fillMaxSize())
                }

                else -> {
                    PortraitLayout(
                        display = display,
                        keys = keys,
                        onKey = onKey,
                        expanded = state.scientificExpanded ?: (maxWidth >= MediumWidth),
                        onToggleExpanded = actions::onToggleScientific,
                        width = maxWidth,
                        height = maxHeight,
                    )
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            HistoryPane(
                entries = state.history,
                formatter = formatter,
                onInsert = { entry, expression ->
                    actions.onInsertFromHistory(entry, expression)
                    showHistorySheet = false
                },
                onClear = actions::onClearHistory,
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    display: @Composable (Modifier) -> Unit,
    keys: KeySet,
    onKey: (CalcKey) -> Unit,
    expanded: Boolean,
    onToggleExpanded: (Boolean) -> Unit,
    width: Dp,
    height: Dp,
) {
    val side = 16.dp
    // Basic keys: square-ish, but leave the display at least ~30% of the height.
    val keyWidth = (width - side * 2 - KeyGap * 3) / 4
    val sciRowHeight = 48.dp
    val sciRows = if (expanded) keys.scientificPortraitRows.size else 1
    val sciHeight = sciRowHeight * sciRows + KeyGap * (sciRows - 1)
    val maxKeypad = height * 0.68f - sciHeight
    val keyHeight = min(keyWidth, (maxKeypad - KeyGap * 4) / 5)
    val keypadHeight = keyHeight * 5 + KeyGap * 4

    Column(Modifier.fillMaxSize().padding(bottom = side)) {
        display(Modifier.weight(1f).fillMaxWidth())
        Column(
            modifier = Modifier.padding(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KeyRows(
                rows = if (expanded) keys.scientificPortraitRows else keys.scientificPortraitRows.take(1),
                rowHeight = sciRowHeight,
                onKey = onKey,
                modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                trailing = { toggleModifier ->
                    FilledTonalIconButton(
                        onClick = { onToggleExpanded(expanded) },
                        modifier = toggleModifier.fillMaxHeight(),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (expanded) DesignR.drawable.ic_expand_less else DesignR.drawable.ic_expand_more,
                                ),
                            contentDescription =
                                stringResource(
                                    if (expanded) R.string.cd_collapse_scientific else R.string.cd_expand_scientific,
                                ),
                        )
                    }
                },
            )
            KeyGrid(rows = keys.basicRows, onKey = onKey, modifier = Modifier.fillMaxWidth().height(keypadHeight))
        }
    }
}

/** Phone landscape, and the calculator half of the large-screen layout. */
@Composable
private fun SideBySideLayout(
    display: @Composable (Modifier) -> Unit,
    keys: KeySet,
    onKey: (CalcKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(bottom = 16.dp)) {
        display(Modifier.weight(0.36f).fillMaxWidth())
        Row(
            modifier = Modifier.weight(0.64f).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeyGrid(rows = keys.scientificGridRows, onKey = onKey, modifier = Modifier.weight(3f).fillMaxHeight())
            KeyGrid(rows = keys.basicRows, onKey = onKey, modifier = Modifier.weight(4f).fillMaxHeight())
        }
    }
}

// ------------------------------------------------------------------------------- previews

private object PreviewActions : CalculatorActions {
    override fun onOpenSettings() = Unit

    override fun onInput(input: Input) = Unit

    override fun onEquals() = Unit

    override fun onToggleInverse() = Unit

    override fun onToggleAngleUnit() = Unit

    override fun onToggleScientific(currentlyExpanded: Boolean) = Unit

    override fun onInsertFromHistory(
        entry: HistoryEntry,
        expression: Boolean,
    ) = Unit

    override fun onClearHistory() = Unit
}

private val previewHistory =
    listOf(
        HistoryEntry("0.1+0.2", "0.3", 2),
        HistoryEntry("1250×1.08", "1350", 1),
        HistoryEntry("sin(30)", "0.5", 0),
    )

@TesseraPreviews
@Composable
private fun CalculatorTypingPreview() {
    TesseraTheme(dynamicColor = false) {
        CalculatorScreen(
            state =
                CalculatorUiState(
                    expression = "1250×(1+8%",
                    preview = "1350",
                    history = previewHistory,
                ),
            actions = PreviewActions,
        )
    }
}

// Editing inside the expression: the cursor after "12" (shown "12|,345+6").
@TesseraPreviews
@Composable
private fun CalculatorCursorPreview() {
    TesseraTheme(dynamicColor = false) {
        CalculatorScreen(
            state = CalculatorUiState(expression = "12345+6", cursor = 2, preview = "12351"),
            actions = PreviewActions,
        )
    }
}

@TesseraPreviews
@Composable
private fun CalculatorScientificPreview() {
    TesseraTheme(dynamicColor = false) {
        CalculatorScreen(
            state =
                CalculatorUiState(
                    expression = "√2×π",
                    preview = "4.442882938158366",
                    angleUnit = AngleUnit.RADIANS,
                    inverse = true,
                    scientificExpanded = true,
                ),
            actions = PreviewActions,
        )
    }
}

@TesseraPreviews
@Composable
private fun CalculatorErrorPreview() {
    TesseraTheme(dynamicColor = false) {
        CalculatorScreen(
            state =
                CalculatorUiState(
                    expression = "1÷0",
                    error = io.github.michaelmassoni.tessera.calculator.engine.CalcError.DivideByZero,
                ),
            actions = PreviewActions,
        )
    }
}

@TesseraPreviews
@Composable
private fun CalculatorLongExpressionPreview() {
    TesseraTheme(dynamicColor = false) {
        CalculatorScreen(
            state =
                CalculatorUiState(
                    expression = "123456789×987654321+3.14159265358979÷2.718281828−(4+5",
                    preview = "121932631112635269.2",
                ),
            actions = PreviewActions,
        )
    }
}
