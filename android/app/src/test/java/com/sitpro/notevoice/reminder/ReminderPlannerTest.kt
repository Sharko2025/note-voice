package com.sitpro.notevoice.reminder

import com.sitpro.notevoice.data.CachedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC "Nhắc việc": one exact alarm per open task with a future reminder; never one for a done or deleted task. */
class ReminderPlannerTest {
    private val now = 1_800_000_000_000L
    private val min = 60_000L
    private val hour = 60 * min

    private fun task(id: String, remindAt: Long?, done: Boolean = false) = CachedTask(
        id = id, voiceEntryId = null, title = "T $id", note = null, starred = false, dueDate = null,
        remindAtMillis = remindAt, myDay = false, completedAtMillis = if (done) now - hour else null,
        createdAtMillis = null, position = 0,
    )

    private fun plan(tasks: List<CachedTask>, scheduled: Map<String, Long> = emptyMap(), alarmFired: Boolean = false) =
        ReminderPlanner.plan(tasks, scheduled, now, alarmFired)

    @Test
    fun futureReminderOfAnOpenTaskIsScheduled() {
        val p = plan(listOf(task("a", now + hour)))
        assertEquals(mapOf("a" to now + hour), p.alarms)
        assertTrue(p.notify.isEmpty())
    }

    @Test
    fun noAlarmWithoutReminderOrWhenDoneOrPast() {
        val p = plan(listOf(task("none", null), task("done", now + hour, done = true), task("past", now - hour)))
        assertTrue(p.alarms.isEmpty())
    }

    /** All wanted alarms are set again on every sync: after a reboot Android has forgotten them. */
    @Test
    fun alreadyScheduledAlarmsAreSetAgain() {
        val p = plan(listOf(task("a", now + hour)), scheduled = mapOf("a" to now + hour))
        assertEquals(mapOf("a" to now + hour), p.alarms)
        assertTrue(p.cancel.isEmpty())
    }

    @Test
    fun completedDeletedOrClearedTasksLoseTheirAlarm() {
        val scheduled = mapOf("done" to now + hour, "deleted" to now + hour, "cleared" to now + hour)
        val p = plan(listOf(task("done", now + hour, done = true), task("cleared", null)), scheduled)
        assertEquals(setOf("done", "deleted", "cleared"), p.cancel)
        assertTrue(p.alarms.isEmpty())
        assertTrue(p.remembered.isEmpty())
    }

    @Test
    fun changedTimeReplacesTheAlarm() {
        val p = plan(listOf(task("a", now + 2 * hour)), scheduled = mapOf("a" to now + hour))
        assertEquals(mapOf("a" to now + 2 * hour), p.alarms)
        assertTrue(p.cancel.isEmpty()) // same PendingIntent: setting it again replaces the old time
    }

    /** Phone off at reminder time (Loi, 2026-10-20): notify once when it is back, within 24 hours. */
    @Test
    fun missedReminderWithin24HoursIsNotifiedOnce() {
        val tasks = listOf(task("a", now - 30 * min))
        val first = plan(tasks, scheduled = mapOf("a" to now - 30 * min))
        assertEquals(listOf("a"), first.notify)
        assertTrue("a" in first.cancel)
        assertTrue(first.remembered.isEmpty())
        // Next sync: no longer remembered as scheduled, so no second notification.
        assertTrue(plan(tasks, scheduled = first.remembered).notify.isEmpty())
    }

    @Test
    fun missedReminderOlderThan24HoursIsDropped() {
        val p = plan(listOf(task("a", now - 25 * hour)), scheduled = mapOf("a" to now - 25 * hour))
        assertTrue(p.notify.isEmpty())
        assertEquals(setOf("a"), p.cancel)
    }

    /** A fresh install or cleared data has nothing remembered: old reminders do not all ring at once. */
    @Test
    fun pastReminderThatWasNeverScheduledIsNotNotified() {
        assertTrue(plan(listOf(task("a", now - 30 * min))).notify.isEmpty())
    }

    @Test
    fun missedReminderOfADoneTaskOrWithAnotherTimeIsNotNotified() {
        val p = plan(
            listOf(task("done", now - 30 * min, done = true), task("moved", now + hour)),
            scheduled = mapOf("done" to now - 30 * min, "moved" to now - 30 * min),
        )
        assertTrue(p.notify.isEmpty())
        assertEquals(mapOf("moved" to now + hour), p.alarms)
    }

    /** An alarm that just went off is the receiver's to show: a sync at the same moment must not show it too. */
    @Test
    fun justDueAlarmIsLeftToTheReceiver() {
        val p = plan(listOf(task("a", now - 30_000)), scheduled = mapOf("a" to now - 30_000))
        assertTrue(p.notify.isEmpty())
        assertTrue(p.cancel.isEmpty())
        assertEquals(mapOf("a" to now - 30_000), p.remembered)
    }

    /** When an alarm goes off, every task due within the next minute is shown with it (Doze may delay the others). */
    @Test
    fun alarmShowsEveryTaskDueNow() {
        val tasks = listOf(task("a", now), task("b", now + 30_000), task("c", now + hour))
        val scheduled = mapOf("a" to now, "b" to now + 30_000, "c" to now + hour)
        val p = plan(tasks, scheduled, alarmFired = true)
        assertEquals(listOf("a", "b"), p.notify)
        assertEquals(mapOf("c" to now + hour), p.alarms)
        assertEquals(mapOf("c" to now + hour), p.remembered)
        assertTrue(p.cancel.containsAll(listOf("a", "b")))
    }

    @Test
    fun notifiedInReminderOrder() {
        val tasks = listOf(task("late", now - 10 * min), task("early", now - 50 * min))
        val p = plan(tasks, scheduled = mapOf("late" to now - 10 * min, "early" to now - 50 * min))
        assertEquals(listOf("early", "late"), p.notify)
    }
}
