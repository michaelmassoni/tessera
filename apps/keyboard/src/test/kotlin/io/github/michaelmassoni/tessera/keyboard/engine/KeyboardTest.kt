package io.github.michaelmassoni.tessera.keyboard.engine

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTest {
    @Test fun `letters have a qwerty layout with digits on long press unless the number row is on`() {
        val rows = Layouts.rows(Layout.Letters, numberRow = false)
        assertEquals(4, rows.size)
        val top = rows[0].filterIsInstance<Key.Char>()
        assertEquals("qwertyuiop", top.joinToString("") { it.text })
        assertEquals("1", top[0].long)
        val withRow = Layouts.rows(Layout.Letters, numberRow = true)
        assertEquals(5, withRow.size)
        assertEquals(null, withRow[1].filterIsInstance<Key.Char>()[0].long)
    }

    @Test fun `every row of each layout has the same total width`() {
        for (layout in Layout.entries) {
            val widths = Layouts.rows(layout, numberRow = true, decimal = true).map { row -> row.sumOf { it.weight.toDouble() } }
            assertEquals("$layout: $widths", 1, widths.toSet().size)
        }
    }

    @Test fun `shift taps, double-tap locks, typing ends a one-shot`() {
        val shift = Shift(doubleTapMillis = 400)
        shift.tap(0)
        assertEquals(ShiftState.Once, shift.state)
        shift.typed()
        assertEquals(ShiftState.Off, shift.state)
        shift.tap(1000)
        shift.tap(1200)
        assertEquals(ShiftState.Locked, shift.state)
        shift.typed()
        shift.auto(false)
        assertEquals(ShiftState.Locked, shift.state)
        shift.tap(5000)
        assertEquals(ShiftState.Off, shift.state)
        shift.auto(true)
        assertEquals(ShiftState.Once, shift.state)
        shift.tap(9000)
        shift.tap(9900)
        assertEquals(ShiftState.Once, shift.state)
    }

    @Test fun `accents follow case`() {
        assertEquals("é", Accents.of("e", false)[0])
        assertEquals("É", Accents.of("E", true)[0])
        assertEquals(emptyList<String>(), Accents.of("q", false))
    }

    @Test fun `action key follows the editor`() {
        assertEquals(EnterAction.Search, ActionKey.of(EditorInfo.IME_ACTION_SEARCH, InputType.TYPE_CLASS_TEXT))
        assertEquals(
            EnterAction.Newline,
            ActionKey.of(
                EditorInfo.IME_ACTION_UNSPECIFIED,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            ),
        )
        assertEquals(
            EnterAction.Newline,
            ActionKey.of(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, InputType.TYPE_CLASS_TEXT),
        )
        assertEquals(EnterAction.Done, ActionKey.of(EditorInfo.IME_ACTION_UNSPECIFIED, InputType.TYPE_CLASS_TEXT))
        assertEquals(EditorInfo.IME_MASK_ACTION, ActionKey.IME_MASK_ACTION)
        assertEquals(EditorInfo.IME_FLAG_NO_ENTER_ACTION, ActionKey.IME_FLAG_NO_ENTER_ACTION)
        assertEquals(InputType.TYPE_TEXT_FLAG_MULTI_LINE, ActionKey.TYPE_TEXT_FLAG_MULTI_LINE)
    }
}
