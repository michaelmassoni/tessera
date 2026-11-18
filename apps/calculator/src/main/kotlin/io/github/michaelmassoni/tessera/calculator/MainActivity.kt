package io.github.michaelmassoni.tessera.calculator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.calculator.data.HistoryEntry
import io.github.michaelmassoni.tessera.calculator.data.HistoryStore
import io.github.michaelmassoni.tessera.calculator.data.SharedPreferencesSettings
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.calculator.ui.CalculatorActions
import io.github.michaelmassoni.tessera.calculator.ui.CalculatorScreen
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.Dispatchers
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: CalculatorViewModel by viewModels {
        viewModelFactory {
            initializer {
                CalculatorViewModel(
                    savedState = createSavedStateHandle(),
                    historyStore = HistoryStore(File(filesDir, "history.tsv")),
                    settings = SharedPreferencesSettings(getSharedPreferences("settings", Context.MODE_PRIVATE)),
                    ioDispatcher = Dispatchers.IO,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val actions = ViewModelActions(viewModel)
        setContent {
            TesseraTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CalculatorScreen(state = state, actions = actions)
            }
        }
    }
}

private class ViewModelActions(
    private val vm: CalculatorViewModel,
) : CalculatorActions {
    override fun onInput(input: Input) = vm.onInput(input)

    override fun onEquals() = vm.onEquals()

    override fun onToggleInverse() = vm.onToggleInverse()

    override fun onToggleAngleUnit() = vm.onToggleAngleUnit()

    override fun onToggleScientific(currentlyExpanded: Boolean) = vm.onToggleScientific(currentlyExpanded)

    override fun onInsertFromHistory(
        entry: HistoryEntry,
        expression: Boolean,
    ) = vm.onInsertFromHistory(entry, expression)

    override fun onClearHistory() = vm.onClearHistory()
}
