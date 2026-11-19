package io.github.michaelmassoni.tessera.keyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.keyboard.SetupScreen
import io.github.michaelmassoni.tessera.keyboard.engine.EnterAction
import io.github.michaelmassoni.tessera.keyboard.engine.Key
import io.github.michaelmassoni.tessera.keyboard.engine.Layout
import io.github.michaelmassoni.tessera.keyboard.engine.ShiftState
import io.github.michaelmassoni.tessera.keyboard.engine.Slot

private object NoActions : KeyboardActions {
    override fun type(text: String) = Unit

    override fun backspace() = Unit

    override fun enter() = Unit

    override fun shift() = Unit

    override fun switchLayout(layout: Layout) = Unit

    override fun showAccents(
        key: Key.Char,
        options: List<String>,
    ) = Unit

    override fun dismissAccents() = Unit

    override fun pick(slot: Slot) = Unit
}

@Preview(widthDp = 411)
@Composable
private fun LettersPreview() {
    TesseraTheme(dynamicColor = false) {
        KeyboardView(
            KeyboardState(
                enter = EnterAction.Send,
                suggestions = listOf(Slot("teh", Slot.Kind.Literal), Slot("the", Slot.Kind.Correction), Slot("ten", Slot.Kind.Word)),
            ),
            NoActions,
        )
    }
}

@Preview(widthDp = 411)
@Composable
private fun AccentsPreview() {
    TesseraTheme(dynamicColor = false, darkTheme = true) {
        KeyboardView(KeyboardState(numberRow = true, accents = listOf("é", "è", "ê", "ë", "ē")), NoActions)
    }
}

@Preview(widthDp = 411)
@Composable
private fun SymbolsPreview() {
    TesseraTheme(dynamicColor = false) { KeyboardView(KeyboardState(layout = Layout.Symbols, enter = EnterAction.Search), NoActions) }
}

@Preview(widthDp = 411)
@Composable
private fun PhonePreview() {
    TesseraTheme(dynamicColor = false) { KeyboardView(KeyboardState(layout = Layout.Phone), NoActions) }
}

@TesseraPreviews
@Composable
private fun SetupPreview() {
    TesseraTheme(dynamicColor = false) { SetupScreen(enabled = true, selected = false, onEnable = {}, onSelect = {}, onSettings = {}) }
}
