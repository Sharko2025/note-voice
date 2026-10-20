package com.sitpro.notevoice.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private fun texts() = Texts(ApplicationProvider.getApplicationContext<Context>().resources)

private val TODAY = LocalDate.of(2026, 10, 19) // Monday
private fun at(day: LocalDate, hh: Int, mm: Int) = vnInstant(day, LocalTime.of(hh, mm)).toEpochMilli()

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "vi")
class TextsVietnameseTest {
    private val t = texts()

    @Test fun shortDate() = assertEquals("T6, 23/10", t.shortDate(LocalDate.of(2026, 10, 23)))

    @Test fun sundayIsCN() = assertEquals("CN, 25/10", t.shortDate(LocalDate.of(2026, 10, 25)))

    /** Reminder notification body, mockup "Notification shade". */
    @Test fun reminderLine() {
        assertEquals("Nhắc lúc 16:00 · Hôm nay", t.reminderLine(at(TODAY, 16, 0), TODAY))
        assertEquals("Nhắc lúc 09:00 · Ngày mai", t.reminderLine(at(TODAY.plusDays(1), 9, 0), TODAY))
        assertEquals("Nhắc lúc 08:30 · T7, 17/10", t.reminderLine(at(TODAY.minusDays(2), 8, 30), TODAY))
    }

    @Test fun dueLabels() {
        assertEquals("Hôm nay", t.dueLabel(TODAY, TODAY))
        assertEquals("Ngày mai", t.dueLabel(TODAY.plusDays(1), TODAY))
        assertEquals("T7, 17/10", t.dueLabel(TODAY.minusDays(2), TODAY))
    }

    @Test fun myDaySubtitle() = assertEquals("Thứ Hai, 19 tháng 10", t.myDaySubtitle(TODAY))

    @Test fun monthTitle() = assertEquals("Tháng 10, 2026", t.monthTitle(YearMonth.of(2026, 10)))

    @Test fun timeIsVietnamTime24h() = assertEquals("09:05", t.time(at(TODAY, 9, 5)))

    @Test fun whenSaved() {
        assertEquals("Hôm nay 14:30", t.whenSaved(at(TODAY, 14, 30), TODAY))
        assertEquals("Hôm qua 21:05", t.whenSaved(at(TODAY.minusDays(1), 21, 5), TODAY))
        assertEquals("T7, 17/10", t.whenSaved(at(TODAY.minusDays(2), 8, 0), TODAY))
    }

    @Test fun resultTitles() {
        assertEquals("Đã thêm 2 việc", t.resultTitle(2, 0))
        assertEquals("Đã thêm 1 việc, 1 ghi chú", t.resultTitle(1, 1))
        assertEquals("Đã lưu 1 ghi chú", t.resultTitle(0, 1))
        assertEquals("Không có gì mới", t.resultTitle(0, 0))
    }

    @Test fun heard() = assertEquals("“mua sữa”", t.heard("mua sữa"))

    @Test fun labels() =
        assertEquals(listOf("Ý tưởng", "Câu hay", "Ghi chú", "Ghi chú"), listOf("idea", "quote", "note", "?").map(t::label))
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class TextsEnglishTest {
    private val t = texts()

    @Test fun shortDate() = assertEquals("Fri, Oct 23", t.shortDate(LocalDate.of(2026, 10, 23)))

    @Test fun reminderLine() {
        assertEquals("Reminder at 16:00 · Today", t.reminderLine(at(TODAY, 16, 0), TODAY))
        assertEquals("Reminder at 09:00 · Tomorrow", t.reminderLine(at(TODAY.plusDays(1), 9, 0), TODAY))
    }

    @Test fun dueLabels() {
        assertEquals("Today", t.dueLabel(TODAY, TODAY))
        assertEquals("Tomorrow", t.dueLabel(TODAY.plusDays(1), TODAY))
        assertEquals("Sat, Oct 17", t.dueLabel(TODAY.minusDays(2), TODAY))
    }

    @Test fun myDaySubtitle() = assertEquals("Monday, October 19", t.myDaySubtitle(TODAY))

    @Test fun monthTitle() = assertEquals("October 2026", t.monthTitle(YearMonth.of(2026, 10)))

    @Test fun whenSaved() {
        assertEquals("Today 14:30", t.whenSaved(at(TODAY, 14, 30), TODAY))
        assertEquals("Yesterday 21:05", t.whenSaved(at(TODAY.minusDays(1), 21, 5), TODAY))
    }

    @Test fun resultTitles() {
        assertEquals("Added 2 tasks", t.resultTitle(2, 0))
        assertEquals("Added 1 task, 1 note", t.resultTitle(1, 1))
        assertEquals("Saved 2 notes", t.resultTitle(0, 2))
    }

    @Test fun labels() = assertEquals(listOf("Idea", "Quote", "Note"), listOf("idea", "quote", "note").map(t::label))
}
