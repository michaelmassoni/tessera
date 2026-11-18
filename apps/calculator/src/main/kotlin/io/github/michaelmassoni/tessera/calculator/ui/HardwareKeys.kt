package io.github.michaelmassoni.tessera.calculator.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.calculator.engine.Symbols

/** Physical keyboard support: digits, operators, Enter/=, Backspace, Escape. */
internal object HardwareKeys {
    fun handle(
        event: KeyEvent,
        decimalSeparator: Char,
        actions: CalculatorActions,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        when (event.key) {
            Key.Enter, Key.NumPadEnter, Key.NumPadEquals -> {
                actions.onEquals()
                return true
            }

            Key.Backspace -> {
                actions.onInput(Input.Backspace)
                return true
            }

            Key.Escape, Key.Delete, Key.Clear -> {
                actions.onInput(Input.Clear)
                return true
            }
        }
        val input = inputFor(event.utf16CodePoint.toChar(), decimalSeparator)
        if (input == null) {
            if (event.utf16CodePoint.toChar() == '=') {
                actions.onEquals()
                return true
            }
            return false
        }
        actions.onInput(input)
        return true
    }

    fun inputFor(
        c: Char,
        decimalSeparator: Char,
    ): Input? =
        when (c) {
            in '0'..'9' -> Input.Digit(c)
            '.', decimalSeparator -> Input.Decimal
            '+' -> Input.BinaryOperator('+')
            '-', Symbols.MINUS -> Input.BinaryOperator(Symbols.MINUS)
            '*', Symbols.TIMES -> Input.BinaryOperator(Symbols.TIMES)
            '/', Symbols.DIVIDE -> Input.BinaryOperator(Symbols.DIVIDE)
            '^' -> Input.BinaryOperator(Symbols.POWER)
            '%' -> Input.Postfix(Symbols.PERCENT)
            '!' -> Input.Postfix(Symbols.FACTORIAL)
            '(' -> Input.OpenParen
            ')' -> Input.CloseParen
            else -> null
        }
}
