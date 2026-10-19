package com.sitpro.notevoice.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Quick reminder choices (mockup "Reminder: quick picks", same as Microsoft To Do). All in Vietnam time. */
private val MORNING: LocalTime = LocalTime.of(9, 0)

/** Hours people say most (mockup "Reminder: date & time"). */
val REMINDER_HOURS: List<LocalTime> = listOf(8, 9, 12, 15, 20).map { LocalTime.of(it, 0) }

fun nowVn(): ZonedDateTime = ZonedDateTime.now(VN_ZONE)

/** Three hours from now, rounded up to the hour; null once that would fall on tomorrow. */
fun laterToday(now: ZonedDateTime): Instant? {
    val plus = now.plusHours(3)
    val rounded = if (plus.minute == 0 && plus.second == 0 && plus.nano == 0) plus else plus.truncatedTo(ChronoUnit.HOURS).plusHours(1)
    return rounded.takeIf { it.toLocalDate() == now.toLocalDate() }?.toInstant()
}

fun tomorrowMorning(now: ZonedDateTime): Instant = now.toLocalDate().plusDays(1).atTime(MORNING).atZone(VN_ZONE).toInstant()

fun nextWeekMorning(now: ZonedDateTime): Instant =
    now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(MORNING).atZone(VN_ZONE).toInstant()

/** SPEC: the picker never allows a reminder in the past. */
fun isAllowedReminder(date: LocalDate, time: LocalTime, now: ZonedDateTime): Boolean = vnInstant(date, time) > now.toInstant()

fun isSelectableDay(date: LocalDate, now: ZonedDateTime): Boolean = date >= now.toLocalDate()

/** Hour chips for the chosen day, each with whether it can still be picked. */
fun reminderChips(date: LocalDate, now: ZonedDateTime): List<Pair<LocalTime, Boolean>> =
    REMINDER_HOURS.map { it to isAllowedReminder(date, it, now) }
