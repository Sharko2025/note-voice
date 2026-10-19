package com.sitpro.notevoice.ui

import com.sitpro.notevoice.data.CachedTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The four task lists (SPEC "Định nghĩa các màn hình"), filtered on the phone from one cached list. */
enum class TaskView { MY_DAY, IMPORTANT, PLANNED, ALL }

enum class PlannedGroup { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

data class ViewContent(val open: List<CachedTask>, val completed: List<CachedTask>)

/** What a typed task gets on each screen (Loi 2026-10-19: like Microsoft To Do). */
data class NewTaskDefaults(val myDay: Boolean = false, val starred: Boolean = false, val dueDate: String? = null)

val CachedTask.isOpen: Boolean get() = completedAtMillis == null

private fun CachedTask.due(): LocalDate? = dueDate?.let(LocalDate::parse)

private fun CachedTask.completedDay(): LocalDate? =
    completedAtMillis?.let { Instant.ofEpochMilli(it).atZone(VN_ZONE).toLocalDate() }

fun isOverdue(task: CachedTask, today: LocalDate): Boolean = task.isOpen && task.due()?.let { it < today } == true

private fun CachedTask.inMyDay(today: LocalDate): Boolean =
    (myDay || due()?.let { it <= today } == true) && (isOpen || completedDay() == today)

fun TaskView.matches(task: CachedTask, today: LocalDate): Boolean = when (this) {
    TaskView.MY_DAY -> task.inMyDay(today)
    TaskView.IMPORTANT -> task.starred
    TaskView.PLANNED -> task.dueDate != null
    TaskView.ALL -> true
}

/** Open tasks first (server order, or by due date and reminder on Planned), completed ones newest first. */
fun TaskView.content(tasks: List<CachedTask>, today: LocalDate): ViewContent {
    val rows = tasks.filter { matches(it, today) }
    val open = rows.filter { it.isOpen }.let { list ->
        if (this == TaskView.PLANNED) {
            list.sortedWith(compareBy<CachedTask>({ it.dueDate }, { it.remindAtMillis ?: Long.MAX_VALUE }, { it.position }))
        } else {
            list.sortedBy { it.position }
        }
    }
    val completed = rows.filterNot { it.isOpen }.sortedByDescending { it.completedAtMillis }
    return ViewContent(open, completed)
}

fun plannedGroup(due: LocalDate, today: LocalDate): PlannedGroup {
    val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    return when {
        due < today -> PlannedGroup.OVERDUE
        due == today -> PlannedGroup.TODAY
        due == today.plusDays(1) -> PlannedGroup.TOMORROW
        due <= endOfWeek -> PlannedGroup.THIS_WEEK
        else -> PlannedGroup.LATER
    }
}

/** Non-empty groups in display order; `open` must already be sorted (see [content]). */
fun plannedGroups(open: List<CachedTask>, today: LocalDate): List<Pair<PlannedGroup, List<CachedTask>>> =
    open.groupBy { plannedGroup(LocalDate.parse(it.dueDate!!), today) }
        .toSortedMap()
        .map { (group, rows) -> group to rows }

fun openCounts(tasks: List<CachedTask>, today: LocalDate): Map<TaskView, Int> =
    TaskView.entries.associateWith { view -> tasks.count { it.isOpen && view.matches(it, today) } }

fun TaskView.newTaskDefaults(today: LocalDate): NewTaskDefaults = when (this) {
    TaskView.MY_DAY -> NewTaskDefaults(myDay = true)
    TaskView.IMPORTANT -> NewTaskDefaults(starred = true)
    TaskView.PLANNED -> NewTaskDefaults(dueDate = today.toString())
    TaskView.ALL -> NewTaskDefaults()
}
