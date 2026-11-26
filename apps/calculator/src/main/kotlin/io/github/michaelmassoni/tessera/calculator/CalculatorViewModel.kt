package io.github.michaelmassoni.tessera.calculator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.calculator.data.CalculatorSettings
import io.github.michaelmassoni.tessera.calculator.data.HistoryEntry
import io.github.michaelmassoni.tessera.calculator.data.HistoryStore
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.calculator.engine.CalcError
import io.github.michaelmassoni.tessera.calculator.engine.EvalResult
import io.github.michaelmassoni.tessera.calculator.engine.Evaluator
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.calculator.engine.InputEditor
import io.github.michaelmassoni.tessera.calculator.engine.InputState
import io.github.michaelmassoni.tessera.calculator.engine.ResultFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the calculator screen draws. Expression and preview are in internal notation. */
data class CalculatorUiState(
    val expression: String = "",
    val justEvaluated: Boolean = false,
    /** Where input goes after a tap inside the expression (an index into it); null for the end. */
    val cursor: Int? = null,
    /** Live result while typing; null when there's nothing useful to show. */
    val preview: String? = null,
    /** Set only after "=" fails. */
    val error: CalcError? = null,
    val angleUnit: AngleUnit = AngleUnit.DEGREES,
    val inverse: Boolean = false,
    /** null = use the layout's default. */
    val scientificExpanded: Boolean? = null,
    val history: List<HistoryEntry> = emptyList(),
)

class CalculatorViewModel(
    private val savedState: SavedStateHandle,
    private val historyStore: HistoryStore,
    private val settings: CalculatorSettings,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            CalculatorUiState(
                expression = savedState[KEY_EXPRESSION] ?: "",
                justEvaluated = savedState[KEY_JUST_EVALUATED] ?: false,
                cursor = savedState[KEY_CURSOR],
                angleUnit = settings.angleUnit,
                scientificExpanded = settings.scientificExpanded,
            ).withPreview(),
        )
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    /** One writer at a time, in submission order. */
    private val historyWriter = ioDispatcher.limitedParallelism(1)

    init {
        viewModelScope.launch {
            val history = withContext(ioDispatcher) { historyStore.load() }
            _state.update { it.copy(history = history) }
        }
    }

    fun onInput(input: Input) {
        updateInput { current ->
            // After "=", backspace edits the result as displayed, not its hidden extra digits.
            val base =
                if (current.justEvaluated && input == Input.Backspace) {
                    current.copy(expression = ResultFormatter.shorten(current.expression))
                } else {
                    current
                }
            InputEditor.apply(base, input)
        }
        // Inverse mode applies to one function key, like the Pixel calculator.
        if (_state.value.inverse && input.isInvertible()) _state.update { it.copy(inverse = false) }
    }

    fun onEquals() {
        val current = _state.value
        if (current.expression.isEmpty() || current.justEvaluated) return
        when (val result = Evaluator.evaluate(current.expression, current.angleUnit)) {
            is EvalResult.Error -> {
                _state.update { it.copy(error = result.error, preview = null) }
            }

            is EvalResult.Value -> {
                val text = ResultFormatter.format(result.value)
                val entry = HistoryEntry(current.expression, text, clock())
                updateInput { InputState(text, justEvaluated = true) }
                updateHistory { HistoryStore.prepend(it, entry) }
            }
        }
    }

    fun onInsertFromHistory(
        entry: HistoryEntry,
        expression: Boolean,
    ) {
        updateInput {
            if (expression) {
                InputEditor.insert(it, entry.expression, asGroup = true)
            } else {
                InputEditor.insert(it, entry.result, asGroup = false)
            }
        }
    }

    /**
     * A tap inside the expression: input goes at [index] (in the expression as stored) from now
     * on, or at the end for null. After "=", the result as displayed becomes the expression.
     */
    fun onCursor(index: Int?) {
        updateInput { current ->
            val text = if (current.justEvaluated) ResultFormatter.shorten(current.expression) else current.expression
            InputState(text, justEvaluated = false, cursor = index?.coerceIn(0, text.length))
        }
    }

    /** Pastes [text] at the cursor. False when it isn't a number or expression. */
    fun onPaste(
        text: String,
        decimalSeparator: Char,
        groupingSeparator: Char,
    ): Boolean {
        var pasted = false
        updateInput { current ->
            InputEditor.paste(current, text, decimalSeparator, groupingSeparator)?.also { pasted = true } ?: current
        }
        return pasted
    }

    fun onClearHistory() = updateHistory { emptyList() }

    fun onToggleAngleUnit() {
        val next = if (_state.value.angleUnit == AngleUnit.DEGREES) AngleUnit.RADIANS else AngleUnit.DEGREES
        settings.angleUnit = next
        _state.update { it.copy(angleUnit = next).withPreview() }
    }

    /** Picks up changes made on the settings screen. */
    fun reloadSettings() {
        val unit = settings.angleUnit
        if (unit != _state.value.angleUnit) _state.update { it.copy(angleUnit = unit).withPreview() }
    }

    fun onToggleInverse() = _state.update { it.copy(inverse = !it.inverse) }

    fun onToggleScientific(currentlyExpanded: Boolean) {
        settings.scientificExpanded = !currentlyExpanded
        _state.update { it.copy(scientificExpanded = !currentlyExpanded) }
    }

    private fun updateInput(transform: (InputState) -> InputState) {
        _state.update { current ->
            val next = transform(InputState(current.expression, current.justEvaluated, current.cursor))
            // A cursor at the end is the same as none.
            val cursor = next.cursor?.takeIf { it < next.expression.length }
            savedState[KEY_EXPRESSION] = next.expression
            savedState[KEY_JUST_EVALUATED] = next.justEvaluated
            savedState[KEY_CURSOR] = cursor
            current
                .copy(expression = next.expression, justEvaluated = next.justEvaluated, cursor = cursor, error = null)
                .withPreview()
        }
    }

    private fun updateHistory(transform: (List<HistoryEntry>) -> List<HistoryEntry>) {
        _state.update { it.copy(history = transform(it.history)) }
        val snapshot = _state.value.history
        viewModelScope.launch(historyWriter) { historyStore.save(snapshot) }
    }

    private fun CalculatorUiState.withPreview(): CalculatorUiState {
        if (justEvaluated || !InputEditor.hasOperation(expression)) return copy(preview = null)
        val result = Evaluator.evaluate(expression, angleUnit)
        return copy(
            preview =
                (result as? EvalResult.Value)?.let {
                    ResultFormatter.format(it.value, ResultFormatter.PREVIEW_DIGITS)
                },
        )
    }

    private fun Input.isInvertible() =
        this is Input.Function ||
            this == Input.SquareRoot ||
            this == Input.Square ||
            this == Input.ExpPower ||
            this == Input.TenPower

    private companion object {
        const val KEY_EXPRESSION = "expression"
        const val KEY_CURSOR = "cursor"
        const val KEY_JUST_EVALUATED = "just_evaluated"
    }
}
