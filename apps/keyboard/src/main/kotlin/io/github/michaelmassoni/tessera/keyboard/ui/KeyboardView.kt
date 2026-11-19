package io.github.michaelmassoni.tessera.keyboard.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.michaelmassoni.tessera.keyboard.R
import io.github.michaelmassoni.tessera.keyboard.engine.Accents
import io.github.michaelmassoni.tessera.keyboard.engine.EnterAction
import io.github.michaelmassoni.tessera.keyboard.engine.Key
import io.github.michaelmassoni.tessera.keyboard.engine.Layout
import io.github.michaelmassoni.tessera.keyboard.engine.Layouts
import io.github.michaelmassoni.tessera.keyboard.engine.ShiftState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

data class KeyboardState(
    val layout: Layout = Layout.Letters,
    val decimal: Boolean = false,
    val numberRow: Boolean = false,
    val shift: ShiftState = ShiftState.Off,
    val enter: EnterAction = EnterAction.Done,
    val haptics: Boolean = true,
    /** Accent alternatives shown above the keys after a long press, or null. */
    val accents: List<String>? = null,
)

interface KeyboardActions {
    fun type(text: String)

    fun backspace()

    fun enter()

    fun shift()

    fun switchLayout(layout: Layout)

    fun showAccents(
        key: Key.Char,
        options: List<String>,
    )

    fun dismissAccents()
}

private val KeyHeight = 54.dp

@Composable
fun KeyboardView(
    state: KeyboardState,
    actions: KeyboardActions,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val tick = { if (state.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 3.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AccentStrip(state.accents, actions, tick)
        Layouts.rows(state.layout, state.numberRow, state.decimal).forEach { row ->
            Row(Modifier.fillMaxWidth().height(KeyHeight), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { key -> KeyCell(key, state, actions, tick) }
            }
        }
    }
}

/** The strip above the keys: accent alternatives after a long press, otherwise empty space. */
@Composable
private fun AccentStrip(
    accents: List<String>?,
    actions: KeyboardActions,
    tick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        accents?.forEach { option ->
            Surface(
                onClick = {
                    tick()
                    actions.type(option)
                },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.height(40.dp),
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(option, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    key: Key,
    state: KeyboardState,
    actions: KeyboardActions,
    tick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val mod = Modifier.weight(key.weight).fillMaxSize()
    when (key) {
        is Key.Gap -> {
            Box(mod)
        }

        is Key.Char -> {
            val upper = state.shift != ShiftState.Off && state.layout == Layout.Letters
            val label = if (upper) key.text.uppercase() else key.text
            KeySurface(
                scheme.surfaceContainerHighest,
                scheme.onSurface,
                description =
                    if (key.text ==
                        " "
                    ) {
                        stringResource(R.string.key_space)
                    } else {
                        label
                    },
                modifier = mod,
                onTap = {
                    tick()
                    actions.type(key.text)
                },
                onLong = {
                    val direct = key.long
                    if (direct != null) {
                        tick()
                        actions.type(direct)
                    } else {
                        val options = Accents.of(key.text, upper)
                        if (options.isNotEmpty()) actions.showAccents(key, options)
                    }
                },
            ) {
                Text(label, fontSize = 22.sp)
                key.long?.let {
                    Text(
                        it,
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                    )
                }
            }
        }

        is Key.Space -> {
            KeySurface(scheme.surfaceContainerHighest, scheme.onSurface, stringResource(R.string.key_space), modifier = mod, onTap = {
                tick()
                actions.type(" ")
            }) {}
        }

        is Key.Shift -> {
            val on = state.shift != ShiftState.Off
            KeySurface(
                if (on) scheme.primaryContainer else scheme.secondaryContainer,
                if (on) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                stringResource(
                    if (state.shift ==
                        ShiftState.Locked
                    ) {
                        R.string.key_caps_lock
                    } else {
                        R.string.key_shift
                    },
                ),
                modifier = mod,
                onTap = {
                    tick()
                    actions.shift()
                },
            ) {
                Icon(
                    painterResource(
                        if (state.shift ==
                            ShiftState.Locked
                        ) {
                            DesignR.drawable.ic_keyboard_capslock
                        } else {
                            DesignR.drawable.ic_shift
                        },
                    ),
                    contentDescription = null,
                )
            }
        }

        is Key.Backspace -> {
            BackspaceKey(actions, tick, mod)
        }

        is Key.Action -> {
            val (icon, label) =
                when (state.enter) {
                    EnterAction.Newline -> DesignR.drawable.ic_keyboard_return to R.string.key_enter
                    EnterAction.Go -> DesignR.drawable.ic_arrow_forward to R.string.key_go
                    EnterAction.Search -> DesignR.drawable.ic_search to R.string.key_search
                    EnterAction.Send -> DesignR.drawable.ic_send to R.string.key_send
                    EnterAction.Next -> DesignR.drawable.ic_arrow_forward to R.string.key_next
                    EnterAction.Previous -> DesignR.drawable.ic_arrow_back to R.string.key_previous
                    EnterAction.Done -> DesignR.drawable.ic_check to R.string.key_done
                }
            KeySurface(scheme.primary, scheme.onPrimary, stringResource(label), modifier = mod, onTap = {
                tick()
                actions.enter()
            }) { Icon(painterResource(icon), contentDescription = null) }
        }

        is Key.Switch -> {
            KeySurface(scheme.secondaryContainer, scheme.onSecondaryContainer, key.label, modifier = mod, onTap = {
                tick()
                actions.switchLayout(key.layout)
            }) { Text(key.label, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun KeySurface(
    color: Color,
    contentColor: Color,
    description: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLong: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(10.dp),
        modifier =
            modifier
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }.pointerInput(onTap, onLong) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = onLong?.let { { _ -> it() } })
                },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/** Deletes once on press, then repeats while held, faster after half a second. */
@Composable
private fun BackspaceKey(
    actions: KeyboardActions,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier =
            modifier
                .semantics {
                    role = Role.Button
                }.pointerInput(Unit) {
                    coroutineScope {
                        val scope = this
                        awaitEachGesture {
                            awaitFirstDown()
                            tick()
                            actions.backspace()
                            val repeat =
                                scope.launch {
                                    delay(400)
                                    var interval = 100L
                                    var count = 0
                                    while (true) {
                                        actions.backspace()
                                        delay(interval)
                                        if (++count == 5) interval = 40L
                                    }
                                }
                            // Wait until every pointer is up, then stop repeating.
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            repeat.cancel()
                        }
                    }
                },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(painterResource(DesignR.drawable.ic_backspace), contentDescription = stringResource(R.string.key_backspace))
        }
    }
}
