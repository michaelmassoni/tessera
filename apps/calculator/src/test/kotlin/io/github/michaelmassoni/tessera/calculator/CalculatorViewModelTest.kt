package io.github.michaelmassoni.tessera.calculator

import androidx.lifecycle.SavedStateHandle
import io.github.michaelmassoni.tessera.calculator.data.CalculatorSettings
import io.github.michaelmassoni.tessera.calculator.data.HistoryEntry
import io.github.michaelmassoni.tessera.calculator.data.HistoryStore
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.calculator.engine.CalcError
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.calculator.engine.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    @get:Rule val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private class FakeSettings : CalculatorSettings {
        override var angleUnit = AngleUnit.DEGREES
        override var scientificExpanded: Boolean? = null
    }

    private lateinit var store: HistoryStore
    private val settings = FakeSettings()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = HistoryStore(folder.root.resolve("history.tsv"))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        CalculatorViewModel(saved, store, settings, dispatcher, clock = {
            1000L
        })

    private fun CalculatorViewModel.type(text: String) {
        text.forEach { c ->
            onInput(
                when (c) {
                    in '0'..'9' -> Input.Digit(c)
                    '.' -> Input.Decimal
                    '(' -> Input.OpenParen
                    ')' -> Input.CloseParen
                    else -> Input.BinaryOperator(c)
                },
            )
        }
    }

    @Test fun `live preview while typing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("0.1+0.2")
            assertEquals("0.3", vm.state.value.preview)
            vm.onInput(Input.BinaryOperator('+'))
            assertNull(vm.state.value.preview) // "0.1+0.2+" is incomplete
        }

    @Test fun `equals shows the result, records history and persists it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.type("6×7")
            vm.onEquals()
            val state = vm.state.value
            assertEquals("42", state.expression)
            assertTrue(state.justEvaluated)
            assertNull(state.preview)
            assertEquals(listOf(HistoryEntry("6×7", "42", 1000L)), state.history)
            advanceUntilIdle()
            assertEquals(state.history, store.load())
        }

    @Test fun `history is loaded on start`() =
        runTest(dispatcher) {
            store.save(listOf(HistoryEntry("1+1", "2", 5)))
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(
                "2",
                vm.state.value.history
                    .single()
                    .result,
            )
        }

    @Test fun `errors only appear after equals and clear on the next input`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("1${Symbols.DIVIDE}0")
            assertNull(vm.state.value.error)
            vm.onEquals()
            assertEquals(CalcError.DivideByZero, vm.state.value.error)
            assertEquals("1÷0", vm.state.value.expression)
            vm.onInput(Input.Backspace)
            assertNull(vm.state.value.error)
        }

    @Test fun `angle unit changes the preview and is persisted`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onInput(Input.Function(Symbols.SIN))
            vm.type("90")
            assertEquals("1", vm.state.value.preview)
            vm.onToggleAngleUnit()
            assertEquals(AngleUnit.RADIANS, settings.angleUnit)
            assertEquals("0.8939966636005579", vm.state.value.preview)
        }

    @Test fun `inverse mode applies to one function`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onToggleInverse()
            assertTrue(vm.state.value.inverse)
            vm.onInput(Input.Function(Symbols.ASIN))
            assertFalse(vm.state.value.inverse)
        }

    @Test fun `expression survives process death`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            viewModel(saved).type("12+3")
            val restored = viewModel(saved)
            assertEquals("12+3", restored.state.value.expression)
            assertEquals("15", restored.state.value.preview)
        }

    @Test fun `clear history`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("1+1")
            vm.onEquals()
            vm.onClearHistory()
            advanceUntilIdle()
            assertTrue(
                vm.state.value.history
                    .isEmpty(),
            )
            assertTrue(store.load().isEmpty())
        }

    @Test fun `results keep full precision but backspace edits the displayed digits`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("1${Symbols.DIVIDE}3")
            vm.onEquals()
            assertEquals("0.33333333333333333333333333333333", vm.state.value.expression)
            vm.onInput(Input.BinaryOperator(Symbols.TIMES))
            vm.type("3")
            assertEquals("1", vm.state.value.preview)

            vm.onInput(Input.Clear)
            vm.type("1${Symbols.DIVIDE}3")
            vm.onEquals()
            vm.onInput(Input.Backspace)
            assertEquals("0.333333333333333", vm.state.value.expression)
        }

    @Test fun `tapping into the expression edits there, and the preview follows`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("12+5")
            vm.onCursor(2)
            vm.type("3")
            assertEquals("123+5", vm.state.value.expression)
            assertEquals(3, vm.state.value.cursor)
            assertEquals("128", vm.state.value.preview)
            // Tapping past the end goes back to typing at the end.
            vm.onCursor(null)
            vm.type("0")
            assertEquals("123+50", vm.state.value.expression)
            assertNull(vm.state.value.cursor)
        }

    @Test fun `tapping into a result edits the result as shown`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("1+2")
            vm.onEquals()
            vm.onCursor(0)
            vm.type("1")
            assertEquals("13", vm.state.value.expression)
            assertFalse(vm.state.value.justEvaluated)
        }

    @Test fun `the cursor survives process death`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            viewModel(saved).apply {
                type("123")
                onCursor(1)
            }
            assertEquals(1, viewModel(saved).state.value.cursor)
        }

    @Test fun `paste inserts or refuses`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.type("2+")
            assertTrue(vm.onPaste("1,000", '.', ','))
            assertEquals("2+1000", vm.state.value.expression)
            assertFalse(vm.onPaste("not a number", '.', ','))
            assertEquals("2+1000", vm.state.value.expression)
        }
}
