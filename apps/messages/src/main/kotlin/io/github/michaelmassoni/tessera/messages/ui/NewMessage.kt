package io.github.michaelmassoni.tessera.messages.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.Recipients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Choosing who to message: one person, or several for a group conversation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NewMessage(
    actions: MessagesActions,
    onClose: () -> Unit,
    onPick: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var to by rememberSaveable { mutableStateOf("") }
    // A normal keyboard by default, so names can be typed; the dialpad is a tap away.
    var dialpad by rememberSaveable { mutableStateOf(false) }
    val numberMode = Recipients.looksLikeNumber(to)
    val phoneFormat = remember(actions) { PhoneNumberTransformation(actions::formatNumber) }
    val invalid = remember(to) { numberMode && Recipients.shouldCheck(to) && !actions.isValidNumber(to) }
    // Chosen recipients as (label, number).
    var chosen by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }
    val suggestions by produceState(emptyList<Pair<String, String>>(), to) {
        value = withContext(Dispatchers.IO) { actions.suggestions(to) }
    }

    fun add(
        label: String,
        number: String,
    ) {
        if (number.isNotBlank() && chosen.none { it.second == number }) chosen = chosen + (label to number.trim())
        to = ""
    }
    val addLabel = stringResource(R.string.add_to_group)
    val numbers = { (chosen.map { it.second } + listOfNotNull(to.trim().takeIf { it.isNotEmpty() })) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (chosen.isNotEmpty()) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chosen.forEach { (label, number) ->
                        InputChip(
                            selected = false,
                            onClick = { chosen = chosen.filterNot { it.second == number } },
                            label = { Text(label) },
                            trailingIcon = {
                                Icon(
                                    painterResource(DesignR.drawable.ic_close),
                                    contentDescription = stringResource(R.string.remove_recipient, label),
                                )
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { text ->
                        // A comma or semicolon finishes a number and starts the next. Numbers are kept as
                        // bare digits; the field lays them out for display.
                        if (text.endsWith(',') || text.endsWith(';')) {
                            val entry = text.dropLast(1)
                            if (Recipients.looksLikeNumber(entry)) {
                                val number = Recipients.dialable(entry)
                                add(Recipients.display(number, actions::formatNumber), number)
                            } else {
                                add(entry, entry)
                            }
                        } else {
                            to = if (Recipients.looksLikeNumber(text)) Recipients.dialable(text) else text
                        }
                    },
                    label = { Text(stringResource(R.string.to)) },
                    singleLine = true,
                    visualTransformation = if (numberMode) phoneFormat else VisualTransformation.None,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.check_number)) }) else null,
                    trailingIcon = {
                        IconButton(onClick = { dialpad = !dialpad }) {
                            Icon(
                                painterResource(if (dialpad) DesignR.drawable.ic_keyboard else DesignR.drawable.ic_dialpad),
                                contentDescription = stringResource(if (dialpad) R.string.use_keyboard else R.string.use_dialpad),
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = if (dialpad) KeyboardType.Phone else KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    keyboardActions = KeyboardActions(onNext = { if (numbers().isNotEmpty()) onPick(numbers()) }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onPick(numbers()) }, enabled = numbers().isNotEmpty()) {
                    Icon(painterResource(DesignR.drawable.ic_arrow_forward), contentDescription = stringResource(R.string.next))
                }
            }
            if (chosen.isNotEmpty() || to.isNotBlank()) {
                Text(
                    stringResource(R.string.add_more_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn {
                items(suggestions) { (name, number) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                // Tap messages this person (or adds them, once a group is started); long-press starts a group.
                                onClick = { if (chosen.isEmpty()) onPick(listOf(number)) else add(name, number) },
                                onLongClick = { add(name, number) },
                                onLongClickLabel = addLabel,
                            ).heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Shows a number typed as bare digits laid out for the phone's country, keeping the cursor right. */
private class PhoneNumberTransformation(
    private val format: (String) -> String?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = Recipients.display(raw, format)
        if (shown == raw) return TransformedText(text, OffsetMapping.Identity)
        val (toShown, toRaw) = Recipients.offsets(raw, shown)
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = toShown[offset.coerceIn(0, raw.length)]

                override fun transformedToOriginal(offset: Int) = toRaw[offset.coerceIn(0, shown.length)]
            },
        )
    }
}
