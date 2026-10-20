package com.sitpro.notevoice.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.sitpro.notevoice.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** All dates and times are shown in Vietnam time, regardless of the phone's time zone setting. */
val VN_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(VN_ZONE)

fun vnInstant(date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(VN_ZONE).toInstant()

fun todayVn(): LocalDate = LocalDate.now(VN_ZONE)

fun vnDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(VN_ZONE).toLocalDate()

/** All user-visible text that depends on the app language (English resources, Vietnamese in values-vi). */
class Texts(private val res: Resources) {
    private val locale = res.configuration.locales[0]
    private val weekdayShort = res.getStringArray(R.array.weekday_short)
    private val weekdayLong = res.getStringArray(R.array.weekday_long)
    private fun pattern(@StringRes id: Int) = DateTimeFormatter.ofPattern(res.getString(id), locale)

    fun get(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)

    /** "T6, 23/10" / "Fri, Oct 23" (SPEC: Android app). */
    fun shortDate(day: LocalDate): String =
        "${weekdayShort[day.dayOfWeek.value - 1]}, ${pattern(R.string.pattern_day_month).format(day)}"

    fun dueLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> res.getString(R.string.date_today)
        today.plusDays(1) -> res.getString(R.string.date_tomorrow)
        else -> shortDate(day)
    }

    /** "Nhắc lúc 16:00 · Hôm nay" in the reminder notification (mockup "Notification shade"). */
    fun reminderLine(millis: Long, today: LocalDate): String =
        res.getString(R.string.reminder_line, time(millis), dueLabel(vnDate(millis), today))

    /** "Thứ Hai, 19 tháng 10" under the My Day title. */
    fun myDaySubtitle(day: LocalDate): String =
        "${weekdayLong[day.dayOfWeek.value - 1]}, ${pattern(R.string.pattern_day_month_long).format(day)}"

    fun monthTitle(month: YearMonth): String = pattern(R.string.pattern_month_year).format(month.atDay(1))

    fun time(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis))

    /** When a note or task was saved: "Hôm nay 14:30", "Hôm qua 21:05", else the short date. */
    fun whenSaved(millis: Long, today: LocalDate): String = when (vnDate(millis)) {
        today -> "${res.getString(R.string.date_today)} ${time(millis)}"
        today.minusDays(1) -> "${res.getString(R.string.date_yesterday)} ${time(millis)}"
        else -> shortDate(vnDate(millis))
    }

    fun updatedAt(millis: Long): String = res.getString(R.string.offline_updated, time(millis))

    /** Snackbar / notification title for a voice result. */
    fun resultTitle(tasks: Int, notes: Int): String {
        val t = res.getQuantityString(R.plurals.task_count, tasks, tasks)
        val n = res.getQuantityString(R.plurals.note_count, notes, notes)
        return when {
            tasks > 0 && notes > 0 -> res.getString(R.string.result_added_both, t, n)
            tasks > 0 -> res.getString(R.string.result_added, t)
            notes > 0 -> res.getString(R.string.result_saved_notes, n)
            else -> res.getString(R.string.result_nothing_new)
        }
    }

    fun heard(rawText: String): String = res.getString(R.string.heard, rawText)

    fun label(label: String): String = res.getString(
        when (label) {
            "idea" -> R.string.label_idea
            "quote" -> R.string.label_quote
            else -> R.string.label_note
        }
    )

    fun queued(n: Int): String = res.getQuantityString(R.plurals.queued, n, n)

    fun queueCount(n: Int): String = res.getQuantityString(R.plurals.queue_count, n, n)
}

/** Recomputed when the app language changes. */
@Composable
fun rememberTexts(): Texts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { Texts(context.resources) }
}
