package io.github.michaelmassoni.tessera.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Setup: enable the keyboard, switch to it, and try it. */
class MainActivity : ComponentActivity() {
    private var enabled by mutableStateOf(false)
    private var selected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTesseraContent {
            SetupScreen(
                enabled = enabled,
                selected = selected,
                onEnable = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                onSelect = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The input method picker is a dialog; check again when it closes.
        if (hasFocus) refresh()
    }

    private fun refresh() {
        val imm = getSystemService(InputMethodManager::class.java)
        enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        selected =
            Settings.Secure
                .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                .orEmpty()
                .startsWith("$packageName/")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    enabled: Boolean,
    selected: Boolean,
    onEnable: () -> Unit,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var test by rememberSaveable { mutableStateOf("") }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
                Step(
                    1,
                    stringResource(R.string.setup_enable),
                    done = enabled,
                    action = stringResource(R.string.setup_enable_action),
                    onClick = onEnable,
                )
                Step(
                    2,
                    stringResource(R.string.setup_select),
                    done = selected,
                    action = stringResource(R.string.setup_select_action),
                    enabled = enabled,
                    onClick = onSelect,
                )
                OutlinedTextField(
                    value = test,
                    onValueChange = { test = it },
                    label = { Text(stringResource(R.string.setup_try)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    text: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (done) {
            Icon(
                painterResource(DesignR.drawable.ic_check_circle),
                contentDescription = stringResource(R.string.setup_done),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(
                "$number",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (done) {
            OutlinedButton(onClick = onClick) { Text(action) }
        } else {
            Button(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}
