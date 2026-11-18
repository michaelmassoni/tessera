package io.github.michaelmassoni.tessera.calculator.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.michaelmassoni.tessera.calculator.R
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.calculator.engine.Input
import io.github.michaelmassoni.tessera.calculator.engine.Symbols
import io.github.michaelmassoni.tessera.core.design.R as DesignR

enum class KeyRole { Digit, Operator, Equals, Clear, Scientific }

/** What pressing a key does. Most keys send an [Input]; a few change calculator modes. */
sealed interface KeyAction {
    data class Send(
        val input: Input,
    ) : KeyAction

    data object Equals : KeyAction

    data object ToggleInverse : KeyAction

    data object ToggleAngleUnit : KeyAction
}

/**
 * One calculator key. The label is literal text, a string resource or an icon;
 * [description] is what TalkBack reads (defaults to the label).
 */
data class CalcKey(
    val action: KeyAction,
    val role: KeyRole,
    val text: String? = null,
    @param:StringRes val label: Int? = null,
    @param:DrawableRes val icon: Int? = null,
    @param:StringRes val description: Int? = null,
    /** For toggle keys (INV): whether the mode is on. */
    val checked: Boolean? = null,
)

/** Builds the key layouts for the current modes. */
class KeySet(
    private val decimalSeparator: Char,
    private val inverse: Boolean,
    private val angleUnit: AngleUnit,
) {
    private fun digit(d: Char) = CalcKey(KeyAction.Send(Input.Digit(d)), KeyRole.Digit, text = d.toString())

    private fun op(
        c: Char,
        label: Int,
        description: Int,
    ) = CalcKey(KeyAction.Send(Input.BinaryOperator(c)), KeyRole.Operator, label = label, description = description)

    private fun sci(
        input: Input,
        label: Int,
        description: Int,
    ) = CalcKey(KeyAction.Send(input), KeyRole.Scientific, label = label, description = description)

    val basicRows: List<List<CalcKey>> =
        listOf(
            listOf(
                CalcKey(
                    KeyAction.Send(Input.Clear),
                    KeyRole.Clear,
                    label = R.string.key_clear,
                    description = R.string.cd_clear,
                ),
                CalcKey(
                    KeyAction.Send(Input.Parenthesis),
                    KeyRole.Operator,
                    label = R.string.key_parentheses,
                    description = R.string.cd_parentheses,
                ),
                CalcKey(
                    KeyAction.Send(Input.Postfix(Symbols.PERCENT)),
                    KeyRole.Operator,
                    label = R.string.key_percent,
                    description = R.string.cd_percent,
                ),
                op(Symbols.DIVIDE, R.string.key_divide, R.string.cd_divide),
            ),
            listOf(digit('7'), digit('8'), digit('9'), op(Symbols.TIMES, R.string.key_multiply, R.string.cd_multiply)),
            listOf(digit('4'), digit('5'), digit('6'), op(Symbols.MINUS, R.string.key_minus, R.string.cd_minus)),
            listOf(digit('1'), digit('2'), digit('3'), op('+', R.string.key_plus, R.string.cd_plus)),
            listOf(
                digit('0'),
                CalcKey(
                    KeyAction.Send(Input.Decimal),
                    KeyRole.Digit,
                    text = decimalSeparator.toString(),
                    description = R.string.cd_decimal,
                ),
                CalcKey(
                    KeyAction.Send(Input.Backspace),
                    KeyRole.Digit,
                    icon = DesignR.drawable.ic_backspace,
                    description = R.string.cd_backspace,
                ),
                CalcKey(
                    KeyAction.Equals,
                    KeyRole.Equals,
                    label = R.string.key_equals,
                    description = R.string.cd_equals,
                ),
            ),
        )

    private val inverseKey =
        CalcKey(
            KeyAction.ToggleInverse,
            KeyRole.Scientific,
            label = R.string.key_inverse,
            description = R.string.cd_inverse,
            checked = inverse,
        )

    private val angleKey =
        CalcKey(
            KeyAction.ToggleAngleUnit,
            KeyRole.Scientific,
            label = if (angleUnit == AngleUnit.DEGREES) R.string.angle_degrees else R.string.angle_radians,
            description =
                if (angleUnit ==
                    AngleUnit.DEGREES
                ) {
                    R.string.cd_switch_to_radians
                } else {
                    R.string.cd_switch_to_degrees
                },
        )

    private val sin =
        if (inverse) {
            sci(Input.Function(Symbols.ASIN), R.string.key_asin, R.string.cd_asin)
        } else {
            sci(Input.Function(Symbols.SIN), R.string.key_sin, R.string.cd_sin)
        }
    private val cos =
        if (inverse) {
            sci(Input.Function(Symbols.ACOS), R.string.key_acos, R.string.cd_acos)
        } else {
            sci(Input.Function(Symbols.COS), R.string.key_cos, R.string.cd_cos)
        }
    private val tan =
        if (inverse) {
            sci(Input.Function(Symbols.ATAN), R.string.key_atan, R.string.cd_atan)
        } else {
            sci(Input.Function(Symbols.TAN), R.string.key_tan, R.string.cd_tan)
        }
    private val ln =
        if (inverse) {
            sci(
                Input.ExpPower,
                R.string.key_exp,
                R.string.cd_exp,
            )
        } else {
            sci(Input.Function(Symbols.LN), R.string.key_ln, R.string.cd_ln)
        }
    private val log =
        if (inverse) {
            sci(Input.TenPower, R.string.key_ten_power, R.string.cd_ten_power)
        } else {
            sci(Input.Function(Symbols.LOG), R.string.key_log, R.string.cd_log)
        }
    private val sqrt =
        if (inverse) {
            sci(
                Input.Square,
                R.string.key_square,
                R.string.cd_square,
            )
        } else {
            sci(Input.SquareRoot, R.string.key_sqrt, R.string.cd_sqrt)
        }
    private val pi = sci(Input.Constant(Symbols.PI), R.string.key_pi, R.string.cd_pi)
    private val e = sci(Input.Constant(Symbols.E), R.string.key_e, R.string.cd_e)
    private val power = sci(Input.BinaryOperator(Symbols.POWER), R.string.key_power, R.string.cd_power)
    private val factorial = sci(Input.Postfix(Symbols.FACTORIAL), R.string.key_factorial, R.string.cd_factorial)
    private val open = sci(Input.OpenParen, R.string.key_open_paren, R.string.cd_open_paren)
    private val close = sci(Input.CloseParen, R.string.key_close_paren, R.string.cd_close_paren)
    private val reciprocal = sci(Input.Reciprocal, R.string.key_reciprocal, R.string.cd_reciprocal)

    /** Portrait panel: the first row is always visible, the rest when expanded. Five columns. */
    val scientificPortraitRows: List<List<CalcKey>> =
        listOf(
            listOf(sqrt, pi, power, factorial),
            listOf(inverseKey, angleKey, sin, cos, tan),
            listOf(ln, log, e, open, close),
        )

    /** Side-by-side panel for landscape and large screens: five rows of three, matching the keypad. */
    val scientificGridRows: List<List<CalcKey>> =
        listOf(
            listOf(inverseKey, angleKey, power),
            listOf(sin, cos, tan),
            listOf(ln, log, sqrt),
            listOf(pi, e, factorial),
            listOf(open, close, reciprocal),
        )
}
