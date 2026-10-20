package com.sitpro.notevoice.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Việc · Ghi chú" pill in the top bar (Loi, 2026-10-20): one tap between the last task list and Notes. */
class QuickSwitchTest {
    @Test
    fun taskListGoesToNotes() {
        assertEquals(Screen.NOTES, quickSwitch(Screen.MY_DAY, lastTaskScreen = Screen.MY_DAY))
        assertEquals(Screen.NOTES, quickSwitch(Screen.PLANNED, lastTaskScreen = Screen.PLANNED))
    }

    @Test
    fun notesGoBackToTheLastTaskList() {
        assertEquals(Screen.MY_DAY, quickSwitch(Screen.NOTES, lastTaskScreen = Screen.MY_DAY))
        assertEquals(Screen.IMPORTANT, quickSwitch(Screen.NOTES, lastTaskScreen = Screen.IMPORTANT))
    }

    @Test
    fun settingsGoesToNotes() {
        assertEquals(Screen.NOTES, quickSwitch(Screen.SETTINGS, lastTaskScreen = Screen.ALL))
    }
}
