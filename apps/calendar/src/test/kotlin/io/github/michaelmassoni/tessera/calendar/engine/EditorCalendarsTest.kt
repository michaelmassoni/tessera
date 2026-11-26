package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorCalendarsTest {
    private fun cal(
        id: Long,
        visible: Boolean = true,
        writable: Boolean = true,
    ) = CalendarInfo(id, "c$id", "a", "t", 0, visible, writable)

    private fun ids(list: List<CalendarInfo>) = list.map { it.id }

    @Test
    fun offersOnlyShownWritableCalendars() {
        val all = listOf(cal(1), cal(2, visible = false), cal(3, writable = false), cal(4))
        assertEquals(listOf(1L, 4L), ids(editorCalendars(all, current = null)))
    }

    @Test
    fun keepsTheEventsOwnCalendarWhenHidden() {
        val all = listOf(cal(1), cal(2, visible = false), cal(3, visible = false))
        assertEquals(listOf(1L, 2L), ids(editorCalendars(all, current = 2)))
    }

    @Test
    fun fallsBackToEveryWritableCalendarWhenNoneShown() {
        val all = listOf(cal(1, visible = false), cal(2, visible = false), cal(3, writable = false))
        assertEquals(listOf(1L, 2L), ids(editorCalendars(all, current = null)))
    }

    @Test
    fun aReadOnlyCurrentCalendarIsNotOffered() {
        val all = listOf(cal(1), cal(2, writable = false))
        assertEquals(listOf(1L), ids(editorCalendars(all, current = 2)))
    }
}
