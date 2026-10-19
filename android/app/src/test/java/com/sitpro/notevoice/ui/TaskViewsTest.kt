package com.sitpro.notevoice.ui

import com.sitpro.notevoice.data.CachedTask
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same sample data as the backend's test_tasks.py (and the mockup), today = Monday 19/10/2026. */
class TaskViewsTest {
    private val today = LocalDate.of(2026, 10, 19)
    private var nextPosition = 0L

    private fun at(day: LocalDate, hh: Int, mm: Int = 0) = vnInstant(day, LocalTime.of(hh, mm)).toEpochMilli()

    private fun task(
        title: String,
        due: LocalDate? = null,
        remind: Long? = null,
        myDay: Boolean = false,
        starred: Boolean = false,
        completed: Long? = null,
    ) = CachedTask(
        id = title, voiceEntryId = null, title = title, note = null, starred = starred, dueDate = due?.toString(),
        remindAtMillis = remind, myDay = myDay, completedAtMillis = completed, createdAtMillis = null,
        position = nextPosition++,
    )

    private val mockup = listOf(
        task("Đóng tiền điện", due = today.minusDays(2)),
        task("Gọi cho chủ nhà", due = today, remind = at(today, 23), myDay = true),
        task("Mua sữa cho con", starred = true, myDay = true),
        task("Nộp báo cáo quý", due = today.plusDays(4), starred = true),
        task("Gửi email cho chị Lan", due = today.plusDays(1), remind = at(today.plusDays(1), 15)),
        task("Gia hạn hộ chiếu", due = today.plusDays(22)),
        task("Đặt lịch khám răng"),
        task("Uống thuốc", myDay = true, completed = at(today, 0, 1)),
        task("Chuyển tiền nhà", completed = at(today.minusDays(1), 23, 59)), // My Day of yesterday
        task("Việc hôm qua"), // was in yesterday's My Day: the server sends my_day = false today
    )

    private fun titles(view: TaskView) = view.content(mockup, today).let { c ->
        c.open.map { it.title } to c.completed.map { it.title }
    }

    @Test
    fun myDay() {
        assertEquals(
            listOf("Đóng tiền điện", "Gọi cho chủ nhà", "Mua sữa cho con") to listOf("Uống thuốc"),
            titles(TaskView.MY_DAY),
        )
    }

    @Test
    fun important() {
        assertEquals(listOf("Mua sữa cho con", "Nộp báo cáo quý") to emptyList<String>(), titles(TaskView.IMPORTANT))
    }

    @Test
    fun plannedIsSortedByDueDateThenReminder() {
        assertEquals(
            listOf("Đóng tiền điện", "Gọi cho chủ nhà", "Gửi email cho chị Lan", "Nộp báo cáo quý", "Gia hạn hộ chiếu"),
            titles(TaskView.PLANNED).first,
        )
    }

    @Test
    fun plannedReminderBreaksTiesAndNoReminderGoesLast() {
        val day = today.plusDays(3)
        val tasks = listOf(task("Không giờ", due = day), task("Chiều", due = day, remind = at(day, 15)), task("Sáng", due = day, remind = at(day, 9)))
        assertEquals(listOf("Sáng", "Chiều", "Không giờ"), TaskView.PLANNED.content(tasks, today).open.map { it.title })
    }

    @Test
    fun plannedGroups() {
        val groups = plannedGroups(TaskView.PLANNED.content(mockup, today).open, today)
        assertEquals(
            listOf(
                PlannedGroup.OVERDUE to listOf("Đóng tiền điện"),
                PlannedGroup.TODAY to listOf("Gọi cho chủ nhà"),
                PlannedGroup.TOMORROW to listOf("Gửi email cho chị Lan"),
                PlannedGroup.THIS_WEEK to listOf("Nộp báo cáo quý"),
                PlannedGroup.LATER to listOf("Gia hạn hộ chiếu"),
            ),
            groups.map { (g, rows) -> g to rows.map { it.title } },
        )
    }

    @Test
    fun thisWeekEndsOnSunday() {
        assertEquals(PlannedGroup.THIS_WEEK, plannedGroup(LocalDate.of(2026, 10, 25), today)) // Sunday
        assertEquals(PlannedGroup.LATER, plannedGroup(LocalDate.of(2026, 10, 26), today)) // next Monday
        // On a Sunday, tomorrow is already next week: nothing is left for "this week".
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(PlannedGroup.TOMORROW, plannedGroup(sunday.plusDays(1), sunday))
        assertEquals(PlannedGroup.LATER, plannedGroup(sunday.plusDays(2), sunday))
    }

    @Test
    fun allHasEverythingOpenFirstThenCompletedNewestFirst() {
        val (open, completed) = titles(TaskView.ALL)
        assertEquals(8, open.size)
        assertEquals(listOf("Uống thuốc", "Chuyển tiền nhà"), completed)
    }

    @Test
    fun openCountsForTheMenu() {
        assertEquals(
            mapOf(TaskView.MY_DAY to 3, TaskView.IMPORTANT to 2, TaskView.PLANNED to 5, TaskView.ALL to 8),
            openCounts(mockup, today),
        )
    }

    @Test
    fun overdueMeansDueBeforeTodayAndOpen() {
        assertEquals(true, isOverdue(mockup[0], today))
        assertEquals(false, isOverdue(mockup[1], today))
        assertEquals(false, isOverdue(task("Xong rồi", due = today.minusDays(3), completed = at(today, 8)), today))
    }

    @Test
    fun typedTaskDefaultsFollowTheScreen() {
        assertEquals(NewTaskDefaults(myDay = true), TaskView.MY_DAY.newTaskDefaults(today))
        assertEquals(NewTaskDefaults(starred = true), TaskView.IMPORTANT.newTaskDefaults(today))
        assertEquals(NewTaskDefaults(dueDate = "2026-10-19"), TaskView.PLANNED.newTaskDefaults(today))
        assertEquals(NewTaskDefaults(), TaskView.ALL.newTaskDefaults(today))
    }
}
