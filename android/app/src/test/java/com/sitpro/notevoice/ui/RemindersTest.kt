package com.sitpro.notevoice.ui

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private fun vn(day: Int, hh: Int, mm: Int = 0): ZonedDateTime = ZonedDateTime.of(2026, 10, day, hh, mm, 0, 0, VN_ZONE)
    private val now = vn(19, 14, 30) // Monday

    @Test
    fun laterTodayIsThreeHoursRoundedUpToTheHour() {
        assertEquals(vn(19, 18).toInstant(), laterToday(now)) // mockup: 14:30 -> 18:00
        assertEquals(vn(19, 12).toInstant(), laterToday(vn(19, 9))) // exactly on the hour stays on it
    }

    @Test
    fun laterTodayIsHiddenWhenItWouldBeTomorrow() {
        assertEquals(vn(19, 23).toInstant(), laterToday(vn(19, 19, 30)))
        assertNull(laterToday(vn(19, 21, 1)))
    }

    @Test
    fun tomorrowAndNextWeekAreAtNine() {
        assertEquals(vn(20, 9).toInstant(), tomorrowMorning(now))
        assertEquals(vn(26, 9).toInstant(), nextWeekMorning(now)) // Monday -> next Monday
        assertEquals(vn(26, 9).toInstant(), nextWeekMorning(vn(25, 10))) // Sunday -> the next day
    }

    @Test
    fun onlyFutureTimesAreAllowed() {
        assertTrue(isAllowedReminder(LocalDate.of(2026, 10, 19), LocalTime.of(14, 31), now))
        assertFalse(isAllowedReminder(LocalDate.of(2026, 10, 19), LocalTime.of(14, 30), now))
        assertFalse(isAllowedReminder(LocalDate.of(2026, 10, 18), LocalTime.of(20, 0), now))
        assertTrue(isAllowedReminder(LocalDate.of(2026, 10, 20), LocalTime.of(8, 0), now))
    }

    @Test
    fun hourChipsInThePastAreDisabledToday() {
        val chips = reminderChips(LocalDate.of(2026, 10, 19), now)
        assertEquals(listOf("08:00", "09:00", "12:00", "15:00", "20:00"), chips.map { it.first.toString() })
        assertEquals(listOf(false, false, false, true, true), chips.map { it.second })
        assertTrue(reminderChips(LocalDate.of(2026, 10, 20), now).all { it.second })
    }

    @Test
    fun pastDaysCannotBePicked() {
        assertFalse(isSelectableDay(LocalDate.of(2026, 10, 18), now))
        assertTrue(isSelectableDay(LocalDate.of(2026, 10, 19), now))
    }
}
