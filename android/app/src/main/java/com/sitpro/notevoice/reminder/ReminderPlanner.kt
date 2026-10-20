package com.sitpro.notevoice.reminder

import com.sitpro.notevoice.data.CachedTask

/**
 * The one place that decides which reminder alarms exist (SPEC "Nhắc việc"). Pure, so every rule is unit-tested;
 * [ReminderScheduler] applies the result to AlarmManager.
 *
 * `scheduled` is what the app remembers having set (task id → reminder time). It survives a reboot, while
 * Android's alarms do not, which is how a reminder missed while the phone was off is found.
 */
object ReminderPlanner {
    /** Missed reminders older than this are dropped (Loi, 2026-10-20); the task still shows as overdue. */
    const val MISSED_WINDOW_MS = 24 * 60 * 60_000L
    /** An alarm going off also shows tasks due within this, since Doze can hold back the app's other alarms. */
    const val ALARM_EARLY_MS = 60_000L
    /** Alarms that went off less than this ago are left to the receiver, so a sync does not show them twice. */
    const val RECEIVER_GRACE_MS = 2 * 60_000L

    data class Plan(
        /** Alarms to set (again): all open tasks with a reminder still to come. */
        val alarms: Map<String, Long>,
        /** Alarms to remove: done, deleted or cleared tasks, and those shown now. */
        val cancel: Set<String>,
        /** Tasks to show a reminder for right away, earliest first. */
        val notify: List<String>,
        /** What to remember as scheduled after this sync. */
        val remembered: Map<String, Long>,
    )

    fun plan(tasks: List<CachedTask>, scheduled: Map<String, Long>, now: Long, alarmFired: Boolean): Plan {
        val wanted = tasks
            .filter { it.completedAtMillis == null && it.remindAtMillis != null }
            .associate { it.id to it.remindAtMillis!! }
        // A remembered alarm still counts only if the task is open and still has that exact time.
        val stillValid = scheduled.filter { (id, at) -> wanted[id] == at }

        val dueBy = if (alarmFired) now + ALARM_EARLY_MS else now - RECEIVER_GRACE_MS
        val notify = stillValid
            .filter { (_, at) -> at <= dueBy && at >= now - MISSED_WINDOW_MS }
            .entries.sortedBy { it.value }.map { it.key }
        val alarms = wanted.filter { (id, at) -> at > now && id !in notify }
        val justDue = stillValid.filter { (id, at) -> at in (dueBy + 1)..now && id !in notify }

        val remembered = alarms + justDue
        return Plan(alarms, scheduled.keys - remembered.keys, notify, remembered)
    }
}
