package io.github.michaelmassoni.tessera.calculator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import kotlinx.coroutines.Dispatchers
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: CalculatorViewModel by viewModels {
        viewModelFactory {
            initializer {
                CalculatorViewModel(
                    savedState = createSavedStateHandle(),
                    historyStore = HistoryStore(File(filesDir, "history.tsv")),
                    settings = calculatorSettings(),
                    ioDispatcher = Dispatchers.IO,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = ViewModelActions(viewModel) { startActivity(Intent(this, SettingsActivity::class.java)) }
        setTesseraContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            CalculatorScreen(state = state, actions = actions)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadSettings()
    }
}

internal fun Context.calculatorSettings() = SharedPreferencesSettings(getSharedPreferences("settings", Context.MODE_PRIVATE))

private class ViewModelActions(
    private val vm: CalculatorViewModel,
    private val openSettings: () -> Unit,
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

    override fun onOpenSettings() = openSettings()

    override fun onCursor(index: Int?) = vm.onCursor(index)

    override fun onPaste(
        text: String,
        decimalSeparator: Char,
        groupingSeparator: Char,
    ) = vm.onPaste(text, decimalSeparator, groupingSeparator)
}
